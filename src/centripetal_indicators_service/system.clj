(ns centripetal-indicators-service.system
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [centripetal-indicators-service.http :as http]
            [centripetal-indicators-service.db :as db]
            [config.core :as config])
  (:gen-class))

(defn build-system
  []
  (component/system-map
   :http (component/using (http/pedestal-http-server (:port config/env))
                          [:db])
   :db (db/json-file-db (io/resource "indicators.json"))))

(defn -main
  [& _args]
  (log/info "system starting")
  (let [system (-> (build-system)
                   (component/start))]
    (log/info "system done starting")
    system))

; TODO a better way to change things at the repl?
(comment
  (def s (-main))
  (component/stop s))

