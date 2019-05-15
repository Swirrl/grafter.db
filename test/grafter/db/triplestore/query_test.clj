(ns grafter.db.triplestore.query-test
  (:require [clojure.test :refer :all]
            [grafter.db.test-helpers :as th]
            [grafter.db.triplestore.query :as q]
            [grafter-2.rdf4j.sparql :as sp]
            [grafter.vocabularies.qb :refer :all]
            [grafter.vocabularies.rdf :refer :all]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf.protocols :as pr])
  (:import (java.net URI)
           (grafter_2.rdf.protocols Quad)))

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

(q/defquery select-renamed-bindings-qry
  "sparql/select-s-p-o-rename.sparql"
  [:key-to-be-renamed])

(q/defquery select-observations-values-single-bind-clause-qry
  "sparql/select-observation-by-single-bind-values.sparql"
  [:obs])

(q/defquery select-observations-values-double-bind-clause-qry
  "sparql/select-observation-by-double-bind-values.sparql"
  [[:p :o]])

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
      (testing "select query (without limits) returns collection of observations hash-maps"
        (let [results (select-observations-qry t-store)
              first-obs (first results)]
          (is (= (count results) 21))
          (is (= (:measure_val first-obs) 100N))
          (is (= (:obs first-obs)
                 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/1994/S92000003/index-1994-100/count")))))

      (testing "select query (with limits) returns collection of observations hash-maps"
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
        (is (= 1 (count result)))
        (is (= (-> result first :measure_val)
               110.6))
        (is (= (-> result first :obs)
               obs-uri))))

    (testing "query with multiple key-value pairs in binding hash-map"
      (let [s (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2012/S92000003/index-1994-100/count")
            p (URI. "http://statistics.gov.scot/def/measure-properties/count")
            result (select-multiple-bindings-qry t-store {:s s :p p})]
        (is (= 1 (count result)))
        (is (= (-> result first)
               {:o 116.4}))))

    (testing "query with var bindings and limits in same hash-map"
      (let [s (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2012/S92000003/index-1994-100/count")
            unlimited-result (select-multiple-bindings-qry t-store {:s s})
            limited-result (select-multiple-bindings-qry t-store {:s s ::sp/limits {500 4}})]
        (is (= 7 (count unlimited-result)))
        (is (= 4 (count limited-result)))))

    (testing "query with kebab-case var bindings that require renaming to snake_case"
      (let [s (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2012/S92000003/index-1994-100/count")
            result (select-renamed-bindings-qry t-store {:key-to-be-renamed s})]
        ;; would be 197 results if renaming failed
        (is (= 7 (count result)))))

    (testing "simple query with single VALUES bindings"
      (let [obs-uri1 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/1994/S92000003/index-1994-100/count")
            obs-uri2 (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2011/S92000003/index-1994-100/count")
            result (select-observations-values-single-bind-clause-qry t-store {:obs [obs-uri1 obs-uri2]})]
        (is (= 2 (count result)))
        (is (= (-> result first :measure_val)
               100N))
        (is (= (-> result first :obs)
               obs-uri1))))

    (testing "simple query with double VALUES bindings"
      (let [p (URI. "http://purl.org/linked-data/sdmx/2009/dimension#refPeriod")
            o (URI. "http://reference.data.gov.uk/id/year/2006")
            expected-uri (URI. "http://statistics.gov.scot/data/terrestrial-breeding-birds/year/2006/S92000003/index-1994-100/count")
            result (select-observations-values-double-bind-clause-qry t-store {[:p :o] [[p o]]})]
        (is (= 1 (count result)))

        (is (= (-> result first :obs)
               expected-uri))))))



(comment
  (require '[grafter-2.rdf4j.repository :as repo])
  (require '[grafter.db.triplestore.impl :as triplestore])

  (require '[integrant.core :as ig])
  (require '[grafter.db.test-helpers :as th])

  (def test-system (-> (th/prep-test-system [:duct.profile/test])
                       (ig/init)))

  (select-observations-values-double-bind-clause-qry (:grafter.db/test-triplestore test-system)
                                                     {[:p :o] [[(java.net.URI. "http://p") (java.net.URI. "http://o")]]})

  (def repo (repo/sparql-repo "http://localhost:5820/grafter-db-dev/query"))


  (def t-store (triplestore/->TripleStoreBoundary nil repo :eager)))
