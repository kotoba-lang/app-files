(ns app-files.page
  "SSR entry. The window comes from `mokuroku-ui`; this only says what a file
  row is called and how its values read.

  This repo carried its own copy of the window until `mokuroku-ui` was
  extracted. Keeping it would have meant nine apps each maintaining their own
  row grid, inspector and status line — and each fixing the same accessibility
  gap separately, or not at all."
  (:require [app-files.model :as model]
            [clojure.string :as str]
            [mokuroku.catalog :as catalog]
            [mokuroku-ui.core :as mui]))

(defn kind-label
  "What the Kind column says for an extension. Absent means a file with no
  extension, or a directory — neither of which is an unknown kind."
  [ext]
  (if (str/blank? (str ext))
    "Document"
    (str (str/upper-case ext) " file")))

(def view-opts
  {:columns [:name :size :extension]
   :formatters {:size mui/human-bytes
                :extension kind-label}
   :noun "items"
   :search-placeholder "Search this folder"
   :empty-title "Nothing here"
   :empty-body "This folder has no items that match the current filter."
   :badge (fn [it] (when (= :directory (:item/kind it)) "Folder"))
   :description "Browse a directory."})

(defn opts-for
  "View options for a catalog, titled with the directory being browsed."
  [cat]
  (assoc view-opts
         :title (str "Files — " (:source/label (:catalog/descriptor cat)))))

(defn render [cat] (mui/->page (catalog/view cat) (opts-for cat)))
(defn render-html [cat] (mui/->html (catalog/view cat) (opts-for cat)))

(def default-query model/default-query)
