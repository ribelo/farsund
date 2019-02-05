(ns farsund.data.documents
  (:require [taoensso.encore :as e]
            [cuerdas.core :as str]))


(defn document->csv [products]
  (->> products
       (map (fn [[_ {:keys [ean qty]}]]
              (str/join ","
                        ["            "
                         (str/pad ean {:length 13 :type :left})
                         (as-> (e/round2 qty) $
                               (format "%.2f" $)
                               (str/pad $ {:length 5 :type :right}))
                         "     \r\n"])))
       (str/join)))