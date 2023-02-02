(ns grafter.db.triplestore
  (:require [clojure.java.io :as io]
            [grafter-2.rdf4j.io :as rio]
            [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.repository :as repo]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [grafter.db.triplestore.impl :as impl]))

(derive :grafter.db/triplestore :duct/database)

(defn init
  ([query-cache repo]
   (init query-cache repo :eager))
  ([query-cache repo evaluation-method]
   (impl/build-triple-store query-cache repo evaluation-method)))

(defn- add-data! [update-endpoint load-files]
  (when (and update-endpoint (seq load-files))
    (with-open [conn (repo/->connection (repo/sparql-repo "" update-endpoint))]
      (doseq [f load-files]
        (pr/add conn (rio/statements (io/resource f))))
      (repo/shutdown conn))))

(defmethod ig/init-key :grafter.db/triplestore
  [_ {:keys [query-cache query-endpoint load-files update-endpoint] :as _opts}]
  (let [repo (if update-endpoint
               (repo/sparql-repo query-endpoint update-endpoint)
               (repo/sparql-repo query-endpoint))
        triplestore (init query-cache repo)]
    (add-data! update-endpoint load-files)
    triplestore))

;; Update only endpoint
(defmethod ig/init-key :grafter.db/update-endpoint [_ {:keys [update-endpoint]}]
  (repo/sparql-repo nil update-endpoint))

(defmethod ig/halt-key! :grafter.db/update-endpoint [_ repo]
  (repo/shutdown repo))


(s/def ::valid-fixture-config (fn [config]
                                (if (:load-files config)
                                  (:update-endpoint config)
                                  true)))

(s/def ::load-files (s/+ string?))

(s/def ::update-endpoint string?)
(s/def ::query-endpoint string?)

(s/def ::query-repo (s/keys :req-un [::query-endpoint]))

(s/def ::update-repo (s/keys :req-un [::query-endpoint ::update-endpoint]))

(s/def ::fixture-repo (s/keys :req-un [::load-files ::update-endpoint]))

(s/def :grafter.db/triplestore (s/or :fixture-repo ::fixture-repo
                                     :update-repo ::update-repo
                                     :query-repo ::query-repo
                                     :connectable #(satisfies? repo/ToConnection %)))

(defmethod ig/pre-init-spec :grafter.db/triplestore [_]
  :grafter.db/triplestore)

(defmethod ig/halt-key! :grafter.db/triplestore [_ {:keys [repo _logger]}]
  (when repo
    (repo/shutdown repo)))
