(ns grafter.db.test-helpers
  (:require [clojure.java.io :as io]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.io :as rio]
            [grafter.db.triplestore.query :as dbq])
  (:import [org.eclipse.rdf4j.repository Repository]))

(def ^:dynamic *test-repo*)

(extend-protocol dbq/EvaluationMethod
  Repository
  (evaluation-method [this]
    ;; force default evaluation in tests to :eager
    :eager))

(defn load-test-repo []
  (let [r (repo/sail-repo)]
    (with-open [c (repo/->connection r)]
      (pr/add c (rio/statements (io/resource "breeding-birds.nt"))))

    r))
