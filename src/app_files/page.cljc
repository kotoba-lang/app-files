(ns app-files.page
  "SSR entry: a catalog value in, a complete document out.

  The theme map is the one place in this repo where a hex colour is
  legitimate (kotoba-ui rule 5). Everything else — every colour, size, radius
  and spacing decision on the page — comes from `--hig-*` tokens that the
  appearance flips automatically, so there is no second dark palette to keep
  in sync."
  (:require [app-files.view :as view]
            [kotoba-ui.core :as ui]
            [mokuroku.catalog :as catalog]))

(def theme
  {:accent "#3E7BFA"
   :appearance :auto})

(def app-css
  "Unlayered app CSS, so it wins over the library layers without a single
  compound selector (kotoba-ui rule 3). Three rules: the row's column
  template, the inspector's field grid, and the selected-row tint — all
  spending tokens, never literals."
  (str
   ".app-files__row{display:grid;"
   "grid-template-columns:minmax(0,1fr) 8rem 6rem;"
   "gap:var(--hig-spacing-3);align-items:baseline;min-width:0}"
   ".app-files__cell{min-width:0;overflow-wrap:break-word}"
   ".app-files__cell--size{text-align:right;"
   "font-variant-numeric:tabular-nums}"
   ".app-files__row--selected{background:var(--hig-fill-quaternary)}"
   ".app-files__command--destructive{color:var(--hig-palette-red)}"
   ".app-files__fields{display:grid;gap:var(--hig-spacing-2)}"
   ".app-files__field{display:flex;justify-content:space-between;"
   "gap:var(--hig-spacing-3);min-width:0}"))

(defn render
  "The whole window as an HTML document."
  [cat]
  (ui/->page {:title (str "Files — " (:source/label (:catalog/descriptor cat)))
              :description "Browse a directory."
              :theme theme
              :head [[:style app-css]]}
             (view/window (catalog/view cat))))

(defn render-html
  "Just the window fragment, for mounting into an existing document."
  [cat]
  (ui/->html (view/window (catalog/view cat))))
