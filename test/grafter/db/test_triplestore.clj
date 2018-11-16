(ns grafter.db.test-triplestore
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf.repository :as repo]
            [integrant.core :as ig]
            [grafter.db.triplestore :as triplestore]))

(derive :grafter.db/test-triplestore :grafter.db/triplestore)

(defn- add-data! [triplestore update-endpoint load-files]
  (let [update-repo (repo/sparql-repo "" update-endpoint)]
    (doseq [f load-files]
      (println "Loading file " f)
      (if-let [file-contents (io/resource f)]
        (grafter.rdf/add update-repo (grafter.rdf/statements file-contents))
        (throw
          (ex-info (str "Could not load resource file. Check the path is correct: " f)
                   {:file f}))))
    (assoc triplestore
           :load-files load-files
           :update-repo update-repo)))

(defmethod ig/init-key :grafter.db/test-triplestore
  [_ {:keys [query-cache query-endpoint load-files update-endpoint] :as _opts}]
  (let [repo (repo/sparql-repo query-endpoint)
        triplestore (triplestore/init query-cache repo)]
    (if update-endpoint
      (add-data! triplestore update-endpoint load-files)
      triplestore)))
