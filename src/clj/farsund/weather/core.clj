(ns farsund.weather.core
  (:require [taoensso.encore :as e]
            [com.rpl.specter :as sp]
            [cheshire.core :as json]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc]
    ;[clj-http.client :as http]
            [farsund.data.utils :refer :all]
            ))


;(defn read-hisotry-weather [path]
;  (->> (json/parse-string (slurp "zlotoryja_weather_2017-01-01_2018-11-04.json"))
;       (into [] (comp (map (fn [{{temp "temp"} "main"
;                                 dt            "dt"}]
;                             {:temp (- temp 273.15)
;                              :date (dtc/from-long (* dt 1000))}))))))


;(def date->weather
;  (e/memoize_
;    (fn [date weather]
;      (let [date' (dt/with-time-at-start-of-day date)]
;        (->> weather (sp/select-one [sp/ALL #(dt/equal? date' (:date %))]))))))


;(defn forecast [{:keys [lat lon api-key] :as params}]
;  (let [url "http://api.apixu.com/v1/forecast.json"
;        data (-> (http/get url
;                           {:query-params {:key  api-key
;                                           :q    (str lat "," lon)
;                                           :days 1}})
;                 :body
;                 (json/parse-string keyword)
;                 :forecast
;                 :forecastday
;                 (first))
;        {dt                :1541721600
;         {temp :avgtemp_c} :day} data]
;    {:temp temp
;     :date (dtc/from-long dt)}))


