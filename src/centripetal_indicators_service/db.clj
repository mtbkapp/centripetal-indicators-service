(ns centripetal-indicators-service.db
  (:require [cheshire.core :as json]
            [clojure.set :as sets]
            [clojure.spec.alpha :as spec]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

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
  (get-in index (concat path [::values v])))

; simple matching language

(spec/def ::query.equals
  (spec/cat :op-token #(= "=" %)
            :path ::query.path
            :value ::query.value))

(spec/def ::query.path
  (spec/and string?
            (spec/conformer
             (fn [path]
               (map keyword (string/split path #"\."))))))

(spec/def ::query.value
  (spec/or :bool boolean?
           :number number?
           :string string?))

(spec/def ::query.boolean-expr
  (spec/or :equals ::query.equals
           :or ::query.or
           :and ::query.and
           :not ::query.not))

(spec/def ::query.not
  (spec/cat :op-token #(= "not" %)
            :expr ::query.boolean-expr))

(spec/def ::query.or
  (spec/cat :op-token #(= "or" %)
            :operands (spec/+ ::query.boolean-expr)))

(spec/def ::query.and
  (spec/cat :op-token #(= "and" %)
            :operands (spec/+ ::query.boolean-expr)))

(spec/def ::query ::query.boolean-expr)

(defn parse-query
  [query]
  (let [parsed (spec/conform ::query query)]
    (if (= ::spec/invalid parsed)
      (throw (ex-info "invalid query"
                      {:type "invalid-query"
                       :explain-data (spec/explain-data ::query query)}))
      parsed)))

(defn invalid-query-exception?
  [ex]
  (and (instance? ExceptionInfo ex)
       (= "invalid-query" (:type (ex-data ex)))))

(def exec-query* nil)
(defmulti exec-query*
  (fn [_db-data [op :as _parsed-query]]
    op))

(defmethod exec-query* :equals
  [{:keys [index]} [_ {path :path [_ v] :value}]]
  (get-positions index path v))

(defmethod exec-query* :not
  [{:keys [docs] :as db-data} [_ {:keys [expr]}]]
  (let [positions (exec-query* db-data expr)]
    (sets/difference (set (range (count docs))) positions)))

(defmethod exec-query* :and
  [db-data [_ {:keys [operands]}]]
  (->> (map #(exec-query* db-data %) operands)
       (apply sets/intersection)))

(defmethod exec-query* :or
  [db-data [_ {:keys [operands]}]]
  (->> (map #(exec-query* db-data %) operands)
       (apply sets/union)))

(defn exec-query
  [{:keys [docs] :as db-data} query]
  (let [positions (exec-query* db-data (parse-query query))]
    (map #(nth docs %) positions)))

(defprotocol Database
  (get-all [db])
  (find-by-id [db id])
  (find-docs [db query]))

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
  (get-all
    [_]
    (:docs data))
  (find-by-id
    [_ id]
    (first (exec-query data ["=" "id" id])))
  (find-docs [_ query]
    (exec-query data query)))

(defn json-file-db
  [json-file]
  (map->JsonFileDb {:json-file json-file}))

