(ns farsund.data.documents
  (:require [taoensso.encore :as e]
            [cuerdas.core :as str]
            [net.cgrand.xforms :as x]))


(defn document->csv [products]
  (->> products
       (into []
             (comp
               (x/sort-by (fn [[_ {:keys [position]}]] position))
               (map (fn [[_ {:keys [ean qty]}]]
                      (str/join ","
                                ["            "
                                 (str/pad ean {:length 13 :type :left})
                                 (as-> (e/round2 ^double qty) $
                                       (format "%.2f" $)
                                       (str/pad $ {:length 5 :type :right}))
                                 "     \r\n"])))))
       (str/join)))
