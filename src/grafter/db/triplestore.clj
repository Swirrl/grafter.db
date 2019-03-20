(ns grafter.db.triplestore
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [grafter-2.rdf4j.io :as rio]
            [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.repository :as repo]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [grafter.db.triplestore.impl :as impl]))

;; TODO: remove this when Grafter is patched for xsd:Date literals
(defmethod rio/backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#date" [literal]
  (pr/raw-value literal))

(derive :grafter.db/triplestore :duct/database)

(defn init
  ([query-cache repo]
   (init query-cache repo :eager))
  ([query-cache repo evaluation-method]
   (log/report ::starting-triplestore {:query-endpoint (str repo)})
   (impl/build-triple-store query-cache repo evaluation-method)))

(defn- add-data! [update-endpoint load-files]
  (when (and update-endpoint (seq load-files))
    (with-open [conn (repo/->connection (repo/sparql-repo "" update-endpoint))]
      (doseq [f load-files]
        (log/debug ::loading-file f)
        (pr/add conn (rio/statements (io/resource f))))
      (repo/shutdown conn))))

(defmethod ig/init-key :grafter.db/triplestore
  [_ {:keys [query-cache query-endpoint load-files update-endpoint] :as _opts}]
  (let [repo (repo/sparql-repo query-endpoint)
        triplestore (init query-cache repo)]
    (add-data! update-endpoint load-files)
    triplestore))

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
                                       :query-repo ::query-repo))

(defmethod ig/pre-init-spec :grafter.db/triplestore [_]
  :grafter.db/triplestore)

(defmethod ig/halt-key! :grafter.db/triplestore [_ {:keys [repo _logger]}]
  (log/report ::stopping-triplestore)
  (when repo
    (repo/shutdown repo)))
