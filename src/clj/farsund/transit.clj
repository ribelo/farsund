(ns farsund.transit
  (:require [cognitect.transit :as t]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [org.joda.time DateTime]))


(def joda-time-writer
  (t/write-handler
    (constantly "m")
    (fn [v] (-> ^DateTime v .getMillis))
    (fn [v] (-> ^DateTime v .getMillis .toString))))


(def joda-time-reader
  (t/read-handler (fn [v] (-> v Long/parseLong dtc/from-long))))


(def write-handlers
  {:handlers {DateTime joda-time-writer}})


(def read-handlers
  {:handlers {"m" joda-time-reader}})


(defn ->json [v]
  (let [out (ByteArrayOutputStream. 4096)]
    (t/write
      (t/writer out :json write-handlers)
      v)
    out))


(defn <-json [v]
  (let [in (ByteArrayInputStream. (.toByteArray v))]
    (t/read
      (t/reader in :json read-handlers))))