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

(q/defquery construct-observations-qry
  "sparql/construct-observation.sparql"
  [])

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

    (testing "simple query with bindings"
      (let [obs-uri (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2011/S92000003/index-1994-100/count")
            result (select-observations-bindings-qry t-store obs-uri)]
        (is (= (-> result first :measure_val)
               110.6))
        (is (= (-> result first :obs)
               obs-uri))))))



