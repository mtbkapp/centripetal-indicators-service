(ns centripetal-indicators-service.http-test
  (:require [centripetal-indicators-service.http :as http]
            [centripetal-indicators-service.system :as system]
            [cheshire.core :as json]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component]
            [io.pedestal.test :as ped-test]
            [io.pedestal.http :as ped-http]))

(def ^:dynamic *service-fn* nil)

(defn test-system-fixture
  [tests]
  (let [*test-system* (system/-main)]
    (try
      (binding [*service-fn* (-> *test-system*
                                 :http
                                 :server
                                 ::ped-http/service-fn)]
        (tests))
      (finally 
        (component/stop *test-system*)))))

(use-fixtures :once test-system-fixture)

(defn http-get 
  [url]
  (let [resp (ped-test/response-for *service-fn* :get url)]
    (cond-> resp
      (= 200 (:status resp))
      (update :body #(json/parse-string % true)))))

(def expected-doc-keys 
  #{:adversary 
    :author_name 
    :created 
    :description 
    :extract_source 
    :id 
    :indicators 
    :industries 
    :modified 
    :more_indicators 
    :name 
    :public 
    :references
    :revision 
    :tags 
    :targeted_countries 
    :tlp})

(deftest test-find-by-id
  (testing "given id identifies a doc"
    (let [id "5b41a900bd391e01408852d4"
          {:keys [body status headers]} (http-get (str "/indicators/" id))]
      (is (= 200 status))
      (is (= "application/json" (get headers "Content-Type")))
      (is (= id (:id body)))
      (doseq [expected-k expected-doc-keys]
        (is (contains? body expected-k)))))
  (testing "given id does not identify a doc"
    (let [{:keys [status] :as r} (http-get "/indicators/doughnuts")]
      (is (= 404 status)))))

(deftest test-get-all
  (let [{:keys [body status headers]} (http-get "/indicators")]
    (is (= 200 status))
    (is (= "application/json" (get headers "Content-Type")))
    (is (= 100 (count body)))
    (doseq [doc body]
      (is (= expected-doc-keys (set (keys doc)))))))

(deftest test-get-with-query-params
  (let [{:keys [body status headers]} (http-get "/indicators?indicators.type=IP4")]
    
    ))

