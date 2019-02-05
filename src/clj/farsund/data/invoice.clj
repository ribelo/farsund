(ns farsund.data.invoice
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [<!! timeout]]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc]
            [cuerdas.core :as str]
            [hawk.core :as hawk]
            [ribelo.wombat.io :as wio]
            [farsund.data.utils :refer :all]
            ;[farsund.firestore.core :as fire]
            ))


(defn read-file [path]
  (let [file (io/as-file path)]
    (when (.exists file)
      (let [id (second (str/split (.getName file) #"_|\."))
            modified (-> file (.lastModified) (dtc/from-long))
            products (->> (wio/read-csv file :sep ";" :encoding "cp1250")
                          (mapv (fn [[^String ean ^String qty]]
                                  {ean {:ean ean
                                        :qty (Float/parseFloat qty)}})))]
        {:id       id
         :name     (.getName file)
         :time     modified
         :products products}))))


;(defn invoice->path [market-id doc-id]
;  (fire/path->field [(str (str/lower market-id) "-invoices") doc-id]))
;
;
;(defn stored-in-firestore? [firestore market-id doc-id]
;  (fire/document-exists? firestore (invoice->path market-id doc-id)))
;
;
;(defn delete-from-firestore [firestore market-id doc-id]
;  (fire/delete-document firestore (invoice->path market-id doc-id)))
;
;
;(defn send-to-firestore [firestore market-id {:keys [id] :as invoice}]
;  (fire/set-document firestore (invoice->path market-id id) invoice))

