(ns farsund.nrepl
  (:require [clojure.tools.nrepl.server :as nrepl]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [cuerdas.core :as str]
            [farsund.config :refer [config]]))


(defmethod ig/init-key :farsund/nrepl [_ {:keys [port]}]
  (timbre/info (str/format "started nrepl-server on port %s" port))
  (nrepl/start-server :port port))


(defmethod ig/halt-key! :farsund/nrepl [_ server]
  (timbre/info (str/format "stoped nrepl-server"))
  (nrepl/stop-server server))