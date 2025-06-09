(ns centripetal-indicators-service.db-test
  (:require [centripetal-indicators-service.db :as db]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component])
  (:import [clojure.lang ExceptionInfo]))

(set! *warn-on-reflection* true)

(def ^:dynamic *db* nil)

(defn db-component-fixture
  [tests]
  (binding [*db* (-> (db/json-file-db (io/resource "indicators.json"))
                     component/start)]
    (try
      (tests)
      (finally
        (component/stop *db*)))))

(use-fixtures :once db-component-fixture)

(deftest test-index-paths-from-json-value
  (is (= [] (db/index-paths-from-json-value {})))
  (testing "atomic json values"
    (is (= #{[[:a] 1]
             [[:b] "donuts"]
             [[:c] true]
             [[:d] nil]}
           (set (db/index-paths-from-json-value {:a 1
                                                 :b "donuts"
                                                 :c true
                                                 :d nil})))))
  (testing "maps"
    (is (= #{[[:a] 1]
             [[:b :c] 2]
             [[:b :d] "donuts"]}
           (set (db/index-paths-from-json-value {:a 1
                                                 :b {:c 2
                                                     :d "donuts"}}))))
    (is (= #{[[:a] 1]
             [[:b :c] 2]
             [[:b :d :food] "donuts"]}
           (set (db/index-paths-from-json-value {:a 1
                                                 :b {:c 2
                                                     :d {:food "donuts"}}})))))
  (testing "arrays"
    (is (= #{[[:a] 1]
             [[:a] nil]
             [[:a] false]
             [[:a] "donuts"]}
           (set (db/index-paths-from-json-value {:a [1 nil false "donuts"]}))))
    (is (= #{[[:a :b] 1]
             [[:a :b] 2]}
           (set (db/index-paths-from-json-value {:a {:b [1 2]}}))))
    (is (= #{[[:a :a] 3]
             [[:a :b] 4]}
           (set (db/index-paths-from-json-value {:a [{:a 3} {:b 4}]}))))
    (is (= #{[[:a :b :c] 1]
             [[:a :b :c] 2]
             [[:a :b :c :d :e] 55]}
           (set (db/index-paths-from-json-value {:a {:b [{:c [1 2 {:d [{:e 55}]}]}]}}))))))

(deftest test-find-by-id
  (testing "given id identifies a doc"
    (let [id "5b41a900bd391e01408852d4"
          doc (db/find-by-id *db* id)]
      (is (= id (:id doc)))))
  (testing "given id does not identify a doc"
    (is (nil? (db/find-by-id *db* "doughnuts")))))

(deftest test-parse-query
  (testing "throws on invalid query"
    (is (thrown? ExceptionInfo (db/parse-query "donuts")))
    (try
      (db/parse-query "donuts")
      (is false)
      (catch Exception ex
        (is (db/invalid-query-exception? ex)))))
  (testing "single field equality"
    (let [query ["=" "indicators.type" "IPv4"]]
      (is (= [:equals {:op-token "="
                       :path [:indicators :type]
                       :value [:string "IPv4"]}]
             (db/parse-query query)))))
  (testing "not operator"
    (let [query ["not" ["=" "indicators.type" "IPv4"]]]
      (is (= [:not {:op-token "not"
                    :expr [:equals {:op-token "="
                                    :path [:indicators :type]
                                    :value [:string "IPv4"]}]}]
             (db/parse-query query)))))
  (testing "and operator"
    (let [query ["and"
                 ["=" "indicators.type" "IPv4"]
                 ["=" "tlp" "green"]]]
      (is (= [:and {:op-token "and"
                    :operands [[:equals {:op-token "="
                                         :path [:indicators :type]
                                         :value [:string "IPv4"]}]
                               [:equals {:op-token "="
                                         :path [:tlp]
                                         :value [:string "green"]}]]}]
             (db/parse-query query)))))
  (testing "or operator"
    (let [query ["or"
                 ["=" "indicators.type" "IPv4"]
                 ["=" "tlp" "green"]]]
      (is (= [:or {:op-token "or"
                   :operands [[:equals {:op-token "="
                                        :path [:indicators :type]
                                        :value [:string "IPv4"]}]
                              [:equals {:op-token "="
                                        :path [:tlp]
                                        :value [:string "green"]}]]}]
             (db/parse-query query))))))

(deftest test-exec-query
  (let [db-data (-> *db* :data)]
    (testing "single equality"
      (let [query ["=" "tlp" "green"]
            results (db/exec-query db-data query)]
        (is (= 83 (count results)))
        (is (= #{"green"} (into #{} (map :tlp) results))))
      (let [query ["=" "indicators.type" "IPv4"]
            results (db/exec-query db-data query)
            types (map #(into #{}
                              (map :type)
                              (:indicators %))
                       results)]
        (is (= 69 (count results)))
        (is (every? #(contains? % "IPv4") types))))
    (testing "not"
      (let [query ["not" ["=" "tlp" "green"]]
            results (db/exec-query db-data query)
            tlps (into #{} (map :tlp) results)]
        (is (= 17 (count results)))
        (is (not (contains? tlps "green")))))
    (testing "and"
      (let [query ["and"
                   ["=" "tlp" "green"]
                   ["=" "indicators.type" "IPv4"]]
            results (db/exec-query db-data query)
            tlps (into #{} (map :tlp) results)
            types (map #(into #{}
                              (map :type)
                              (:indicators %))
                       results)]
        (is (= 65 (count results)))
        (is (= #{"green"} tlps))
        (is (every? #(contains? % "IPv4") types))))
    (testing "or"
      (let [query ["or"
                   ["=" "tlp" "green"]
                   ["=" "indicators.type" "IPv4"]]
            results (db/exec-query db-data query)
            slimed-results (map (fn [{:keys [tlp indicators]}]
                                  {:tlp tlp
                                   :types (into #{} (map :type) indicators)})
                                results)]
        (is (= 87 (count results)))
        (is (every? (fn [{:keys [tlp types]}]
                      (or (= tlp "green")
                          (contains? types "IPv4")))
                    slimed-results))))
    (testing "valid query with path that doesn't exist"
      (is (empty? (db/exec-query db-data ["=" "food.donuts.type" "raised"]))))))

(deftest test-find-docs
  (let [query ["=" "tlp" "green"]
        results (db/find-docs *db* query)]
    (is (= 83 (count results)))
    (is (= #{"green"} (into #{} (map :tlp) results)))))

