(ns centripetal-indicators-service.core
  (:require [cheshire.core :as json]))

(comment

  (def data (json/parse-string (slurp (clojure.java.io/resource "indicators.json")) true))

  (count data)
  (let [[ks0 & kss] (map keys data)]
    (every? #(= ks0 %) kss))

  ; 100 docs
  ; all docs have the same keys

  (def indicators
    (into []
          (mapcat :indicators)
          data))

  (count indicators)
  ; 18077 indicators in total

  (prn (sort (keys (first data))))

  '(:adversary
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
    :tlp)

  (every? false? (map :more_indicators data))
  ; there are never "more_indicators"

  (System/getProperty "java.version")

  
  )
