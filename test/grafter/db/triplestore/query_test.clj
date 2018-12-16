(ns grafter.db.triplestore.query-test
  (:require [clojure.test :refer :all]
            [grafter.db.test-helpers :as th]
            [grafter.db.triplestore.query :as q]
            [grafter.rdf.sparql :as sp]
            [grafter.vocabularies.qb :refer :all]
            [grafter.vocabularies.rdf :refer :all])
  (:import (java.net URI)
           (grafter.rdf.protocols Quad)))

(use-fixtures :once (th/wrap-test-system [:duct.profile/test]))

(q/defquery select-observations-qry
  "sparql/select-observation.sparql"
  [])

(q/defquery select-observations-bindings-qry
  "sparql/select-observation.sparql"
  [:obs])

(q/defquery select-multiple-bindings-qry
  "sparql/select-s-p-o.sparql"
  [:s :p])

(q/defquery select-observations-values-clause-qry
  "sparql/select-observation-by-values.sparql"
  [[:obs]])

(q/defquery construct-observations-qry
  "sparql/construct-observation.sparql"
  [])

(deftest generate-query-bindings-test
  (testing "binding is a map of :key var-name tuples"
    (let [bindings (q/generate-bindings [:foo :bar])]
      (is (= bindings
             {:foo 'foo
              :bar 'bar}))))

  (testing "sequential binding keys generate a VALUES clause binding"
    (let [bindings (q/generate-bindings [:foo [:baz :qux]])]
      (is (= (:foo bindings) 'foo))
      (is (->> (get bindings [:baz :qux])
               (name)
               (re-matches #"values-clause-bazqux\d+")))))
)

(deftest defquery-test
  (let [t-store (:grafter.db/test-triplestore th/*test-system*)]
    (testing "simple queries without bindings"
      (testing "select query returns collection of observations hash-maps"
        (let [results (select-observations-qry t-store {::sp/limits {1000 3}})
              first-obs (first results)]
          (is (= (count results) 3))
          (is (= (:measure_val first-obs) 100N))
          (is (= (:obs first-obs)
                 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/1994/S92000003/index-1994-100/count")))))

      (testing "construct query returns collection of qb:Observations quads"
        (let [results (construct-observations-qry t-store {::sp/limits {1000 3}})
              first-quad (first results)]
          (is (= (count results) 6))
          (is (instance? Quad first-quad))
          (is (= (:s first-quad)
                 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/1994/S92000003/index-1994-100/count")))
          (is (= (:p first-quad) rdf:a))
          (is (= (:o first-quad) qb:Observation)))))

    (testing "simple query with binding"
      (let [obs-uri (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2011/S92000003/index-1994-100/count")
            result (select-observations-bindings-qry t-store {:obs obs-uri})]
        (is (= (-> result first :measure_val)
               110.6))
        (is (= (-> result first :obs)
               obs-uri))))

    (testing "query with multiple key-value pairs in binding hash-map"
      (let [s (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2012/S92000003/index-1994-100/count")
            p (URI. "http://statistics.gov.scot/def/measure-properties/count")
            result (select-multiple-bindings-qry t-store {:s s :p p})]
        (is (= (-> result first)
               {:o 116.4}))))

    (testing "simple query with VALUES bindings"
      (let [obs-uri1 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/1994/S92000003/index-1994-100/count")
            obs-uri2 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2011/S92000003/index-1994-100/count")
            result (select-observations-values-clause-qry t-store {:obs [obs-uri1 obs-uri2]})]
        (is (= 2 (count result)))
        (is (= (-> result first :measure_val)
               100N))
        (is (= (-> result first :obs)
               obs-uri1))))))

(comment
  (require '[grafter.rdf.repository :as repo])
  (require '[grafter.db.triplestore.impl :as triplestore])
  (def repo (repo/sparql-repo "http://localhost:5820/grafter-db-dev/query"))
  (def t-store (triplestore/->TripleStoreBoundary nil repo :eager)))


