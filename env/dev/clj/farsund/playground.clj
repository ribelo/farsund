(ns farsund.playground
  (:require [clojure.java.io :as io]
            [taoensso.encore :as e]
            [net.cgrand.xforms :as x]
            [taoensso.timbre :as timbre]
            [farsund.data.sales :as sales]
            [ribelo.wombat.dataframe :as df]
            [ribelo.wombat.aggregate :as agg]
            [ribelo.wombat.io :as wio]
            [criterium.core :refer [quick-bench]]))

(timbre/merge-config! {:level :info})

(quick-bench
  (sales/read-store-sales "f01451" "2017-01-01" "2019-12-31"
                          "/home/ribelo/s1-dane"))

(def data (sales/read-store-sales "f01451" "2017-01-01" "2019-12-31"
                                  "/home/ribelo/s1-dane"))

(count data)

(->> (sorted-map-by (fn [k1 k2]
                      (println k1 k2)
                      (compare k1 k2))
       1 {:a 5}
       3 {:a 3}
       2 {:a 4}
       5 {:a 1}
       4 {:a 2}))

(quick-bench
  (into []
        (comp (map (fn [{:keys [sales profit] :as m}]
                     (if-not (zero? sales)
                       (assoc m :margin (* 100 (/ profit sales)))
                       m))))
        data))