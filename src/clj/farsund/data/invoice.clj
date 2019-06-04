(ns farsund.data.invoice
  (:require [clojure.java.io :as io]
            [java-time :as jt]
            [cuerdas.core :as str]
            [ribelo.wombat.io :as wio]))

(defn read-file [path]
  (let [file (io/as-file path)]
    (when (.exists file)
      (let [id (second (str/split (.getName file) #"_|\."))
            modified (-> file (.lastModified)
                         (jt/instant)
                         (jt/local-date-time (jt/zone-id)))
            products (->> (wio/read-csv file :sep ";" :encoding "cp1250")
                          (into {}
                                (map-indexed (fn [i [^String ean ^String qty]]
                                               {ean {:ean      ean
                                                     :qty      (Float/parseFloat qty)
                                                     :position i}}))))]
        {:id       id
         :name     (.getName file)
         :time     modified
         :products products}))))
