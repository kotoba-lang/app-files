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
            [mokuroku.source :as source]))

(defrecord BrowseSource [dir browse-fn]
  source/ISource
  (-descriptor [_] (model/descriptor dir))
  (-fetch [_] (model/listing->items (browse-fn dir))))

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
