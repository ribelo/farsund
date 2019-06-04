(ns farsund.data.stock
  (:require
   [clojure.java.io :as io]
   [java-time :as jt]
   [ribelo.wombat.io :as wio]
   [taoensso.encore :as e]
   [taoensso.timbre :as timbre]))

(defn read-file
  ([^String market-id date path]
   (timbre/debug :read-product-stock market-id date path)
   (let [date* (if (string? date) (jt/local-date "yyyy-MM-dd" date) date)
         date-str (jt/format "yyyy_MM_dd" date*)
         file-name (str (.toUpperCase market-id) "_ProductStock_" date-str)
         file (e/path path file-name)]
     (when (.exists (io/as-file file))
       (->> (wio/read-csv file :sep ";" :encoding "cp1250")
            (mapv (fn [[_ pid ean _ _ stock purchase _ sales vat _ category-id]]
                    {:date           date-str
                     :ean            ean
                     :id             pid
                     :price          (e/round2 (/ (Double/parseDouble sales) (Double/parseDouble stock)))
                     :purchase-price (e/round2 (/ (Double/parseDouble purchase) (Double/parseDouble stock)))
                     :stock          (e/round2 (Double/parseDouble stock))}))))))
  ([market-id path]
   (let [date (-> (jt/local-date-time) (jt/minus (jt/days 1)))]
     (read-file market-id date path))))
