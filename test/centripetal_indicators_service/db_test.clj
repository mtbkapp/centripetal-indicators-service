(ns centripetal-indicators-service.db-test
  (:require [centripetal-indicators-service.db :as db]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component]))

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
    (is (= #{[[:a :a] 3 ]
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
 
