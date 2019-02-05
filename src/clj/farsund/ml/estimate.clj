(ns farsund.ml.estimate
  (:require [clojure.core.async :as async :refer [go-loop <! >! timeout chan close!]]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [taoensso.encore :as e]
            [taoensso.nippy :as nippy]
            [farsund.config :refer [config]]
            [net.cgrand.xforms :as x]
            [clj-time.core :as dt]
            [clj-time.periodic :as dtper]
            [taoensso.timbre :as timbre]
            [ribelo.visby.math :as math]
            [ribelo.visby.emath :as emath]
            [ribelo.visby.stats :as stats]
            [ribelo.wombat.dataframe :as df]
            [ribelo.wombat.aggregate :as agg]
            [clj-time.periodic :as dtper]
            [clj-time.coerce :as dtc]
            [com.rpl.specter :as sp]
            [farsund.ml.core :as ml]))


(defn model-checker-loop [in-chan {:keys [pub db] :as params}]
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
                    (timbre/info id "send model to ml-estimator")
                    (>! ml-chan [product model aggregated-data]))
                  (catch Exception e
                    (timbre/error :error-create-model id)
                    (timbre/info id "send product to avg-estimator")
                    (>! avg-chan product)))
                (let [model (ml/load-model id)]
                  (timbre/info id "2 send model to ml-estimator")
                  (>! ml-chan [product model aggregated-data]))))
            (do
              (timbre/info id "send product to avg-estimator")
              (>! avg-chan product))))
        (recur)))
    [ml-chan avg-chan]))


(defn ml-estimator-loop [in-chan {:keys [db] :as params}]
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


(defn avg-estimator-loop [in-chan {:keys [pub db] :as params}]
  (timbre/info :init :avg-estimator-loop)
  (go-loop []
    (when-let [{:keys [id optimal-supply] :as product} (<! in-chan)]
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
        (doseq [[id product] data]
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