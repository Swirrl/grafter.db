(ns grafter.db.test-helpers
      (:require [clojure.test :refer :all]
                [integrant.core :as ig]
                [clojure.java.io :as io]
                [duct.core :as duct]))

  (def ^:dynamic *test-system*)

  (defn read-config []
        (duct/read-config (io/resource "test-config.edn")))

  (defn prep-test-system [profiles]
        (duct/load-hierarchy)
        (-> (read-config)
            (duct/prep-config profiles)))

  (defn wrap-test-system
        "Function for building a clojure.test fixture function that will start
        & stop a Duct (Integrant) system.

        You must supply a seq of duct profile keys to specify the test system
        config e.g.:

        (use-fixtures :once (h/wrap-test-system [:duct.profile/test]))"
        [profiles]
        (fn [t]
          (let [sys (try
                      (-> (prep-test-system profiles)
                          (ig/init))
                      (catch Exception ex
                        (throw
                          (ex-info "Error prepping test-system" {:profiles profiles} ex))))]
            (binding [*test-system* sys]
              (try
                (t)
                (finally
                  (ig/halt! sys)))))))

  (defmethod ig/prep-key :duct.profile/test [_ profile]
             (-> (ig/prep-key :duct/profile profile)
                 (assoc :duct.core/environment :test)))
