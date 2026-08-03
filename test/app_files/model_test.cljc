(ns app-files.model-test
  (:require [app-files.model :as model]
            [app-files.source :as source]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [mokuroku.catalog :as catalog]
            [mokuroku.item :as item]))

(def entries
  [{:path "/w/README.md" :name "README.md" :size 2048 :modified 300 :owner "jun"}
   {:path "/w/src" :name "src" :directory? true :modified 500 :owner "jun"}
   {:path "/w/.gitignore" :name ".gitignore" :size 24 :modified 100 :owner "jun"}
   {:path "/w/deps.edn" :name "deps.edn" :size 512 :modified 400 :owner "jun"}
   {:path "/w/target" :name "target" :directory? true :modified 900 :owner "root"}
   {:path "/w/link" :name "link" :symlink? true :size 8 :modified 50 :owner "jun"}])

(deftest extension-handling
  (testing "the part after the last dot"
    (is (= "md" (model/extension "README.md")))
    (is (= "edn" (model/extension "deps.edn")))
    (is (= "gz" (model/extension "archive.tar.gz"))))

  (testing "a dotfile is a name, not an extension"
    ;; Getting this wrong files every dotfile under its own Kind, which is
    ;; exactly the noise the Kind column exists to remove.
    (is (nil? (model/extension ".gitignore")))
    (is (nil? (model/extension ".env"))))

  (testing "no extension at all"
    (is (nil? (model/extension "Makefile")))
    (is (nil? (model/extension "trailing.")))
    (is (nil? (model/extension "")))))

(deftest entry-normalisation
  (let [by-id (into {} (map (juxt :item/id identity)) (model/listing->items entries))]
    (testing "the id is the path, because that is what survives a re-sort"
      (is (contains? by-id "/w/README.md")))

    (testing "kind comes from the entry flags"
      (is (= :file (:item/kind (by-id "/w/README.md"))))
      (is (= :directory (:item/kind (by-id "/w/src"))))
      (is (= :symlink (:item/kind (by-id "/w/link")))))

    (testing "a directory has no size"
      ;; Not 0 — that would claim the folder is empty. Absent sorts last,
      ;; which is where a sizeless row belongs.
      (is (nil? (item/attr (by-id "/w/src") :size)))
      (is (= 2048 (item/attr (by-id "/w/README.md") :size))))

    (testing "and no extension"
      (is (nil? (item/attr (by-id "/w/src") :extension)))
      (is (= "md" (item/attr (by-id "/w/README.md") :extension))))

    (testing "a name is derived from the path when the provider omits it"
      (is (= "solo.txt"
             (:item/label (model/entry->item {:path "/a/b/solo.txt" :size 1})))))

    (testing "every item satisfies the kernel's contract"
      (is (= [] (item/problems-in (model/listing->items entries)))))))

(deftest directories-sort-above-files
  (let [cat (-> (catalog/catalog (source/fixture-source "/w" entries)
                                 model/default-query)
                catalog/refresh)
        ids (mapv :item/id (:result/items (catalog/result cat)))]
    ;; Name ordering is case-insensitive, so README.md sits with the lowercase
    ;; names rather than ahead of them. That is what a file browser should do —
    ;; ASCII ordering would file every capitalised name in its own block.
    (is (= ["/w/src" "/w/target" "/w/.gitignore" "/w/deps.edn" "/w/README.md" "/w/link"]
           ids)
        "directories, then files by name, then the symlink — one total order")))

(deftest dotfiles-are-filtered-by-the-app-not-hidden-by-the-source
  (let [all (model/listing->items entries)
        shown (model/without-dotfiles all)]
    (is (= 6 (count all)) "the source reports everything it found")
    (is (= 5 (count shown)))
    (is (not-any? model/dotfile? shown))))

(deftest denied-is-not-empty
  (testing "a refused grant is distinguishable from an empty folder"
    (is (source/denied? source/denied))
    (is (not (source/denied? (source/granted []))))
    (is (= "fs/browse" (:browse/capability source/denied)))))

(deftest commands-are-bounded-by-what-a-listing-can-do
  (let [cat (-> (catalog/catalog (source/fixture-source "/w" entries))
                catalog/refresh
                (catalog/select "/w/README.md"))
        offered (set (map :command/id (:view/commands (catalog/view cat))))]
    (is (= #{:open :reveal :copy-path :quicklook} offered))
    (testing "trash is not offered, because no provider implements it yet"
      ;; A button whose effect nothing implements reads to the user as a
      ;; broken app rather than an ungranted one.
      (is (not (contains? offered :trash)))
      (is (= :source-does-not-accept (:proposal/refused (catalog/propose cat :trash)))))

    (testing "an accepted proposal names the capability that would authorise it"
      (let [p (catalog/propose cat :open)]
        (is (= "fs/browse" (:proposal/capability p)))
        (is (= ["/w/README.md"] (:proposal/targets p)))
        (is (false? (:proposal/destructive? p)))))))

(deftest search-and-selection-across-a-refresh
  (let [cat (-> (catalog/catalog (source/fixture-source "/w" entries))
                catalog/refresh
                (catalog/select "/w/deps.edn")
                (catalog/toggle "/w/target"))
        ;; the build directory was cleaned away between refreshes
        after (catalog/with-items cat (model/listing->items
                                       (remove #(= "/w/target" (:path %)) entries)))]
    (is (= #{"/w/deps.edn"} (:selection/ids (:catalog/selection after))))
    (is (= #{"/w/target"} (:selection/dropped (:catalog/selection after))))
    (testing "search reaches the name and the owner, not the byte count"
      (is (= ["/w/target"]
             (mapv :item/id (:result/items (catalog/result (catalog/search cat "root"))))))
      (is (empty? (:result/items (catalog/result (catalog/search cat "2048"))))))))

(deftest the-descriptor-declares-its-capability
  (let [d (model/descriptor "/w")]
    (is (= "fs/browse" (:source/capability d)))
    (is (= "/w" (:source/label d)))
    (testing "path is a column but not a sortable axis"
      (is (some #(and (= :path (:attribute/key %)) (not (:attribute/sortable? %)))
                (:source/attributes d))))))

(deftest a-provider-that-throws-is-the-hosts-problem-not-a-corrupt-listing
  ;; The seam is the only thing here that can fail for an outside reason.
  ;; Everything downstream of it is total, so a bad row degrades to a reported
  ;; problem rather than an exception in the middle of a render.
  (let [cat (catalog/refresh
             (catalog/catalog
              (source/fixture-source "/w" (conj entries {:path "/w/odd" :name 7}))))
        v (catalog/view cat)]
    (is (= 7 (count (:result/items (:view/result v)))))
    (is (str/includes? (pr-str (:problems/items (:view/problems v)))
                       "item/label-not-string"))))
