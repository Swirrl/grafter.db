(ns grafter.db.triplestore.query
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [grafter.rdf.repository :as repo]
            [grafter.rdf.sparql :as sp]
            [grafter.db.triplestore.impl :as triplestore])
  (:import grafter.db.triplestore.impl.TripleStoreBoundary))

(defprotocol EvaluationMethod
  (evaluation-method [this]))

(extend-type TripleStoreBoundary
  EvaluationMethod
  (evaluation-method [this]
    ;; default evaluation should be :eager
    (:evaluation-method this :eager)))

(defn wrap-eager-evaluation [query-fn]
  (fn [sparql-file bindings repo]
    (with-open [conn (repo/->connection repo)]
      (let [res (query-fn sparql-file bindings conn)]
        (if (seq? res)
          (doall res)
          ;; if not a sequence then its an ask query
          res)))))

(defn wrap-caching [query-fn]
  (fn [sparql-resource bindings repo]
    (let [run-query (delay
                      (query-fn sparql-resource bindings repo))
          qry-str (slurp (io/resource sparql-resource))]
      (if-let [cache (triplestore/query-cache repo)]
        (let [cache-key {:repo repo :sparql-query qry-str :bindings bindings}]
          (cache/lookup (swap! cache
                               #(if (cache/has? % cache-key)
                                  (do (log/debug :cache-hit {:query qry-str :bindings bindings})
                                      (cache/hit % cache-key))
                                  (cache/miss % cache-key
                                              (do (log/debug :cache-miss {:query qry-str :bindings bindings})
                                                  @run-query))))
                        cache-key))
        @run-query))))

(defmulti get-query-fn identity)

(defmethod get-query-fn :eager [_]
  (wrap-caching (wrap-eager-evaluation sp/query)))

(defmethod get-query-fn :lazy [_]
  ;; note we don't cache :lazy queries as they may have very large results
  sp/query)

(defmethod get-query-fn :default [_]
  ;; Use the lazy query function from grafter.rdf.sparql
  ;; Note default here is what sesame Repositories will use
  sp/query)

(defn- rename-binding
  "Rename clojure style hyphenated variable-bindings to sparql style
  underscored_bindings."
  [binding]
  (str/replace (name binding) #"-" "_"))

(defn- generate-bindings [arg]
  (if (sequential? arg)
    (let [n (gensym (apply str "values-clause-" (map name arg)))]
      [arg n])
    [(keyword (rename-binding arg))
     (symbol (name arg))]))

(defn- make-defquery* [var-name sparql-resource args-vector]
  (let [doc-args (str/join ", " (map (comp second generate-bindings) args-vector))
        docstring (str "Grafter query function that takes a repo, "
                       doc-args ".\n\n"
                       "It runs the SPARQL query: \n\n"
                       (slurp (io/resource sparql-resource)) "\n"
                       "With the variables " doc-args " bound to the supplied values.")]
    `(defquery ~var-name ~docstring ~sparql-resource ~args-vector)))

(defmacro defquery
  "Define a function named var-name for running a sparql query via
  grafter.rdf/sparql that is defined in the given sparql-resource
  file.  The symbols used for argument are expected to map directly to
  variables in the query.

  e.g. for a SPARQL resource-file containing the query:

  SELECT * WHERE { ?s ?p ?o }

  The declaration should contain keywords defining the variables you
  wish to bind.  e.g.

  (defquery select-spo \"sparql/select-spo.sparql\" [:s])

  Then to select ?p and ?o on an <http://s>

  (select-spo (URI. \"http://s\"))

  Generated query functions take an optional map of options as their
  last parameter.  Valid options are:

  - :evaluation-method a keyword for the evaluation function (see get-query-fn)
  - :grafter.rdf.sparql/limits
  - :grafter.rdf.sparql/offsets

  ::sp/limits and ::sp/offsets both take a map of of limits that
  correspond to the overrides in the query.  For example with the query:

  SELECT * WHERE {
    SELECT ?s WHERE {
      ?s ?p ?o .
    } LIMIT 10
  } LIMIT 1

  We can override both the inner and outer limits by specifying a
  value of:

  {::sp/limits {10 100 ;; override inner limit with 100
                1 1000 ;; override outer limit with 1000
  }}

  NOTE also that SPARQL variables ?snake_style variable can be
  referenced with clojure-style-kebab-case names."

  ([var-name sparql-resource args-vector]
   (make-defquery* var-name sparql-resource args-vector))
  ([var-name docstring sparql-resource args-vector]
   (let [args (map generate-bindings args-vector)
         bindings (into {} args)
         arg-names (map second args)]

     `(def ~var-name ~docstring (fn ~'graph-fn
                                  ([repo# ~@arg-names]
                                    (~'graph-fn repo# ~@arg-names {}))
                                  ([repo# ~@arg-names {:keys [~'evaluation-method] :as opts#}]
                                    (let [query-fn# (get-query-fn (or ~'evaluation-method (evaluation-method repo#)))
                                          limoffs# (select-keys opts# [::sp/limits ::sp/offsets])
                                          new-bindings# (merge ~bindings limoffs#)]
                                      (query-fn# ~sparql-resource new-bindings# repo#))))))))

(defmethod ig/init-key :zib.database.triplestore/query [_ _opts]
  (fn [repo sparql-file bindings]
    ((wrap-caching (wrap-eager-evaluation sp/query))
      sparql-file bindings repo)))

(comment

  (defquery graph-uri*
            "sparql/graph-uri.sparql"
            [:resource-uri {::sp/limits #{1}}])

  (defquery graph-uri*
            "sparql/graph-uri.sparql" [])

  (graph-uri repo (URI. "http://foo") 100)

  (graph-uri (assoc repo :evaluation-method :lazy)
             (URI. "http://foo")
             {::sp/limits {10 100} ::sp/offsets {0 10}})

  (defquery graph-uri* "sparql/graph-uri.sparql")
  )
