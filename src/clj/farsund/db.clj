(ns farsund.db
  (:require
   [integrant.core :as ig]))

(defmethod ig/init-key :farsund/db [_ {:keys [file-path]}]
  (atom {}))

(defmethod ig/halt-key! :farsund/db [_ component]
  (reset! component nil))
