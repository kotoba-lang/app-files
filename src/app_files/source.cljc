(ns app-files.source
  "The `fs/browse` seam.

  A source here is a function the host supplies: directory path in, entry
  vector out. That function is where the capability is spent, and it is the
  only thing in this repo that can fail for an outside reason. Everything
  else — normalising, sorting, selecting, proposing — is total.

  Nothing in this namespace opens a file, and there is no fallback that reads
  the real filesystem when a grant is missing. A `capability/browse` provider
  that was never granted yields `:denied`, and the app says so; it does not
  quietly show an empty directory, which is indistinguishable from a directory
  that is genuinely empty."
  (:require [app-files.model :as model]
            [clojure.string :as str]
            [mokuroku.source :as source]))

;; ── confinement ─────────────────────────────────────────────────────────────
;;
;; The grant is for one directory. Nothing here verified that what came back
;; was in it: `-fetch` handed the provider's rows straight to `listing->items`,
;; so a provider that returned `/etc/passwd` while the grant was for `/w` got
;; it displayed as though it were inside `/w`. Whether that is a hostile
;; provider or a buggy one does not change what the user sees.
;;
;; `bounded_validate.kotoba` states this property already, as `confined?`, and
;; it is NOT what runs here. It cannot be: its entries carry an explicit
;; `:parent` keyword and a provider row has no `:parent` at all, its ids are
;; `:keyword` where real ids are paths, and its sibling `bounded?` caps a
;; listing at eight entries -- which is a fixture bound, not a directory's.
;; That module is a bounded profile that proves the property on an abstract
;; model; this is the same property on the real one. `app-files.confinement-test`
;; is what keeps the two saying the same thing.

(defn- parent-of
  "The directory holding `path`, or nil when there is not one.

  Trailing slashes are not significant: a provider that says `/w/src/` and one
  that says `/w/src` named the same entry, and only one of them would pass a
  string comparison against `/w`."
  [path]
  (let [trimmed (str/replace (str path) #"/+$" "")
        idx (str/last-index-of trimmed "/")]
    (cond
      (str/blank? trimmed) nil
      (nil? idx) nil
      (zero? idx) "/"
      :else (subs trimmed 0 idx))))

(defn confined?
  "Is `entry` a direct child of `dir`?

  Direct, not descendant: a listing of `/w` that contains `/w/src/main.clj`
  has been given something it did not ask for, even though the path is under
  the grant. The Kotoba core says the same thing by comparing `:parent` to
  `:dir` for equality rather than by prefix."
  [dir entry]
  (let [dir* (str/replace (str dir) #"/+$" "")
        dir* (if (str/blank? dir*) "/" dir*)]
    (= dir* (parent-of (:path entry)))))

(defn escapees
  "The rows a provider returned that are not inside `dir`.

  Exposed because dropping them is the safe default and silence is not: an app
  that wants to tell the user its provider misbehaved needs to be able to ask."
  [dir entries]
  (vec (remove #(confined? dir %) entries)))

(defn confine
  "Only the rows that are inside `dir`."
  [dir entries]
  (vec (filter #(confined? dir %) entries)))

(defrecord BrowseSource [dir browse-fn]
  source/ISource
  (-descriptor [_] (model/descriptor dir))
  ;; Filtered rather than thrown: `-fetch` has no error channel, and a file
  ;; browser that crashes on one bad row is worse than one that shows the rows
  ;; the grant covers. What must not happen -- showing the others -- does not.
  (-fetch [_] (model/listing->items (confine dir (browse-fn dir)))))

(defn browse-source
  "A source over an injected `fs/browse` provider.

  BROWSE-FN takes a directory path and returns a vector of entry maps. It is
  the host's, and it is where the granted capability is exercised."
  [dir browse-fn]
  (->BrowseSource dir browse-fn))

(defn fixture-source
  "A source over a literal entry vector — fixtures, tests, and the state an
  app is in before a grant arrives."
  [dir entries]
  (browse-source dir (constantly entries)))

(def denied
  "What the app shows when the capability was refused.

  Not an empty listing: an empty directory and a denied grant look identical
  in a list, and only one of them is the user's fault to fix."
  {:browse/state :denied
   :browse/capability model/capability
   :browse/entries []})

(defn granted [entries]
  {:browse/state :granted
   :browse/capability model/capability
   :browse/entries (vec entries)})

(defn denied? [result]
  (= :denied (:browse/state result)))
