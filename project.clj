(defproject centripetal-indicators-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]

                 ; json
                 [cheshire "6.0.0"]

                 ; http / pedestal
                 [io.pedestal/pedestal.jetty "0.7.2"]

                 ; java logging
                 [org.slf4j/slf4j-simple "2.0.10"]
                 
                 ; dependency injection
                 [com.stuartsierra/component "1.1.0"]
                 
                 ; config management 
                 [yogthos/config "1.2.1"]
                 
                 ; logging
                 [org.clojure/tools.logging "1.3.0"]]
  :main ^:skip-aot centripetal-indicators-service.system
  :target-path "target/%s"
  :profiles {:dev {:jvm-opts ["-DPORT=8890"]}
             :uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})

