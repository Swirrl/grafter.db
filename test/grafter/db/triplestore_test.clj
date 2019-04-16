(ns grafter.db.triplestore-test
  (:require [grafter.db.triplestore :as sut]
            [clojure.test :as t]
            [grafter.db.test-helpers :as th]
            [grafter.db.triplestore.query :as q]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf.protocols :as pr])
  (:import (java.net URI)))


(t/use-fixtures :once (th/wrap-test-system [:duct.profile/test]))


(def test-triple (pr/->Triple (URI. "http://a") (URI. "http://a") (URI. "http://a")))

(q/defquery select-s-p-o
  "sparql/select-s-p-o.sparql"
  [:s])

(t/deftest writing-to-repo
  (let [update-endpoint (:grafter.db/update-endpoint th/*test-system*)
        query-endpoint (:grafter.db/test-triplestore th/*test-system*)]

    (try
      (with-open [conn (repo/->connection update-endpoint)]
        (pr/add conn [test-triple]))

      (t/is (= [{:p (URI. "http://a") :o (URI. "http://a")}]
               (select-s-p-o query-endpoint {:s (URI. "http://a")})))

      (finally
        (pr/update! (repo/->connection update-endpoint) "DROP ALL")))))
