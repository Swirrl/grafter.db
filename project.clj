(defproject grafter.db "0.8.4-reasoning"
  :description "Grafter SPARQL database query tools"
  :url "https://github.com/Swirrl/grafter.db"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.5.0"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.cache "0.7.1"]
                 [integrant "0.7.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [grafter "2.0.2"]]

  :profiles
  {:dev [:project/dev :profiles/dev]
   :repl {:prep-tasks ^:replace ["javac" "compile"]}

   :test [:dev {:prep-tasks ^:replace ["javac" "compile"]
                :jvm-opts ["-Xmx4g"]}]

   :profiles/dev {}

   :project/dev {:dependencies [[duct/core "0.7.0"]]
                 :jvm-opts [;; never collapse repeated stack traces in dev
                            "-XX:-OmitStackTraceInFastThrow"]
                 :resource-paths ["test/resources"]}}

  :plugins [[s3-wagon-private "1.3.1"]]

  :jar-exclusions [#"^sparql"]

  :release-tasks [["vcs" "assert-committed"]
                  ["deploy" "swirrl-jars"]]

  :repositories [["releases" {:sign-releases false
                              :url "s3p://swirrl-jars/releases/"
                              :username :env/AWS_ACCESS_KEY_ID
                              :passphrase :env/AWS_SECRET_ACCESS_KEY
                              :snapshots false}]
                 ["snapshots" {:sign-releases false
                               :url "s3p://swirrl-jars/snapshots/"
                               :username :env/AWS_ACCESS_KEY_ID
                               :passphrase :env/AWS_SECRET_ACCESS_KEY
                               :releases false}]])
