(ns farsund.transit
  (:require [cognitect.transit :as t]
            [java-time :as jt])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.time LocalDateTime]))


(def java-time-writer
  (t/write-handler
    (constantly "m")
    (fn [v] (-> ^LocalDateTime v (jt/to-sql-timestamp) .getTime))
    (fn [v] (-> ^LocalDateTime v (jt/to-sql-timestamp) .getTime .toString))))


(def java-time-reader
  (t/read-handler (fn [v] (-> (Long/parseLong v)
                              (jt/instant)
                              (jt/local-date-time (jt/zone-id))))))


(def write-handlers
  {:handlers {LocalDateTime java-time-writer}})


(def read-handlers
  {:handlers {"m" java-time-reader}})


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
