(defproject grafter.db "0.9.0"
  :description "Grafter SPARQL database query tools"
  :url "https://github.com/Swirrl/grafter.db"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.8"

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/core.cache "0.7.1"]
                 [integrant "0.7.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [io.github.swirrl/grafter.repository "3.0.0"]]

  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:prep-tasks ^:replace ["javac" "compile"]}

   :test [:dev {:prep-tasks ^:replace ["javac" "compile"]
                :jvm-opts ["-Xmx4g"]}]

   :profiles/dev {}

   :project/dev {:jvm-opts [;; never collapse repeated stack traces in dev
                            "-XX:-OmitStackTraceInFastThrow"]
                 :resource-paths ["test/resources"]}}

  :jar-exclusions [#"^sparql"]

  :deploy-repositories [["releases" :clojars]])
