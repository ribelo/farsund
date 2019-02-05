(ns farsund.data.stock
  (:require [clojure.java.io :as io]
            [taoensso.encore :as e]
            [taoensso.timbre :as timbre]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc]
            [clj-time.format :as dtf]
            [ribelo.wombat.io :as wio]))


(defn read-file                                             ;TODO
  ([^String market-id date path]
   (timbre/debug :read-product-stock market-id date path)
   (let [date* (if (string? date) (dtf/parse (dtf/formatter "YYYY-MM-dd") date) date)
         epoch (dtc/to-epoch date*)
         file-name (str (.toUpperCase market-id) "_ProductStock_" (dtf/unparse (dtf/formatter "YYYY_MM_dd") date*))
         file (e/path path file-name)]
     (when (.exists (io/as-file file))
       (->> (wio/read-csv file :sep ";" :encoding "cp1250")
            (mapv (fn [[_ pid ean _ _ stock purchase _ sales vat _ category-id]]
                    {:date           epoch
                     :ean            ean
                     :id             pid
                     :price          (e/round2 (/ (Double/parseDouble sales) (Double/parseDouble stock)))
                     :purchase-price (e/round2 (/ (Double/parseDouble purchase) (Double/parseDouble stock)))
                     :stock          (e/round2 (Double/parseDouble stock))}))))))
  ([market-id path]
   (let [date (-> (dt/now) (dt/minus (dt/days 1)))]
     (read-file market-id date path))))
