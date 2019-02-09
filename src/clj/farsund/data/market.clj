(ns farsund.data.market
  (:require [clojure.java.io :as io]
            [clojure.core.async :as async :refer [timeout <!! >! >!!]]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc]
            [com.rpl.specter :as sp]
            [hawk.core :as hawk]
            [cuerdas.core :as str]
            [farsund.data.sales :as sales]
            [farsund.data.report :as report]
            [farsund.data.invoice :as invoice]))


(defn read-market-data [{:keys [market-id report-path data-path days-to-read]}]
  (let [store-sales (future (sales/read-store-sales market-id
                                                    (dt/minus (dt/today-at-midnight) (dt/days days-to-read))
                                                    (dt/today-at-midnight)
                                                    data-path))
        sale-price (future (sales/read-sale-price market-id
                                                  (dt/minus (dt/today-at-midnight) (dt/days days-to-read))
                                                  (dt/today-at-midnight)
                                                  data-path))
        ;stock-data (future (stock/read-file market-id data-path))
        market-report (future (report/read-file report-path))]
    {:store-sales         (clojure.set/join @store-sales @sale-price)
     :market-report/by-id @market-report}))


(defn- report-handler
  [{:keys [db report-path chan] :as params}]
  (fn [_ {:keys [kind] :as e}]
    (when (not= kind :delete)
      (<!! (timeout 3000))
      (println :report-handler)
      (let [report (future (report/read-file report-path))]
        (async/put! chan {:event :market-report/changed
                          :data  @report})
        (swap! db assoc :market-report/by-id @report)))))


(defmethod ig/init-key :farsund/market-watcher
  [_ {:keys [db report-path chan] :as params}]
  (timbre/info :ig/init-key :farsund/market-watcher)
  (let [market-data (read-market-data params)]
    (swap! db merge market-data)
    (async/put! chan {:event :market-report/changed
                      :data  (:market-report/by-id market-data)})
    (hawk/watch! [{:paths   [report-path]
                   :handler (report-handler params)}])))


(defn- check-invoices-path [{:keys [invoices-path max-age-in-days delete-old-files? mask]}]
  (let [files (file-seq (io/as-file invoices-path))]
    (into {}
          (comp (filter #(.isFile %))
                (filter #(re-find (re-pattern mask) (.getName %)))
                (filter #(let [last-modified (-> % (.lastModified) (dtc/from-long))
                               to-old? (dt/before? last-modified
                                                   (dt/minus (dt/today-at-midnight) (dt/days max-age-in-days)))]
                           (when (and to-old? delete-old-files?)
                             (io/delete-file %))
                           to-old?))
                (map #(let [{:keys [id] :as invoice} (invoice/read-file %)]
                        {id invoice})))
          files)))


(defn- invoice-handler [{:keys [db chan mask] :as params}]
  (fn [_ {:keys [kind file]}]
    (when (and (not= kind :delete)
               (re-find (re-pattern mask) (.getName file)))
      (<!! (timeout 3000))
      (when (.exsists file)
        (let [{:keys [id] :as invoice} (invoice/read-file file)]
          (sp/setval [sp/ATOM :invoices/by-id id] invoice db)
          (async/put! chan {:event    :sente/send!
                            :dispatch [:write-to [:market/documents :_invoices/by-id id] invoice]}))))))


(defmethod ig/init-key :farsund/invoice-watcher
  [_ {:keys [db invoices-path] :as params}]
  (timbre/info :ig/init-key :farsund/invoice-watcher)
  (swap! db assoc :invoices/by-id (check-invoices-path params))
  (hawk/watch! [{:paths   [invoices-path]
                 :handler (invoice-handler params)}]))