(ns app-files.model
  "Finder's domain: a directory listing, as `mokuroku` items.

  This namespace performs no I/O. It turns whatever a `fs/browse` provider
  hands back into items the catalog kernel can sort, filter and select, and it
  decides what a file listing's columns are. Reading the directory is the
  host's job, behind the capability grant."
  (:require [clojure.string :as str]
            [mokuroku.item :as item]
            [mokuroku.source :as source]))

(def capability "fs/browse")

(def columns
  [(source/attribute :name "Name" :string)
   (source/attribute :size "Size" :number)
   (source/attribute :modified "Date Modified" :number)
   (source/attribute :extension "Kind" :string)
   (source/attribute :owner "Owner" :string)
   ;; Not sortable: sorting by a full path in a single-directory listing tells
   ;; the user nothing that sorting by name does not, and it makes the column
   ;; look like a meaningful axis when it is an identity.
   (source/attribute :path "Path" :string false)])

(def commands
  "What a directory listing accepts. Deliberately no :rename and no :trash
  until a provider exists that can perform them — offering a command whose
  effect nothing implements produces a button that silently does nothing,
  which reads to the user as a broken app rather than an ungranted one."
  #{:open :reveal :copy-path :quicklook})

(defn descriptor
  ([] (descriptor "/"))
  ([dir]
   (source/descriptor
    {:id :app-files/directory
     :item-kind :file
     :label dir
     :capability capability
     :commands commands
     :attributes columns})))

(defn extension
  "The part after the last dot, lowercased; nil when there is none.

  Dotfiles have no extension: `.gitignore` is a file named `.gitignore`, not a
  nameless file of kind `gitignore`. Getting this wrong groups every dotfile
  under a different kind, which is exactly the noise the Kind column exists to
  remove."
  [nm]
  (let [base (or nm "")
        idx (str/last-index-of base ".")]
    (when (and idx (pos? idx) (< (inc idx) (count base)))
      (str/lower-case (subs base (inc idx))))))

(defn- kind-of [entry]
  (cond
    (:directory? entry) :directory
    (:symlink? entry) :symlink
    :else :file))

(defn entry->item
  "Normalise one provider row.

  ENTRY is the shape a `fs/browse` provider yields:
  `{:path :name :directory? :symlink? :size :modified :owner}`. The id is the
  path, because that is what stays stable while a listing is re-sorted and
  what a command proposal must name."
  [entry]
  (let [nm (or (:name entry)
               (last (remove str/blank? (str/split (str (:path entry)) #"/")))
               "")
        dir? (:directory? entry)]
    (item/item (:path entry)
               (kind-of entry)
               nm
               (cond-> {:name nm
                        :path (:path entry)
                        :owner (:owner entry)}
                 ;; A directory's size is the size of its own inode, which is
                 ;; not what anyone reading a Size column wants to compare.
                 ;; Absent is honest and sorts last.
                 (not dir?) (assoc :size (:size entry)
                                   :extension (extension nm))
                 true (assoc :modified (:modified entry))))))

(defn listing->items [entries]
  (mapv entry->item entries))

(def directories-first
  "Finder's default: directories above files, then by name.

  Expressed as a sort spec rather than a special case in the comparator —
  `:kind` orders :directory before :file and :symlink alphabetically, which is
  the order wanted, and it stays a plain total order the kernel already knows
  how to run."
  [[:kind :asc] [:name :asc]])

(def default-query
  {:query/sort directories-first
   :query/text ""
   :query/filters []})

;; Hiding dotfiles is applied to the items before they reach the catalog, not
;; expressed as a `:query/filters` entry. mokuroku's operator set has no
;; "starts-with", and inventing one here would mean either a filter that
;; silently matches nothing or a fork of the shared kernel to serve one app.
;; The source is never asked to lie about what the directory contains.
(defn dotfile? [it]
  (str/starts-with? (str (item/attr it :name)) "."))

(defn without-dotfiles [items]
  (vec (remove dotfile? items)))
