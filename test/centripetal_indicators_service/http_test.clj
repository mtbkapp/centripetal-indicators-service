(ns centripetal-indicators-service.http-test
  (:require [centripetal-indicators-service.system :as system]
            [cheshire.core :as json]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [com.stuartsierra.component :as component]
            [io.pedestal.http :as ped-http]
            [io.pedestal.test :as ped-test]))

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

(defn decode-body
  [{:keys [status] :as response}]
  (cond-> response
    (#{400 200} status)
    (update :body #(json/parse-string % true))))

(defn http-get
  [url]
  (-> (ped-test/response-for *service-fn* :get url)
      decode-body))

(defn http-post
  [url body headers]
  (-> (ped-test/response-for *service-fn*
                             :post url
                             :body body
                             :headers headers)
      decode-body))

(defn http-post-json
  [url body]
  (http-post url
             (json/generate-string body)
             {"Content-Type" "application/json"}))

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
    (let [{:keys [status]} (http-get "/indicators/doughnuts")]
      (is (= 404 status)))))

(deftest test-get-all
  (let [{:keys [body status headers]} (http-get "/indicators")]
    (is (= 200 status))
    (is (= "application/json" (get headers "Content-Type")))
    (is (= 100 (count body)))
    (doseq [doc body]
      (is (= expected-doc-keys (set (keys doc)))))))

(deftest test-find-with-params
  (testing "single params"
    (let [{:keys [body status headers]} (http-get "/indicators?indicators.type=IPv4")]
      (is (= 200 status))
      (is (= "application/json" (get headers "Content-Type")))
      (is (= 69 (count body)))
      (is (every? #(contains? % "IPv4")
                  (map #(into #{}
                              (map :type)
                              (:indicators %))
                       body)))
      (doseq [doc body]
        (is (= expected-doc-keys (set (keys doc)))))))
  (testing "multi param"
    (let [{:keys [body status headers]} (http-get "/indicators?indicators.type=IPv4&tlp=green")]
      (is (= 200 status))
      (is (= "application/json" (get headers "Content-Type")))
      (is (= 65 (count body)))
      (is (every? #(contains? % "IPv4")
                  (map #(into #{}
                              (map :type)
                              (:indicators %))
                       body)))
      (is (= #{"green"} (into #{} (map :tlp) body)))
      (doseq [doc body]
        (is (= expected-doc-keys (set (keys doc))))))))

(deftest test-search
  (testing "valid query"
    (let [{:keys [status body headers]} (http-post-json "/indicators/search"
                                                        ["or"
                                                         ["=" "tlp" "green"]
                                                         ["=" "author_name" "AlienVault"]])]
      (is (= 200 status))
      (is (= 93 (count body)))
      (is (= "application/json" (get headers "Content-Type")))
      (doseq [doc body]
        (is (or (= "green" (:tlp doc))
                (= "AlienVault" (:author_name doc))))
        (is (= expected-doc-keys (set (keys doc)))))))
  (testing "invalid query"
    (let [{:keys [status body]} (http-post-json "/indicators/search"
                                                ["dounts"])]
      (is (= 400 status))
      (is (= {:errors ["Invalid query input"]} body))))
  (testing "invalid Content-Type"
    (testing "no content type"
      (let [{:keys [status body]} (http-post "/indicators/search"
                                             (json/generate-string ["=" "tlp" "green"])
                                             {} ; no content-type specified
                                             )]
        (is (= 400 status))
        (is (= {:errors ["Unsupported Content-Type. text/json and application/json are supported"]}
               body))))
    (testing "default curl content type"
      (let [{:keys [status body]} (http-post "/indicators/search"
                                             (json/generate-string ["=" "tlp" "green"])
                                             {"Content-Type" "application/x-www-form-urlencoded"})]
        (is (= 400 status))
        (is (= {:errors ["Unsupported Content-Type. text/json and application/json are supported"]}
               body)))))
  (testing "invalid JSON, missing commas"
    (let [{:keys [status body]} (http-post "/indicators/search"
                                           "[\"=\" \"tlp\" \"green\"]"
                                           {"Content-Type" "text/json"})]
      (is (= 400 status))
      (is (= {:errors ["Invalid JSON"]} body)))))

