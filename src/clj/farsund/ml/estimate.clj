(ns farsund.ml.estimate
  (:require [clojure.core.async :as async :refer [go-loop <! >! timeout chan close!]]
            [integrant.core :as ig]
            [taoensso.encore :as e]
            [taoensso.timbre :as timbre]
            [com.rpl.specter :as sp]
            [farsund.ml.core :as ml]))

(defn model-checker-loop [in-chan {:keys [db]}]
  (timbre/info :init :model-checker-loop)
  (let [ml-chan (async/chan)
        avg-chan (async/chan)]
    (go-loop []
      (when-let [{:keys [id] :as product} (<! in-chan)]
        (timbre/info :model-checker-loop id)
        (let [store-sales (:store-sales @db)]
          (if (ml/enough-data? id store-sales)
            (let [aggregated-data (ml/aggregate-data id store-sales)]
              (if (ml/model-needs-refresh? id)
                (try
                  (let [xy (ml/data->xy ml/model-ks :qty aggregated-data)
                        model (ml/create-random-forest-model id xy)]
                    (ml/save-model id model)
                    (>! ml-chan [product model aggregated-data]))
                  (catch Exception e
                    (timbre/error :error-create-model id)
                    (ml/save-model id nil)
                    (>! avg-chan product)))
                (if-let [model (ml/load-model id)]
                  (>! ml-chan [product model aggregated-data])
                  (>! avg-chan product))))
            (do
              (timbre/info id "send product to avg-estimator")
              (>! avg-chan product))))
        (recur)))
    [ml-chan avg-chan]))

(defn ml-estimator-loop [in-chan {:keys [db]}]
  (timbre/info :init :ml-estimator-loop)
  (go-loop []
    (when-let [[{:keys [id optimal-supply]} model data] (<! in-chan)]
      (timbre/info :ml-estimator-loop id)
      (try
        (let [optimal (e/round2 (ml/predict-sales model ml/model-ks optimal-supply data))]
          (timbre/info :ml-estimator-loop id optimal)
          (sp/setval [sp/ATOM :market-report sp/ALL #(= id (:id %)) :optimal] optimal db))
        (catch Exception e (timbre/error e)))
      (recur))))

(defn avg-estimator-loop [in-chan {:keys [db]}]
  (timbre/info :init :avg-estimator-loop)
  (go-loop []
    (when-let [{:keys [id optimal-supply]} (<! in-chan)]
      (timbre/info :avg-estimator-loop id)
      (try
        (when-let [optimal (try (e/round2 (ml/average-sales id optimal-supply (:store-sales @db)))
                                (catch Exception e nil))]
          (timbre/info :avg-estimator-loop id optimal)
          (sp/setval [sp/ATOM :market-report sp/ALL #(= id (:id %)) :optimal] optimal db))
        (catch Exception e (timbre/error e)))
      (recur))))

(defmethod ig/init-key :farsund/estimator [_ {:keys [pub] :as params}]
  (timbre/info :ig/init-key :farsund/estimator)
  (let [topic-chan (async/chan)
        products-chan (async/chan)
        [ml-chan avg-chan] (model-checker-loop products-chan params)
        ml-estimator (ml-estimator-loop ml-chan params)
        avg-estimator (avg-estimator-loop avg-chan params)]
    (async/sub pub :market-report/changed topic-chan)
    (go-loop []
      (when-let [{:keys [data]} (<! topic-chan)]
        (doseq [[_ product] data]
          (timbre/info :farsund/estimator (:id product))
          (>! products-chan product))
        (recur)))
    {:products-chan products-chan
     :ml-chan       ml-chan
     :ml-estimator  ml-estimator
     :avg-chan      avg-chan
     :avg-estimator avg-estimator}))

(defmethod ig/halt-key! :farsund/estimator [_ {:keys [products-chan ml-chan ml-estimator avg-chan avg-estimator]}]
  (close! products-chan)
  (close! ml-chan)
  (close! ml-estimator)
  (close! avg-chan)
  (close! avg-estimator))
