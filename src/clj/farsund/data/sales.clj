(ns farsund.data.sales
  (:require [clojure.java.io :as io]
            [taoensso.encore :as e]
            [taoensso.timbre :as timbre]
            [clj-time.core :as dt]
            [clj-time.format :as dtf]
            [clj-time.periodic :as dtper]
            [net.cgrand.xforms :as x]
            [cuerdas.core :as str]
            [ribelo.visby.stats :as stats]
            [ribelo.wombat.io :as wio]
            [ribelo.wombat.dataframe :as df]
            [farsund.data.utils :refer :all]))


(defn read-sale-price
  ([market-id date path]
   (timbre/debug :read-sale-price market-id date path)
   (let [date* (if (string? date) (dtf/parse (dtf/formatter "YYYY-MM-dd") date) date)
         date-str (dtf/unparse (dtf/formatter "YYYY_MM_dd") date*)
         file-name (str (str/upper market-id) "_SalePrice_" date-str)
         file-path (e/path path file-name)]
     (if (.exists (io/as-file file-path))
       (->> (wio/read-csv file-path :sep ";" :encoding "cp1250")
            (x/into []
                    (map (fn [[_ _ _ _ id _ _ _ _ qty _ _ _ _ promotion & xs]]
                           {:date      date*
                            :id        id
                            :promotion (not-empty promotion)})))))))
  ([market-id start end path]
   (let [start* (if (string? start) (dtf/parse (dtf/formatter "YYYY-MM-dd") start) start)
         end* (if (string? end) (dtf/parse (dtf/formatter "YYYY-MM-dd") end) end)
         days (dtper/periodic-seq start* end* (dt/days 1))]
     (x/into [] (mapcat #(read-sale-price market-id % path)) days))))


(defn read-store-sales
  ([market-id date path]
   (timbre/debug :read-store-sales market-id date path)
   (let [date* (if (string? date) (dtf/parse (dtf/formatter "YYYY-MM-dd") date) date)
         date-str (dtf/unparse (dtf/formatter "YYYY_MM_dd") date*)
         file-name (str (str/upper market-id) "_StoreSale_" date-str)
         file-path (e/path path file-name)
         ]
     (if (.exists (io/as-file file-path))
       (->> (wio/read-csv file-path :sep ";" :encoding "windows-1250")
            (x/into []
                    (map (fn [[_ date ean _ id name _ _ category qty _ sales _ profit _ _ _ unit vat _ _ weight category-id]]
                           {:date        date*
                            :ean         ean
                            :id          id
                            :category-id category-id
                            :name        name
                            :category    category
                            :qty         (Double/parseDouble qty)
                            :sales       (Double/parseDouble sales)
                            :profit      (Double/parseDouble profit)}))))
       [])))
  ([market-id start end path]
   (let [start* (if (string? start) (dtf/parse (dtf/formatter "YYYY-MM-dd") start) start)
         end* (if (string? end) (dtf/parse (dtf/formatter "YYYY-MM-dd") end) end)
         days (dtper/periodic-seq start* end* (dt/days 1))]
     (x/into [] (mapcat #(read-store-sales market-id % path)) days))))


(defn pace-report [n sales]
  (into {}
        (comp
          (df/select-columns [:id :date :qty])
          (x/by-key :id (df/asfreq [:d 1] :fill [:id]))
          (map second)
          (df/where :qty identity)
          (df/where :qty pos?)
          (df/group-by [:date :id] {:id   :first
                                    :date :first
                                    :qty  :sum})
          (df/group-by :id (comp
                             (x/sort-by :date)
                             (x/take-last n)
                             (map :qty)
                             stats/mean))
          )
        sales))


(defn join-pace-report [pace-report report]
  (into []
        (comp
          (map (fn [{:keys [id optimal-supply] :as m}]
                 (let [pace (get pace-report id 0.0)]
                   (assoc m :pace pace
                            :optimal (* pace optimal-supply)))))
          (x/sort-by identity (fn [x y]
                                (let [order-x (- (:optimal x) (max 0 (:qty x)))
                                      order-y (- (:optimal y) (max 0 (:qty y)))]
                                  (compare order-y order-x)))))
        report))
