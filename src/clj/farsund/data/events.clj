(ns farsund.data.events
  (:require [clojure.core.async :as async :refer [>!]]
            [com.rpl.specter :as sp]
            [taoensso.encore :as e]
            [farsund.ws.core :as ws]
            [farsund.ws.reponse :as response]
            [farsund.data.market :as market]
            [farsund.data.documents :as docs]
            [farsund.data.utils :refer :all]
            [farsund.transit :as t]
            [farsund.data.cg :as cg]
            [clojure.java.io :as io]))


(defmethod ws/event-msg-handler :data/market-report
  [{:keys [db]} {:keys [?reply-fn] event-id :id}]
  (if-let [data (:market-report/by-id @db)]
    (?reply-fn (response/success event-id data))
    (?reply-fn (response/no-content event-id))))


(defmethod ws/event-msg-handler :data/invoices
  [{:keys [db]} {:keys [?reply-fn] event-id :id}]
  (if-let [data (:invoices/by-id @db)]
    (?reply-fn (response/success event-id data))
    (?reply-fn (response/no-content event-id))))


(defmethod ws/event-msg-handler :cg/cg-warehouse
  [{:keys [db]} {:keys [?reply-fn] event-id :id}]
  (if-let [data (:cg-warehouse/by-id @db)]
    (?reply-fn (response/success event-id data))
    (?reply-fn (response/no-content event-id))))


(defmethod ws/event-msg-handler :data/mm-file-list
  [{:keys [ftp-config]} {:keys [?reply-fn] event-id :id}]
  (if-let [data (cg/list-mm-files ftp-config)]
    (?reply-fn (response/success event-id data))
    (?reply-fn (response/no-content event-id))))


(defmethod ws/event-msg-handler :data/set-market-order
  [{:keys [db]} {:keys [?reply-fn] event-id :id market-order :?data}]
  (sp/setval [sp/ATOM :market-order] market-order db))


(defmethod ws/event-msg-handler :sync/document->collector
  [{:keys [collector-path]} {:keys [?reply-fn] event-id :id products :?data}]
  (let [csv (docs/document->csv products)]
    (println collector-path (e/path collector-path "rcv.txt"))
    (spit (e/path collector-path "rcv.txt") csv)))


(defmethod ws/event-msg-handler :sync/document->ftp
  [{:keys [ftp-config]} {:keys [?reply-fn] event-id :id order :?data}]
  (cg/document->ftp ftp-config order))


(defmethod ws/event-msg-handler :sync/ftp->collector
  [{:keys [collector-path ftp-config]} {:keys [?reply-fn] event-id :id file-name :?data}]
  (let [edi (cg/get-mm-from-ftp ftp-config file-name)
        csv (-> edi
                (cg/edi->document)
                (docs/document->csv))]
    (spit (e/path collector-path "rcv.txt") csv)))


(defmethod ws/event-msg-handler :document/change-product-qty
  [{:keys [db chan]} {:keys [invoice-id product-id qty]}]
  (sp/setval [sp/ATOM :invoices/by-id invoice-id product-id :qty] qty db)
  (async/put! chan {:event    :sente/send!
                    :dispatch [:write-to [:order :_documents/by-id invoice-id product-id :qty] qty]}))