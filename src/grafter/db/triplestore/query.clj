(ns grafter.db.triplestore.query
  (:require [clojure.core.cache :as cache]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf4j.sparql :as sp]
            [grafter.db.triplestore.impl :as triplestore])
  (:import grafter.db.triplestore.impl.TripleStoreBoundary))

(defn keyname
  "For namespaced keywords, (name) only returns the keyword part.
  This function returns the namespace and keyword"
  [key]
  (str (namespace key) "/" (name key)))

(defprotocol EvaluationMethod
  (evaluation-method [this]))

(extend-type TripleStoreBoundary
  EvaluationMethod
  (evaluation-method [this]
    ;; default evaluation should be :eager
    (:evaluation-method this :eager)))

(defn wrap-eager-evaluation [query-fn]
  (fn [sparql-file opts bindings repo]
    (with-open [conn (repo/->connection repo)]
      (let [res (query-fn sparql-file opts bindings conn)]
        (if (seq? res)
          (doall res)
          ;; if not a sequence then its an ask query
          res)))))

(defn wrap-caching [query-fn]
  (fn [sparql-resource opts bindings repo]
    (let [run-query (delay
                      (query-fn sparql-resource opts bindings repo))
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

(defn wrap-try-query [query-fn]
  (fn [sparql-file opts bindings repo]
    (try
      (query-fn sparql-file opts bindings repo)
      (catch Throwable t
        (let [e (Throwable->map t)]
          (throw
            (ex-info (str "Grafter DB: Failure to execute " (:evaluation-method repo) " query against " (:repo repo) ".\n"
                          (:cause e)
                          "\nCheck that your DB is running. Check your SPARQL Repo config.")
                     e)))))))

(defmulti get-query-fn identity)

(defmethod get-query-fn :eager [_]
  (wrap-caching (wrap-try-query (wrap-eager-evaluation sp/query))))

(defmethod get-query-fn :lazy [_]
  ;; note we don't cache :lazy queries as they may have very large results
  (wrap-try-query sp/query))

(defmethod get-query-fn :default [_]
  (wrap-try-query (wrap-eager-evaluation sp/query)))

(defn kmap
  "update all keys in a hash-map with function"
  [f m]
  (into {} (map #(update-in % [0] f) m)))

(defn rename-binding
  "Rename clojure style hyphenated variable-bindings to sparql style
  underscored_bindings. Exclude ::sp/limits and ::sp/offsets."
  [binding]
  (if (sequential? binding)
    (into [] (map rename-binding binding))
    (let [binding-str (if (qualified-keyword? binding)
                        (keyname binding)
                        (name binding))]
      (->> (if (#{::sp/limits ::sp/offsets} binding)
             binding-str
             (str/replace binding-str #"-" "_"))
           (keyword)))))

(defn generate-bindings [args-vector]
  (->> args-vector
       (map (fn [arg]
              (if (sequential? arg)
                (let [n (gensym (apply str "values-clause-" (map (comp symbol name) arg)))]
                  [arg n])
                [(rename-binding arg)
                 (symbol (name arg))])))
       (into {})))

(defmacro defquery
  "Define a function named var-name for running a sparql query via
  grafter-2.rdf4j/sparql that is defined in the given sparql-resource file. The
  symbols used for argument are expected to map directly to variables in the
  query.

  - `:reasoning?` `true|false` whether or not reasoning/inference should be used
    in the query. DEFAULT: `false`


  e.g. for a SPARQL resource-file containing the query:

  SELECT * WHERE { ?s ?p ?o }

  The declaration should contain keywords defining the variables you wish to bind.
  e.g.:

  (defquery select-spo \"sparql/select-spo.sparql\" [:s])

  Then to select ?p & ?o on an <http://s> pass a bindings map of SPARQL var
  name to its val:

  (select-spo {:s (URI. \"http://s\")})

  Special namespaced keyword bindings can be supplied for limits & offsets:

  - :grafter-2.rdf4j.sparql/limits
  - :grafter-2.rdf4j.sparql/offsets

  ::sp/limits and ::sp/offsets both take a map of of limits that correspond to
  the overrides in the query.  For example with the query:

  SELECT * WHERE {
    SELECT ?s WHERE {
      ?s ?p ?o .
    } LIMIT 10
  } LIMIT 1

  We can override both the inner and outer limits by specifying a value of:

  {::sp/limits {10 100 ;; override inner limit with 100
                1 1000 ;; override outer limit with 1000
  }}

  Generated query functions take an optional map of options as their last
  parameter.  Valid options are:

  - `:evaluation-method` a keyword for the evaluation function
    (see get-query-fn)

  NOTE also that SPARQL variables ?snake_style variable can be referenced with
  clojure-style-kebab-case names."
  ([var-name sparql-resource args-vector]
   `(defquery ~var-name ~sparql-resource ~args-vector {:reasoning? false}))
  ([var-name sparql-resource args-vector {:keys [reasoning?] :as opts}]
   (let [doc-args (generate-bindings args-vector)
         docstring (str "Grafter query function that takes a repo, "
                        doc-args ".\n\n"
                        "It runs the SPARQL query: \n\n"
                        (slurp (io/resource sparql-resource)) "\n"
                        "With the variables " doc-args " bound to the supplied values.")]
     `(def ~var-name
        ~docstring
        (fn ~'graph-fn
          ([repo#]
           (~'graph-fn repo# nil nil))

          ([repo# binding-args#]
           (~'graph-fn repo# binding-args# nil))

          ([repo# binding-args#
            {:keys [~'evaluation-method] :as opts#}]
           (let [query-fn# (get-query-fn (or ~'evaluation-method (evaluation-method repo#)))
                 new-bindings# (kmap rename-binding binding-args#)]
             (query-fn# ~sparql-resource
                        ~opts
                        new-bindings#
                        repo#))))))))

(defmethod ig/init-key :grafter.db.triplestore/query [_ _opts]
  (fn [repo sparql-file bindings]
    ((wrap-caching (wrap-try-query (wrap-eager-evaluation sp/query)))
      sparql-file bindings repo)))
