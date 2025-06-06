(ns centripetal-indicators-service.db
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]))

; like PostgreSQLs Generialized Inverted Indexes (GIN)

(defn index-paths-from-json-value
  ([v] (index-paths-from-json-value [] v))
  ([path v]
   (cond (map? v)
         (mapcat (fn [[mk mv]]
                   (index-paths-from-json-value (conj path mk) mv))
                 v)
         (coll? v)
         (mapcat (fn [av]
                   (index-paths-from-json-value path av))
                 v)
         :else
         [[path v]])))

(defn add-doc-to-index
  [idx doc position]
  (reduce (fn [new-idx [path v]]
            (update-in new-idx
                       (concat path [::values v])
                       (fnil conj #{})
                       position))
          idx
          (index-paths-from-json-value doc)))

(defn index-docs
  [docs]
  (transduce (map-indexed vector)
             (completing
               (fn [idx [position doc]]
                 (add-doc-to-index idx doc position)))
             {}
             docs))

(defn get-positions
  [index path v]
  (get-in index (into path [::values v])))

(defprotocol Database
  (count-records [db])
  (find-by-id [db id])
  (find-docs [db path v]))

(defrecord JsonFileDb
  [json-file data]
  component/Lifecycle
  (start [this]
    (log/info "database starting")
    (let [docs (-> json-file
                   slurp
                   (json/parse-string true))
          index (index-docs docs)]
      (log/info "database done starting")
      (assoc this :data {:docs docs
                         :index index})))
  (stop [this]
    (assoc this :data nil))
  Database
  (count-records [_]
    (count (:docs data)))
  (find-by-id
    [_ id]
    (let [{:keys [docs index]} data]
      (when-let [[first-position :as positions] (seq (get-positions index [:id] id))]
        (when (< 1 (count positions))
          (log/warn "Found more than one document with id" {:id id}))
        (nth docs first-position))))
  (find-docs [_ path v]
    ))

(defn json-file-db
  [json-file]
  (map->JsonFileDb {:json-file json-file}))

