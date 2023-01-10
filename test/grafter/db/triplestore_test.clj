(ns grafter.db.triplestore-test
  (:require [grafter.db.triplestore :as sut]
            [clojure.test :as t]
            [grafter.db.test-helpers :as th]
            [grafter.db.triplestore.query :as q]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf.protocols :as pr])
  (:import (java.net URI)))


(def test-triple (pr/->Triple (URI. "http://a") (URI. "http://a") (URI. "http://a")))

(q/defquery select-s-p-o
  "sparql/select-s-p-o.sparql"
  [:s])

(t/deftest writing-to-repo
  (let [repo (th/load-test-repo)]

    (try
      (with-open [conn (repo/->connection repo)]
        (pr/add conn [test-triple]))

      (t/is (= [{:s (URI. "http://a") ;; note whether :s binding is returned or not may be implementation dependent
                 :p (URI. "http://a")
                 :o (URI. "http://a")}]
               (select-s-p-o repo {:s (URI. "http://a")}))))))
