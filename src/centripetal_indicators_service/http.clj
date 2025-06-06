(ns centripetal-indicators-service.http
  (:require [centripetal-indicators-service.db :as db]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]                     
            [io.pedestal.http.route :as route]))

; Based on: https://pedestal.io/pedestal/0.7/guides/your-first-api.html

(defn respond-hello
  [request]
  {:status 200 :body (str "Hello, world! wat")})

(defn make-db-injector 
  [db]
  {:name :db-injector
   :enter
   (fn [context]
     (assoc context :db db))})

(def count-records
  {:name :count-records
   :leave
   (fn [{:keys [db] :as context}]
     (assoc context
            :response
            {:status 200
             :body (str "Hello, world! count = " (db/count-records db))}))})

(def find-by-id
  {:name :find-by-id
   :enter
   (fn [{:keys [db request] :as context}]
     (let [id (get-in request [:path-params :id])
           doc (db/find-by-id db id)]
       (cond-> context 
         (some? doc)
         (assoc context
                :response
                {:status 200
                 :body doc}))))})

(defn build-routes
  [db]
  (let [db-injector (make-db-injector db)]
    (route/expand-routes
      #{["/count" :get [db-injector count-records]]
        ["/indicators/:id" :get [db-injector find-by-id]]})))

(defrecord PedestalHttpServer
  [port db server]
  component/Lifecycle
  (start [this]
    (log/info "pedestal http server starting")
    (let [server (-> {::http/routes (build-routes db)
                      ::http/type :jetty
                      ::http/port port 
                      ::http/join? false}
                     http/create-server
                     http/start)]
      (log/info "pedestal http server done starting")
      (assoc this :server server)))
  (stop [this]
    (http/stop server)
    (assoc this :server nil :db nil)))

(defn pedestal-http-server
  [port]
  (map->PedestalHttpServer {:port port}))
