(ns farsund.ml
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [taoensso.encore :as e]
            [taoensso.timbre :as timbre]
            [com.rpl.specter :as sp]
            [farsund.config :refer [config]]
            [farsund.data.market :as market]
            [net.cgrand.xforms :as x]
            [clj-time.core :as dt]
            [clj-time.periodic :as dtper]
            [ribelo.visby.math :as math]
            [ribelo.visby.stats :as stats]
            [farsund.weather.core :as weather]
            [farsund.data.sales :as sales])
  (:import (smile.regression OLS$Trainer
                             RidgeRegression$Trainer
                             RandomForest$Trainer)))

(OLS$Trainer.)

;(def df1 (sales/read-store-sales "f01451" "2018-10-01" "2018-11-01"
;                                 "/home/ribelo/s1-dane"))
;(def df2 (sales/read-store-sales "f01450" "2018-01-01" "2018-11-01"
;                                 "/home/ribelo/s3-dane"))
;(def weather-history (weather/read-hisotry-weather "zlotoryja_weather_2017-01-01_2018-11-04.json"))
;(def df df2)


;(def weekdays (drop 1 (range 1 8)))
;(def weekday-ks (mapv #(keyword (str "weekday-" %)) weekdays))



;(def data
;  (into []
;        (comp
;          (filter #(pos? (:qty %)))
;          (map #(select-keys % (concat [:id :name :date :qty :sales :profit :category-id])))
;          (map (fn [{:keys [date] :as m}]
;                 (assoc m :day-sales (get-sales date))))
;          (map (fn [m]
;                 (reduce (fn [{:keys [date] :as acc} day]
;                           (assoc acc (keyword (str "weekday-" day))
;                                      (if (= day (dt/day-of-week date)) 1 0)))
;                         m weekdays))))
;        df))

(count data)
(nth data 1)


(defn train-test-split [p coll]
  (let [c (count coll)
        n (math/floor (* p c))]
    (split-at n coll)))


(let [[train test] (train-test-split 0.8 data)]
  (def train-df train)
  (def test-df test))


(def dmatrix
  (let [data {:x (mapv (fn [m]
                         (let [ks (concat [:day-sales] weekday-ks)]
                           (reduce (fn [acc k] (conj acc (get m k))) [] ks))) train-df)
              :y (mapv :qty train-df)}]
    (xgb/dmatrix data)))


(def xgb-model
  (xgb/fit dmatrix
           {:params         {:max_depth 5
                             :eta       0.0001
                             :silent    1
                             :objective "reg:linear"}
            :watches        {:train dmatrix}
            :rounds         1000
            :early-stopping 10}))


(def xgb-prediction
  (xgb/predict xgb-model
               (xgb/dmatrix
                 {:x (mapv (fn [m]
                             (let [ks (concat [:day-sales] weekday-ks)]
                               (reduce (fn [acc k] (conj acc (get m k))) [] ks))) test-df)})))


(def ols-model
  (let [trainer (OLS$Trainer.)
        x (into-array (mapv (fn [m]
                              (let [ks (concat [:day-sales] weekday-ks)]
                                (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks)))) train-df))
        y (double-array (mapv :qty train-df))]
    (.train trainer x y)))


(def ols-prediction
  (let [x (into-array (mapv (fn [m]
                              (let [ks (concat [:day-sales] weekday-ks)]
                                (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks)))) test-df))]
    (into [] (.predict ols-model x))))


(def ridge-model
  (let [trainer (RidgeRegression$Trainer. 0.001)
        x (into-array (mapv (fn [m]
                              (let [ks (concat [:day-sales] weekday-ks)]
                                (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks)))) train-df))
        y (double-array (mapv :qty train-df))]
    (.train trainer x y)))


(def ridge-prediction
  (let [x (into-array (mapv (fn [m]
                              (let [ks (concat [:day-sales] weekday-ks)]
                                (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks)))) test-df))]
    (into [] (.predict ridge-model x))))


(def random-forest-model
  (let [trainer (RandomForest$Trainer. 1000)
        x (into-array (mapv (fn [m]
                              (let [ks (concat [:day-sales] weekday-ks)]
                                (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks)))) train-df))
        y (double-array (mapv :qty train-df))]
    (.setNumRandomFeatures trainer (e/round0 (/ (inc (count weekday-ks)) 3)))
    (.train trainer x y)
    ))


(def random-forest-prediction
  (let [x (into-array (mapv (fn [m]
                              (let [ks (concat [:day-sales] weekday-ks)]
                                (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks)))) train-df))]
    (into [] (.predict random-forest-model x))))


(let [[rmse] (into [] (stats/rmse (mapv :qty train-df))
                   ;xgb-prediction
                   ;ols-prediction
                   ;ridge-prediction
                   ;random-forest-prediction
                   )]
  rmse)

(->> (mapv (fn [m p]
             (assoc m :pqty p))
           test-df
           ols-prediction)
     (into []
           (comp
             (map (fn [{:keys [qty pqty]}]
                    (math/abs (- pqty qty))))
             stats/mean)))
