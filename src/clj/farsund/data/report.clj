(ns farsund.data.report
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [cuerdas.core :as str]))


(defn read-file [path]
  (->> (xls/load-workbook-from-file path)
       (xls/select-sheet "Arkusz 1")
       (xls/select-columns {:A :category-id
                            :B :name
                            :C :id
                            :D :ean
                            :E :pace
                            :F :optimal
                            :G :price
                            :H :established-margin
                            :I :min-supply
                            :J :optimal-supply
                            :K :max-supply
                            :L :last-sales
                            :M :last-delivery
                            :N :volume
                            :O :sales
                            :P :profit
                            :Q :buy-price
                            :R :stock
                            :S :promotion})
       (rest)
       (into {}
             (comp
               (filter #(identity (:id %)))
               (filter #(identity (:ean %)))
               (map (fn [{:keys [^double sales ^double profit
                                 ^double volume ^String ean] :as m}]
                      {ean (-> m
                               (update :name str/human)
                               (update :promotion str/trim)
                               (update :optimal #(if (string? %) 0 %))
                               (assoc :margin (if (pos? sales)
                                                (/ profit sales)
                                                0.0))
                               (update :established-margin #(/ % 100))
                               (assoc :profit (/ profit volume))
                               (assoc :sales (/ sales volume)))}))))))
