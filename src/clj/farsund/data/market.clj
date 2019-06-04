(ns farsund.data.market
  (:require
   [clojure.java.io :as io]
   [clojure.core.async :as async :refer [timeout <!! >! >!!]]
   [integrant.core :as ig]
   [taoensso.timbre :as timbre]
   [java-time :as jt]
   [com.rpl.specter :as sp]
   [hawk.core :as hawk]
   [farsund.data.sales :as sales]
   [farsund.data.report :as report]
   [farsund.data.invoice :as invoice]))

(defn read-market-data [{:keys [^String market-id ^String report-path
                                ^String data-path ^long days-to-read]}]
  (let [store-sales (future (sales/read-store-sales
                             market-id
                             (jt/minus (jt/truncate-to (jt/local-date-time) :days) (jt/days days-to-read))
                             (jt/truncate-to (jt/local-date-time) :days)
                             data-path))
        sale-price (future (sales/read-sale-price
                            market-id
                            (jt/minus (jt/truncate-to (jt/local-date-time) :days) (jt/days days-to-read))
                            (jt/truncate-to (jt/local-date-time) :days)
                            data-path))
        ;stock-data (future (stock/read-file market-id data-path))
        market-report (future (report/read-file report-path))]
    {:store-sales         (clojure.set/join @store-sales @sale-price)
     :market-report/by-id @market-report}))

(defn- report-handler
  [{:keys [db ^String report-path chan]}]
  (fn [_ {:keys [kind]}]
    (when (not= kind :delete)
      (<!! (timeout 3000))
      (println :report-handler)
      (let [report (future (report/read-file report-path))]
        (async/put! chan {:event :market-report/changed
                          :data  @report})
        (swap! db assoc :market-report/by-id @report)))))

(defmethod ig/init-key :farsund/market-watcher
  [_ {:keys [db ^String report-path chan] :as params}]
  (timbre/info :ig/init-key :farsund/market-watcher)
  (let [market-data (read-market-data params)]
    (swap! db merge market-data)
    (async/put! chan {:event :market-report/changed
                      :data  (:market-report/by-id market-data)})
    (hawk/watch! [{:paths   [report-path]
                   :handler (report-handler params)}])))

(defn- check-invoices-path [{:keys [^String invoices-path ^long max-age-in-days
                                    ^boolean delete-old-files? ^String mask]}]
  (let [files (file-seq (io/as-file invoices-path))]
    (into {}
          (comp (filter #(.isFile %))
                (filter #(re-find (re-pattern mask) (.getName %)))
                (filter #(let [last-modified (-> (.lastModified %)
                                                 (jt/instant)
                                                 (jt/local-date-time (jt/zone-id)))
                               to-old? (jt/before? last-modified
                                                   (jt/minus (jt/truncate-to (jt/local-date-time) :days)
                                                             (jt/days max-age-in-days)))]
                           (when (and to-old? delete-old-files?)
                             (io/delete-file %))
                           (not to-old?)))
                (map #(let [{:keys [id] :as invoice} (invoice/read-file %)]
                        {id invoice})))
          files)))

(defn- invoice-handler [{:keys [db chan ^String mask]}]
  (fn [_ {:keys [kind file]}]
    (when (and (not= kind :delete)
               (re-find (re-pattern mask) (.getName file)))
      (<!! (timeout 3000))
      (when (.exsists file)
        (let [{:keys [id] :as invoice} (invoice/read-file file)]
          (sp/setval [sp/ATOM :invoices/by-id id] invoice db)
          (async/put! chan {:event    :sente/send!
                            :dispatch [:write-to [:invoices :_documents/by-id id] invoice]}))))))

(defmethod ig/init-key :farsund/invoice-watcher
  [_ {:keys [db ^String invoices-path] :as params}]
  (timbre/info :ig/init-key :farsund/invoice-watcher)
  (swap! db assoc :invoices/by-id (check-invoices-path params))
  (hawk/watch! [{:paths   [invoices-path]
                 :handler (invoice-handler params)}]))
