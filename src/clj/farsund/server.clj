(ns farsund.server
  (:require [clojure.spec.alpha :as s]
            [integrant.core :as ig]
            [taoensso.timbre :as timbre]
            [immutant.web :as immutant]
            [cuerdas.core :as str]))


(defmethod ig/init-key :farsund/webserver [_ {:keys [handler port] :as opts}]
  (try
    (timbre/info (str/format "started web-server on port %s" port))
    (immutant/run handler (dissoc opts :handler))
    (catch Throwable t
      (timbre/error t (str/format "web-server failed to start on port %s" port))
      (throw t))))


(defmethod ig/halt-key! :farsund/webserver [_ server]
  (immutant/stop server)
  (timbre/info "web-server stopped"))