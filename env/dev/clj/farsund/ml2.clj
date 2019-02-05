(ns farsund.ml2
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
            [farsund.data.sales :as sales]
            [ribelo.wombat.dataframe :as df]
            [ribelo.wombat.aggregate :as agg])
  (:import (smile.regression OLS
                             OLS$Trainer
                             RidgeRegression$Trainer
                             RandomForest$Trainer
                             GradientTreeBoost$Loss
                             GradientTreeBoost$Trainer GradientTreeBoost RidgeRegression)))



(def df1 (sales/read-store-sales "f01451" "2018-01-01" "2018-12-31"
                                 "/home/ribelo/s1-dane"))
;(def df2 (sales/read-store-sales "f01450" "2018-01-01" "2018-11-01"
;                                 "/home/ribelo/s3-dane"))
;(def weather-history (weather/read-hisotry-weather "zlotoryja_weather_2017-01-01_2018-11-04.json"))
;(def df df2)
(def data df1)



(def days (range 1 31))
(def days-ks (mapv #(keyword (str "day-" %)) days))
(def weekdays (range 1 7))
(def weekdays-ks (mapv #(keyword (str "weekday-" %)) weekdays))
(def months (range 1 12))
(def months-ks (mapv #(keyword (str "month-" %)) months))
(def model-ks
  (concat [
           :promotion
           :mean-qty-3
           ;:median-qty-3
           :mean-qty-7
           ;:median-qty-7
           :mean-qty-14
           ;:median-qty-14
           :mean-qty-30
           ;:median-qty-30
           :price
           :mean-price-3
           ;:median-price-3
           :mean-price-7
           ;:median-price-7
           :mean-price-14
           ;:median-price-14
           :mean-price-30
           ;:median-price-30
           ]
          weekdays-ks))


(defn append-price []
  (map (fn [{:keys [qty sales] :as m}]
         (assoc m :price (if (and qty (pos? qty)) (/ sales qty) 0.0)))))


(def aggregate-data
  (e/memoize*
    (fn [ean store-sales]
      (into []
            (comp
              (df/where :ean #(= ean %))
              (df/where :qty pos?)
              (df/select-columns (concat [:id :date :qty :sales :profit :category-id :promotion]))
              (append-price)
              (df/where :price #(> % 0.01))
              (df/asfreq [:d 1] :fill [:id :category-id :promotion :price])
              (df/replace :qty nil? 0.0)
              (map (fn [{:keys [promotion] :as m}] (assoc m :promotion (if promotion 1 0))))
              (x/by-key :date (x/transjuxt
                                {:id        (comp (map :id) x/last)
                                 :date      (comp (map :date) x/last)
                                 :promotion (comp (map :promotion) x/last)
                                 :qty       (comp (map :qty) (x/reduce +))
                                 :price     (comp (map :price) x/last)}))
              (map second)
              (x/by-key :id (comp
                              (x/sort-by :date)
                              (x/partition 31 1 (x/transjuxt
                                                  {:id              (comp (map :id) x/last)
                                                   :category-id     (comp (map :category-id) x/last)
                                                   :date            (comp (map :date) x/last)
                                                   :promotion       (comp (map :promotion) x/last)
                                                   :qty             (comp (map :qty) x/last)
                                                   :mean-qty-3      (comp (x/drop-last) (x/take-last 3) (map :qty) stats/mean)
                                                   :median-qty-3    (comp (x/drop-last) (x/take-last 3) (map :qty) stats/median)
                                                   :mean-qty-7      (comp (x/drop-last) (x/take-last 7) (map :qty) stats/mean)
                                                   :median-qty-7    (comp (x/drop-last) (x/take-last 7) (map :qty) stats/median)
                                                   :mean-qty-14     (comp (x/drop-last) (x/take-last 14) (map :qty) stats/mean)
                                                   :median-qty-14   (comp (x/drop-last) (x/take-last 14) (map :qty) stats/median)
                                                   :mean-qty-30     (comp (x/drop-last) (x/take-last 30) (map :qty) stats/mean)
                                                   :median-qty-30   (comp (x/drop-last) (x/take-last 30) (map :qty) stats/median)
                                                   :price           (comp (map :price) x/last)
                                                   :mean-price-3    (comp (x/drop-last) (x/take-last 3) (map :price) stats/mean)
                                                   :median-price-3  (comp (x/drop-last) (x/take-last 3) (map :price) stats/median)
                                                   :mean-price-7    (comp (x/drop-last) (x/take-last 7) (map :price) stats/mean)
                                                   :median-price-7  (comp (x/drop-last) (x/take-last 7) (map :price) stats/median)
                                                   :mean-price-14   (comp (x/drop-last) (x/take-last 14) (map :price) stats/mean)
                                                   :median-price-14 (comp (x/drop-last) (x/take-last 14) (map :price) stats/median)
                                                   :mean-price-30   (comp (x/drop-last) (x/take-last 30) (map :price) stats/mean)
                                                   :median-price-30 (comp (x/drop-last) (x/take-last 30) (map :price) stats/median)}))))
              (map second)
              (map (fn [m]
                     (reduce (fn [{:keys [date] :as acc} day]
                               (assoc acc (keyword (str "weekday-" day))
                                          (if (= day (dt/day-of-week date)) 1 0)))
                             m weekdays)))
              (x/sort-by :date))
            store-sales))))


(defn train-test-split
  ([p coll & {:keys [shuffle?]}]
   (let [c (count coll)
         n (math/floor (* p c))]
     (split-at n (if shuffle? (shuffle coll) coll))))
  ([p coll]
   (train-test-split p coll :shuffle? false)))


(defn data->x [data]
  (into-array (mapv (fn [m]
                      (double-array (reduce (fn [acc k] (conj acc (get m k))) [] model-ks))) data)))


(defn data->y [data]
  (double-array (mapv :qty data)))


(defn data->xy [data]
  [(data->x data) (data->y data)])


(defn train-ols-model [train-x train-y]
  (OLS. train-x train-y true))


(defn train-ridge-model [train-x train-y]
  (let [trainer (RidgeRegression$Trainer. 1)]
    (.train trainer train-x train-y)))


(defn train-gbm-model [train-x train-y]
  (let [model (GradientTreeBoost$Trainer. 100)]
    (doto model
      (.setLoss GradientTreeBoost$Loss/LeastSquares)
      (.setMaxNodes 10)
      (.setSamplingRates 0.7)
      (.setShrinkage 0.05)
      )
    (.train model train-x train-y)))


(defn train-random-forest-model [train-x train-y]
  (let [trainer (RandomForest$Trainer. 500)]
    (.setNumRandomFeatures trainer 10)
    ;(.setNodeSize trainer 10)
    ;(.setSamplingRates trainer 0.5)
    (.train trainer train-x train-y)))


(def eans (into []
                (comp
                  (df/group-by :ean {:sales :sum})
                  (x/sort-by :sales #(compare %2 %1))
                  (take 5000)
                  (map :ean))
                data))

model-ks
(def eans ["5904903000677"])
(let [data* (aggregate-data "2900120000000" data)]
  data*)

(first eans)

(def result
  (into {}
        (map-indexed
          (fn [i ean]
            (println i ean (e/round2 (* 100 (/ i (count eans)))))
            (try
              (let [data* (aggregate-data ean data)
                   [train-df test-df] (train-test-split 0.8 data*)
                   [train-x train-y] (data->xy train-df)
                   [test-x test-y] (data->xy test-df)
                   [mean-rmse] (into [] (stats/rmse1 test-y) (map :mean-qty-14 test-df))
                   ;ols-model (train-ols-model train-x train-y)
                   ;ols-prediction (.predict ols-model test-x)
                   ;[ols-rmse] (into [] (stats/rmse1 test-y) ols-prediction)
                   ;ridge-model (train-ridge-model train-x train-y)
                   ;ridge-prediction (.predict ridge-model test-x)
                   ;[ridge-rmse] (into [] (stats/rmse1 test-y) ridge-prediction)
                   random-forest-model (train-random-forest-model train-x train-y)
                   random-forest-prediction (.predict random-forest-model test-x)
                   [random-forest-rmse] (into [] (stats/rmse1 test-y) random-forest-prediction)
                   gbm-model (train-gbm-model train-x train-y)
                   gbm-prediction (.predict gbm-model test-x)
                   [gbm-rmse] (into [] (stats/rmse1 test-y) gbm-prediction)
                   ]
               {ean
                {
                 :mean-rmse   mean-rmse
                 ;:ols-rmse    ols-rmse
                 ;:ridge-rmse  ridge-rmse
                 :forest-rmse random-forest-rmse
                 :gbm-rmse    gbm-rmse
                 }})
              (catch Exception e
                {}))))
        eans))

(map second result)

(into {}
      (comp
        (map second)
        (filter not-empty)
        (x/transjuxt {
                      :mean-rmse   (comp (map :mean-rmse) stats/mean)
                      ;:ols-rmse    (comp (map :ols-rmse) stats/median)
                      :forest-rmse (comp (map :forest-rmse) stats/mean)
                      :gbm-rmse    (comp (map :gbm-rmse) stats/mean)
                      })
        )
      result)


(defn save-model [ean model]
  (let [file-path (str "./models/" ean ".model")]
    (io/make-parents file-path)
    (nippy/freeze-to-file file-path model)))


(defn load-model [ean]
  (nippy/thaw-from-file (str "./models/" ean ".model")))


(defn create-model [id [x y]]
  (let [model (train-ols-model x y)]
    (save-model id model)
    model))






