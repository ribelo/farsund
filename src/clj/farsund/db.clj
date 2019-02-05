(ns farsund.db
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [cuerdas.core :as str]
            [taoensso.nippy :as nippy]
            [clj-time.core :as dt]
            [farsund.config :refer [config]]
            [farsund.data.market :as market]
            [farsund.data.sales :as sales]
            [farsund.data.cg :as cg]))


(defmethod ig/init-key :farsund/db [_ {:keys [file-path]}]
  (atom {}))


(defmethod ig/halt-key! :farsund/db [_ component]
  (reset! component nil))








