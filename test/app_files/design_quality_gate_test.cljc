(ns app-files.design-quality-gate-test
  "The window is scored, not admired (ADR-2607132300: an unmeasured metric is
  theater).

  Renders the real `->page` output for the three states the app is actually
  ever in — a populated listing, a listing with a multi-selection, and the
  state before a capability grant arrives — and scores the emitted HTML plus
  inline CSS with the deterministic HIG/WCAG rubric.

  The empty state is included deliberately. It is the one screen most likely
  to ship unstyled, because it is the one nobody looks at while building."
  (:require [app-files.model :as model]
            [app-files.page :as page]
            [app-files.source :as source]
            [clojure.test :refer [deftest is testing]]
            [design-quality.audit :as dq]
            [mokuroku.catalog :as catalog]))

(def aggregate-floor
  "Set from the honestly measured aggregate minus a ~2pt margin. RAISE this
  when upstream improves; never lower it to make a regression pass."
  98.0)

(def page-floor 98.0)

(def entries
  [{:path "/w/README.md" :name "README.md" :size 2048 :modified 300 :owner "jun"}
   {:path "/w/src" :name "src" :directory? true :modified 500 :owner "jun"}
   {:path "/w/deps.edn" :name "deps.edn" :size 512 :modified 400 :owner "jun"}
   {:path "/w/target" :name "target" :directory? true :modified 900 :owner "root"}])

(defn- loaded []
  (-> (catalog/catalog (source/fixture-source "/w" entries) model/default-query)
      catalog/refresh))

(def pages
  {"listing" (page/render (loaded))
   "multi-selection" (page/render (catalog/select-all (loaded)))
   ;; Never refreshed: what the window looks like while the fs/browse grant
   ;; has not arrived.
   "awaiting-grant" (page/render
                     (catalog/catalog (source/fixture-source "/w" []) model/default-query))})

(deftest window-meets-the-design-quality-floor
  (let [{:keys [overall pages] :as report} (dq/audit pages {:extra-axes dq/extra-axes})]
    ;; Printed, not just asserted: a gate that only says pass/fail hides the
    ;; headroom, and the floor is supposed to be raised as upstream improves.
    (println "design-quality: aggregate" overall)
    (doseq [[nm r] (sort-by key pages)]
      (println " " nm (:overall r)
               (pr-str (mapv :id (remove #(>= (:score %) 1.0) (:axes r))))))
    (testing "aggregate"
      (is (>= overall aggregate-floor)
          (pr-str (select-keys report [:findings]))))
    (testing "every state, including the one nobody looks at"
      (doseq [[nm r] pages]
        (is (>= (:overall r) page-floor)
            (str nm " " (pr-str (keep :finding (:axes r)))))))))

(deftest the-page-spends-tokens-not-literals
  (let [html (page/render (loaded))]
    (testing "app CSS contains no raw hex colour"
      ;; The theme map is the only legitimate place for one (kotoba-ui rule 5),
      ;; and it is not part of app-css.
      (is (not (re-find #"#[0-9a-fA-F]{3,8}" page/app-css))))
    (testing "and no px font-size"
      (is (not (re-find #"font-size\s*:\s*\d+px" page/app-css))))
    (testing "it declares a viewport and an appearance-aware theme"
      (is (re-find #"viewport" html))
      (is (re-find #"theme-color" html)))))
