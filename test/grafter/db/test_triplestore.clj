(ns grafter.db.test-triplestore
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [grafter-2.rdf4j.io :as rio]
            [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.repository :as repo]
            [integrant.core :as ig]
            [grafter.db.triplestore :as triplestore]))

(derive :grafter.db/test-triplestore :grafter.db/triplestore)

(defn add-data! [triplestore update-endpoint load-files]
  (with-open [conn (repo/->connection (repo/sparql-repo "" update-endpoint))]
    (doseq [f load-files]
      (println "Loading file " f)
      (if-let [file-contents (io/resource f)]
        (pr/add conn (rio/statements file-contents))
        (throw
          (ex-info (str "Could not load resource file. Check the path is correct: " f)
                   {:file f}))))
    (assoc triplestore
           :load-files load-files
           :update-repo conn)))

(defmethod ig/init-key :grafter.db/test-triplestore
  [_ {:keys [query-cache query-endpoint load-files update-endpoint] :as _opts}]
  (let [repo (repo/sparql-repo query-endpoint update-endpoint)
        triplestore (triplestore/init query-cache repo)]
    (if update-endpoint
      (add-data! triplestore update-endpoint load-files)
      triplestore)))
