(defproject solar-test-task "0.1.0-SNAPSHOT"
  :description "Solar test task"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.2"]
                 [org.clojure/core.async "1.5.648"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler solar-test-task.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
