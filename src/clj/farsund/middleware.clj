(ns farsund.middleware
  (:require [cognitect.transit :as transit]
            [cheshire.generate :as cheshire]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [muuntaja.core :as m]
            [muuntaja.format.transit :as transit-format]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [immutant.web.middleware :refer [wrap-session]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [taoensso.timbre :as timbre])
  (:import (com.fasterxml.jackson.datatype.joda JodaModule)
           (org.joda.time ReadableInstant DateTime)))


(def joda-time-writer
  (transit/write-handler
    (constantly "m")
    (fn [v] (-> ^ReadableInstant v .getMillis))
    (fn [v] (-> ^ReadableInstant v .getMillis .toString))))


(def instance
  (m/create
    (-> m/default-options
        (assoc-in
          [:formats "application/json" :opts :modules]
          [(JodaModule.)])
        (assoc-in
          [:formats "application/transit+json" :encode-opts]
          {:handlers {DateTime joda-time-writer}}))))



;(cheshire/add-encoder
;  DateTime
;  (fn [c jsonGenerator]
;    (.writeString jsonGenerator (-> ^ReadableInstant c .getMillis .toString))))


(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (timbre/error t (.getMessage t))
        "Something very bad has happened!"))))


(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response "Invalid anti-forgery token"}))


(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format instance))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))


(defn wrap-base [handler]
  (-> handler
      (wrap-session {:cookie-attrs {:http-only true}})
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (dissoc :session)))
      wrap-internal-error))