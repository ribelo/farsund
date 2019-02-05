(ns farsund.core
  (:require [clojure.core.async :as async :refer [<!! timeout]]
            [integrant.core :as ig]
            [cuerdas.core :as str]
            [hawk.core :as hawk]
            [taoensso.timbre :as timbre]
            [taoensso.nippy :as nippy]
            [com.rpl.specter :as sp]
            [farsund.config :refer [config]]
            [farsund.db]
            [farsund.nrepl]
            [farsund.middleware]
            [farsund.handler]
            [farsund.server]
            [farsund.routes.core]
            [farsund.ws.core]
            [farsund.data.core]
            [farsund.data.events]
            [farsund.data.market :as market]
            [farsund.data.cg :as cg]
            [farsund.ml.estimate])
  (:gen-class))


(defmethod ig/init-key :pubsub/channel [_ opts]
  (async/chan (async/buffer 1)))

(defmethod ig/halt-key! :pubsub/channel [_ chan]
  (async/close! chan))

(defmethod ig/init-key :pubsub/publisher [_ {:keys [chan topic]}]
  (async/pub chan topic))

(defmethod ig/init-key :farsund/ftp [_ ftp-config] ftp-config)

(defmethod ig/init-key :farsund/timbre [_ config]
  (timbre/merge-config! config))


(defn -main [& args]
  (timbre/debug :-main)
  (ig/init config))



