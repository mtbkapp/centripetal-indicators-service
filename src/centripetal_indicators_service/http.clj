(ns centripetal-indicators-service.http
  (:require [centripetal-indicators-service.db :as db]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

; Based on: https://pedestal.io/pedestal/0.7/guides/your-first-api.html

(defn make-db-injector
  [db]
  {:name :db-injector
   :enter
   (fn [context]
     (assoc context :db db))})

(def ok-status 200)
(def bad-input-status 400)
(def not-found-status 404)

(defn json-response
  [context status body]
  (assoc context
         :response
         {:status status
          :body (json/generate-string body)
          :headers {"Content-Type" "application/json"}}))

(defn not-found
  [context]
  (assoc context :response {:status not-found-status}))

(def handle-find-by-id
  {:name :handle-find-by-id
   :enter
   (fn [{:keys [db request] :as context}]
     (let [id (get-in request [:path-params :id])
           doc (db/find-by-id db id)]
       (if (some? doc)
         (json-response context ok-status doc)
         (not-found context))))})

(defn query-from-params
  [query-params]
  (into ["and"]
        (map (fn [[path value]]
               ["=" (name path) value]))
        query-params))

(defn find-docs
  [context db query]
  (try
    (json-response context ok-status (db/find-docs db query))
    (catch ExceptionInfo ex
      (if (db/invalid-query-exception? ex)
        (json-response context bad-input-status (ex-data ex))
        (throw ex)))))

(def handle-find-with-params
  {:name :handle-find-with-params
   :enter
   (fn [{:keys [db request] :as context}]
     (let [query-params (:query-params request)]
       (if (empty? query-params)
         (json-response context ok-status (db/get-all db))
         (find-docs context db (query-from-params query-params)))))})

(def decode-json-body
  {:name :decode-json-body
   :enter
   (fn [context]
     (let [content-type (get-in context [:request :headers "content-type"])]
       (cond-> context
         (#{"text/json" "application/json"} content-type)
         (update-in [:request :body]
                    #(json/parse-stream (io/reader %) true)))))})

(def handle-search
  {:name :handle-search
   :enter
   (fn [{:keys [db request] :as context}]
     (let [query (:body request)]
       (find-docs context db query)))})

(defn build-routes
  [db]
  (let [db-injector (make-db-injector db)]
    (route/expand-routes
     #{["/indicators" :get [db-injector handle-find-with-params]]
       ["/indicators/:id" :get [db-injector handle-find-by-id]]
       ["/indicators/search" :post [db-injector decode-json-body handle-search]]})))

(defrecord PedestalHttpServer
           [port db server]
  component/Lifecycle
  (start [this]
    (log/info "pedestal http server starting")
    (let [server (-> {::http/routes (build-routes db)
                      ::http/router :linear-search
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
