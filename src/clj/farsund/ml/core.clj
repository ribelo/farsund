(ns farsund.ml.core
  (:require [clojure.java.io :as io]
            [taoensso.encore :as e]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre]
            [net.cgrand.xforms :as x]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc]
            [clj-time.periodic :as dtper]
            [ribelo.visby.math :as math]
            [ribelo.visby.emath :as emath]
            [ribelo.visby.stats :as stats]
            [ribelo.wombat.dataframe :as df]
            [ribelo.wombat.aggregate :as agg])
  (:import (smile.regression RandomForest$Trainer
                             GradientTreeBoost$Loss
                             GradientTreeBoost$Trainer GradientTreeBoost RidgeRegression)))


(defn train-test-split
  ([p coll & {:keys [shuffle?]}]
   (let [c (count coll)
         n (math/floor (* p c))]
     (split-at n (if shuffle? (shuffle coll) coll))))
  ([p coll]
   (train-test-split p coll {})))


(def weekdays (drop-last 1 (range 1 8)))
(def weekday-ks (mapv #(keyword (str "weekday-" %)) weekdays))

(def model-ks
  (concat [:promotion
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
          weekday-ks))


(defn append-price []
  (map (fn [{:keys [qty sales] :as m}]
         (assoc m :price (if (and qty (pos? qty)) (/ sales qty) 0.0)))))


(defn enough-data? [id store-sales & {:keys [min-count]
                                      :or   {min-count 61}}]
  (let [data (into []
                   (comp (df/where :id #(= id %))
                         (df/where :qty pos?)
                         (append-price)
                         (df/where :price #(> % 0.01))
                         (df/asfreq [:d 1] :fill [:id :category-id :promotion :price])
                         (map :date)
                         (distinct))
                   store-sales)]
    (>= (count data) min-count)))


(defn aggregate-data [id store-sales]
  (into []
        (comp
          (df/where :id #(= id %))
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
                                              {:id            (comp (map :id) x/last)
                                               :category-id   (comp (map :category-id) x/last)
                                               :date          (comp (map :date) x/last)
                                               :promotion     (comp (map :promotion) x/last)
                                               :qty           (comp (map :qty) x/last)
                                               :mean-qty-3    (comp (x/drop-last) (x/take-last 3) (map :qty) stats/mean)
                                               ;:median-qty-3    (comp (x/drop-last) (x/take-last 3) (map :qty) stats/median)
                                               :mean-qty-7    (comp (x/drop-last) (x/take-last 7) (map :qty) stats/mean)
                                               ;:median-qty-7    (comp (x/drop-last) (x/take-last 7) (map :qty) stats/median)
                                               :mean-qty-14   (comp (x/drop-last) (x/take-last 14) (map :qty) stats/mean)
                                               ;:median-qty-14   (comp (x/drop-last) (x/take-last 14) (map :qty) stats/median)
                                               :mean-qty-30   (comp (x/drop-last) (x/take-last 30) (map :qty) stats/mean)
                                               ;:median-qty-30   (comp (x/drop-last) (x/take-last 30) (map :qty) stats/median)
                                               :price         (comp (map :price) x/last)
                                               :mean-price-3  (comp (x/drop-last) (x/take-last 3) (map :price) stats/mean)
                                               ;:median-price-3  (comp (x/drop-last) (x/take-last 3) (map :price) stats/median)
                                               :mean-price-7  (comp (x/drop-last) (x/take-last 7) (map :price) stats/mean)
                                               ;:median-price-7  (comp (x/drop-last) (x/take-last 7) (map :price) stats/median)
                                               :mean-price-14 (comp (x/drop-last) (x/take-last 14) (map :price) stats/mean)
                                               ;:median-price-14 (comp (x/drop-last) (x/take-last 14) (map :price) stats/median)
                                               :mean-price-30 (comp (x/drop-last) (x/take-last 30) (map :price) stats/mean)
                                               ;:median-price-30 (comp (x/drop-last) (x/take-last 30) (map :price) stats/median)
                                               }))))
          (map second)
          (map (fn [m]
                 (reduce (fn [{:keys [date] :as acc} day]
                           (assoc acc (keyword (str "weekday-" day))
                                      (if (= day (dt/day-of-week date)) 1 0)))
                         m weekdays)))
          (x/sort-by :date))
        store-sales))


(defn average-sales [id optimal-supply store-sales]
  (->> store-sales
       (into []
             (comp (df/where :id #(= id %))
                   (df/where :qty pos?)
                   (append-price)
                   (df/where :price #(> % 0.01))
                   (df/asfreq [:d 1] :fill [:id])
                   (x/by-key :date (x/transjuxt
                                     {:id   (comp (map :id) x/last)
                                      :date (comp (map :date) x/last)
                                      :qty  (comp (map :qty) (x/reduce +))}))
                   (map second)
                   (x/sort-by :date)
                   (x/take-last optimal-supply)
                   (map :qty)
                   stats/mean))
       (first)))


(defn data->x [ks data]
  (into-array (mapv (fn [m]
                      (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks))) data)))


(defn data->y [k data]
  (double-array (mapv #(get % k) data)))


(defn data->xy [ks-x k-y data]
  [(data->x ks-x data) (data->y k-y data)])


(defn save-model [id model]
  (let [file-path (str "./ml_models/" id ".model")]
    (io/make-parents file-path)
    (nippy/freeze-to-file file-path model)))


(defn load-model [id]
  (nippy/thaw-from-file (str "./ml_models/" id ".model")))


(defn create-random-forest-model [id [x y]]
  (timbre/info :create-model id)
  (let [trainer (RandomForest$Trainer. 500)]
    (doto trainer
      (.setNumRandomFeatures 10))
    (let [model (.train trainer x y)]
      (save-model id model)
      model)))


(defn create-gbm-model [id [x y] & {:keys [max-nodes sampling-rates shrinkage]
                                    :or   {max-nodes      5
                                           sampling-rates 0.7
                                           shrinkage      0.05}}]
  (timbre/info :create-model id)
  (let [trainer (GradientTreeBoost$Trainer. 100)]
    (doto trainer
      (.setLoss GradientTreeBoost$Loss/Huber)
      (.setMaxNodes max-nodes)
      (.setSamplingRates sampling-rates)
      (.setShrinkage shrinkage))
    (let [model (.train trainer x y)]
      (save-model id model)
      model)))


(defn data->last-x [ks-x days data]
  (let [days-seq (dtper/periodic-seq (dt/plus (dt/now) (dt/days 1))
                                     (dt/plus (dt/now) (dt/days days)) (dt/days 1))
        m (reduce (fn [acc k] (assoc acc k 0)) (last data) weekday-ks)]
    (->> (mapv (fn [d]
                 (let [weekday (dt/day-of-week d)]
                   (if (not= 7 weekday)
                     (assoc m (keyword (str "weekday-" weekday)) 1)
                     m))) days-seq)
         (mapv (fn [m]
                 (double-array (reduce (fn [acc k] (conj acc (get m k))) [] ks-x))))
         (into-array))))


(defn raw-model-prediction [model ks-x days df]
  (.predict model (data->last-x ks-x days df)))


(defn predict-sales [model ks-x days df]
  (reduce + (.predict model (data->last-x ks-x days df))))


(defn model-needs-refresh? [id]
  (let [model (or (io/as-file (str "./ml_models/" id ".model"))
                  (io/as-file (str "./ml_models/" id ".error")))]
    (if (.exists model)
      (let [modified (-> model (.lastModified) (dtc/from-long))]
        (dt/before? (dt/plus modified (dt/days 7)) (dt/now)))
      true)))