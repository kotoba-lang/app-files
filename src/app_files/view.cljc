(ns app-files.view
  "The window, as pure hiccup.

  Requires `kotoba-ui.core` and `appkit.core` only — the desktop-dense
  binding, because a file listing is dense tabular data on a large surface.
  No raw colours, no font sizes, no hand-written layout: type is the 11 HIG
  text styles, structure comes from shell, and the one place a hex is
  legitimate is the theme map in `app-files.page`.

  Views are pure: they take the `mokuroku.catalog/view` value and return
  hiccup. The same hiccup renders through `->page` on the server and mounts
  through the reagent seam in the browser."
  (:require [appkit.core :as appkit]
            [clojure.string :as str]
            [kotoba-ui.core :as ui]
            [mokuroku.item :as item]))

;; ------------------------------------------------------------- formatting

(def ^:private units ["B" "KB" "MB" "GB" "TB"])

(defn human-size
  "Bytes as a short string. nil for a directory — not 0, which would claim a
  directory is empty."
  [bytes]
  (when (number? bytes)
    (loop [n (double bytes) [u & more] units]
      (if (or (< n 1024) (nil? (seq more)))
        (str (if (or (= "B" u) (>= n 100))
               (long n)
               (/ (Math/round (* 10.0 n)) 10.0))
             " " u)
        (recur (/ n 1024) more)))))

(defn kind-label [it]
  (case (:item/kind it)
    :directory "Folder"
    :symlink "Alias"
    (if-let [ext (item/attr it :extension)]
      (str (str/upper-case ext) " file")
      "Document")))

;; ------------------------------------------------------------------- rows

(defn- column-cell [class-suffix body]
  [:span {:class (str "app-files__cell app-files__cell--" class-suffix)} body])

(defn file-row
  "One row.

  KNOWN GAP: selection is carried by a class only. `aria-selected` belongs on
  the `role=\"listitem\"` element, and `liquid-glass/list-row` destructures
  just `:act`/`:trailing`/`:class` — it has no `:attrs` passthrough, so an
  `:attrs {:aria-selected …}` here would be silently dropped and the code
  would read as accessible while rendering nothing of the sort. Every app in
  this suite that has a selectable list needs it, so the fix belongs in
  liquid-glass-ui, not in a fork here. Filed rather than faked."
  [view it]
  (let [selected? (contains? (:selection/ids (:view/selection view)) (:item/id it))]
    (ui/list-row
     [:span {:class "app-files__row"}
      (column-cell "name" [:span {:class "hig-body"} (:item/label it)])
      (column-cell "kind" [:span {:class "hig-footnote"} (kind-label it)])
      (column-cell "size" [:span {:class "hig-footnote"}
                           (or (human-size (item/attr it :size)) "--")])]
     {:act [:select (:item/id it)]
      :class (when selected? "app-files__row--selected")
      :trailing (when (= :directory (:item/kind it))
                  (ui/badge "Folder"))})))

(defn listing [view]
  (let [items (:result/items (:view/result view))]
    (if (seq items)
      (appkit/list-view (mapv #(file-row view %) items))
      (ui/empty-state
       {:title (if (:view/fetched? view) "Nothing here" "Not loaded")
        :body (if (:view/fetched? view)
                "This folder has no items that match the current filter."
                (str "Waiting on the "
                     (:source/capability (:view/descriptor view))
                     " grant."))}))))

;; -------------------------------------------------------------- inspector

(defn- inspector-field [{:keys [field/key field/label field/value]}]
  [:div {:class "app-files__field"}
   [:span {:class "hig-caption1"} label]
   [:span {:class "hig-callout"}
    (if (= :size key) (or (human-size value) (str value)) (str value))]])

(defn inspector [view]
  (let [i (:view/inspector view)]
    (appkit/panel
     (ui/stack {:gap :2}
       [:h2 {:class "hig-headline"} (:inspector/title i)]
       (when-let [s (:inspector/subtitle i)]
         [:p {:class "hig-footnote"} s])
       (ui/divider)
       (into [:div {:class "app-files__fields"}]
             (map inspector-field (:inspector/fields i)))))))

;; ---------------------------------------------------------------- chrome

(defn command-bar
  "Commands the source accepts for the current selection.

  A destructive command is marked with a class and a `:title`, so the
  confirmation the kernel will demand is visible before the click rather than
  after it. `button` takes `:class`/`:title`/`:act`/`:disabled`/`:type` and
  nothing else, so those are what is used — not an `:attrs` map it would drop."
  [view]
  (ui/toolbar
   (mapv (fn [c]
           (ui/button (:command/label c)
                      (cond-> {:act [:command (:command/id c)]}
                        (:command/destructive? c)
                        (assoc :class "app-files__command--destructive"
                               :title "Asks for confirmation"))))
         (:view/commands view))))

(defn status-line
  "What is on screen versus what exists.

  A truncated listing that only reported the number of visible rows would tell
  the user a folder holds 200 files when it holds 40,000."
  [view]
  (let [{:result/keys [items matched total truncated?]} (:view/result view)
        dropped (:problems/dropped-selection (:view/problems view))]
    [:p {:class "hig-footnote"}
     (str (count items)
          (when truncated? (str " of " matched))
          (if (= matched total)
            (str " of " total " items")
            (str " shown, " total " in folder"))
          (when (seq dropped)
            (str " · " (count dropped) " selected item(s) no longer exist")))]))

(defn window [view]
  (ui/app-shell
   {:nav (ui/nav-bar
          (:source/label (:view/descriptor view))
          {:trailing [(ui/search-field {:placeholder "Search this folder"
                                        :aria-label "Search this folder"
                                        :value (:query/text (:view/query view))
                                        :act :search})]})
    :sidebar (inspector view)}
   (ui/stack {:gap :3}
     (command-bar view)
     (listing view)
     (status-line view))))
