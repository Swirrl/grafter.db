(ns grafter.db.triplestore.impl
  (:require [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.repository :as repo])
  (:import [org.eclipse.rdf4j.repository Repository RepositoryConnection]))


;; Use build-triple-store to construct these
(defrecord TripleStoreBoundary [query-cache repo evaluation-method])

(defprotocol GetQueryCache
  (query-cache [this]))

(defprotocol ToggleQueryCaching
  (toggle-query-caching [this bool])
  (query-caching-status [this]))

(extend-type Repository
  ;; Raw sesame repo's can't have a query cache so return nil
  GetQueryCache
  (query-cache [_]
    nil)

  ToggleQueryCaching
  (toggle-query-caching [this bool]
    this)

  (query-caching-status [this]
    false))

(extend-type RepositoryConnection
  GetQueryCache
  (query-cache [_]
    nil)

  ToggleQueryCaching
  (toggle-query-caching [this bool]
    this)

  (query-caching-status [this]
    false))

(defprotocol GetRepo
  (repo [this]))

(extend-type TripleStoreBoundary
  GetRepo
  (repo [this]
    (:repo this))

  pr/ISPARQLable
  (pr/query-dataset [this sparql-string model]
    ;; NOTE this does not honour the query-cache/evaluation-mode yet.
    (pr/query-dataset (repo this) sparql-string model))

  pr/ITripleReadable
  (pr/to-statements [this opts]
    (pr/to-statements (repo this) opts))

  repo/ToConnection
  (repo/->connection [this]
    (repo/->connection (repo this)))

  pr/ISPARQLUpdateable
  (pr/update! [this sparql-string]
    (pr/update! (repo this) sparql-string))

  pr/ITripleWriteable
  (pr/add-statement
    ([this statement]
     (pr/add-statement (repo this) statement))

    ([this graph statement]
     (pr/add-statement (repo this) graph statement)))

  (pr/add
    ([this quads]
     (pr/add (repo this) this quads))

    ([this graph quads]
     (pr/add (repo this) graph quads))

    ([this graph format triple-stream]
     (pr/add (repo this) graph format triple-stream))

    ([this graph base-uri format triple-stream]
     (pr/add (repo this) graph base-uri format triple-stream)))

  pr/ITripleDeleteable
  (pr/delete-statement
    ([this statement]
     (pr/delete-statement (repo this) statement))

    ([this graph statement]
     (pr/delete-statement (repo this) graph statement)))

  (pr/delete
    ([this quads]
     (pr/delete (repo this) quads))

    ([this graph triples]
     (pr/delete (repo this) graph triples)))

  GetQueryCache
  (query-cache [this]
    (and (:query-caching-status this true)
         (:query-cache this)))

  ToggleQueryCaching
  (toggle-query-caching [this bool]
    (assoc this :query-caching-status bool))

  (query-caching-status [this]
    (:query-caching-status this true)))


(defn build-triple-store
  "Use this function to construct a TripleStoreBoundary"
  ([query-cache repo]
   (build-triple-store query-cache repo :eager))
  ([query-cache repo evaluation-method]
   (->TripleStoreBoundary query-cache repo evaluation-method)))
