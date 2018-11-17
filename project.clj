(defproject grafter/db "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0-beta5"]
                 [org.clojure/core.cache "0.7.1"]

                 [com.taoensso/timbre "4.10.0"]
                 [duct/core "0.7.0-beta2"]
                 [grafter "0.11.7"]])

:profiles
{:dev [:project/dev :profiles/dev]
 :repl {:prep-tasks ^:replace ["javac" "compile"]}

 :test [:dev {:prep-tasks ^:replace ["javac" "compile"]
              :jvm-opts ["-Xmx4g"]}]

 :profiles/dev {}
 :project/dev {:jvm-opts [;; never collapse repeated stack traces in dev
                          "-XX:-OmitStackTraceInFastThrow"]
               :resource-paths ["test/resources"]}}