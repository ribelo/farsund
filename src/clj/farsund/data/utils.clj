(ns farsund.data.utils
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [com.rpl.specter :as sp]
            [taoensso.encore :as e]
            [clj-time.core :as dt]
            [clj-time.periodic :as dtper]
            [net.cgrand.xforms :as x])
  (:import (clojure.lang Keyword PersistentVector)))


(defn map->rows
  ([columns data]
   (let [headers (map name columns)
         rows (mapv #(mapv % columns) data)]
     (cons headers rows)))
  ([data]
   (map->rows (keys (first data)) data)))


(defn read-csv-data
  [path & {:keys [separator quote encoding]
           :or   {separator \;
                  quote     \~}}]
  (csv/read-csv (io/reader path :encoding (or encoding "UTF-8")) :separator separator :quote quote))


(defn write-csv-data
  ([path ks data]
   (with-open [out-file (io/writer path)]
     (csv/write-csv out-file (map->rows (if (seq ks) ks (keys (first data))) data)
                    :separator \;
                    :encoding "utf8")))
  ([path data]
   (with-open [out-file (io/writer path)]
     (csv/write-csv out-file (map->rows (keys (first data)) data)
                    :separator \;
                    :encoding "utf8"))))


(defn in-stock? [id coll]
  (->> coll
       (filter #(= id (:id %)))
       (first)
       (boolean)))


(defn product-by-ean [ean coll]
  (sp/select-one [sp/ALL #(= ean (:ean %))] coll))


(defn- empty-map [ks]
  (reduce (fn [acc k] (assoc acc k nil)) {} ks))