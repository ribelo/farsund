(ns farsund.handler
  (:require [integrant.core :as ig]
            [farsund.middleware :as middleware]))


(defmethod ig/init-key :farsund/handler [_ {:keys [routes]}]
  (middleware/wrap-base
    (middleware/wrap-csrf
      routes)))