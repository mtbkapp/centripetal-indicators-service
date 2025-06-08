(ns centripetal-indicators-service.http
  (:require [centripetal-indicators-service.db :as db]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]))

; Based on: https://pedestal.io/pedestal/0.7/guides/your-first-api.html

(defn make-db-injector 
  [db]
  {:name :db-injector
   :enter
   (fn [context]
     (assoc context :db db))})

(defn json-response 
  [context body]
  (assoc context
         :response
         {:status 200
          :body (json/generate-string body)
          :headers {"Content-Type" "application/json"}}))

(defn not-found
  [context]
  (assoc context :response {:status 404}))

(def find-by-id
  {:name :find-by-id
   :enter
   (fn [{:keys [db request] :as context}]
     (let [id (get-in request [:path-params :id])
           doc (db/find-by-id db id)]
       (if (some? doc)
         (json-response context doc)
         (not-found context))))})

(def get-all
  {:name :get-all
   :enter
   (fn [{:keys [db request] :as context}]
     (let [query-params (:query-params request)]
       (clojure.pprint/pprint query-params)
       (json-response context (db/get-all db))))})

(defn build-routes
  [db]
  (let [db-injector (make-db-injector db)]
    (route/expand-routes
      #{["/indicators" :get [db-injector get-all]]
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
