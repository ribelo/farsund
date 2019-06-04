(ns farsund.data.core
  (:require
   [integrant.core :as ig]))

(defmethod ig/init-key :market/id [_ ^String id] id)

(defmethod ig/init-key :market/collector-path [_ ^String path] path)

(defmethod ig/init-key :market/report-path [_ ^String path] path)

(defmethod ig/init-key :market/data-path [_ ^String path] path)

(defmethod ig/init-key :market/invoices-path [_ ^String path] path)
