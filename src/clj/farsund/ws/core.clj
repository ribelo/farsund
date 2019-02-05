(ns farsund.ws.core
  (:require [clojure.core.async :as async :refer [go thread go-loop <! >!]]
            [integrant.core :as ig]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
            [taoensso.timbre :as timbre]
            [farsund.transit :as t]
            [farsund.ws.reponse :as response]))


(defmulti event-msg-handler (fn [db {:keys [id]}] id))


(defn -event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [db {:as ev-msg :keys [id ?data event ?reply-fn]}]
  (when (not= :chsk/ws-ping id)
    (timbre/debug "try to handle event:" id ?data))
  (thread
    (try
      (event-msg-handler db ev-msg)
      (catch Exception e
        (?reply-fn (response/internal-server-error id e))))))


(defmethod event-msg-handler :default
  [db {:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (timbre/warn "unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(ns farsund.ws.core
  (:require [clojure.core.async :as async :refer [go thread go-loop <! >!]]
            [integrant.core :as ig]
            [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]
            [taoensso.sente.server-adapters.immutant :refer [get-sch-adapter]]
            [taoensso.timbre :as timbre]
            [farsund.transit :as t]
            [farsund.ws.reponse :as response]))


(defmulti event-msg-handler (fn [db {:keys [id]}] id))


(defn -event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [db {:as ev-msg :keys [id ?data event ?reply-fn]}]
  (when (not= :chsk/ws-ping id)
    (timbre/debug "try to handle event:" id ?data))
  (thread
    (try
      (event-msg-handler db ev-msg)
      (catch Exception e
        (?reply-fn (response/internal-server-error id e))))))


(defmethod event-msg-handler :default
  [db {:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (timbre/warn "unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))


(defmethod event-msg-handler :chsk/ws-ping
  [db {:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (timbre/debug "handle ping event")))


(defn sender-loop [{:keys [chsk-send! connected-uids]} {:keys [pub] :as params}]
  (let [topic-chan (async/chan)]
    (async/sub pub :sente/send! topic-chan)
    (let [any-uids (:any @connected-uids)]
      (go-loop []
        (when-let [{:keys [dispatch]} (<! topic-chan)]
          (doseq [uid any-uids]
            (chsk-send! uid [:rf/dispatch dispatch]))
          (recur))))))


(defmethod ig/init-key :farsund/sente [_ {:keys [db] :as params}]
  (let [packer (sente-transit/->TransitPacker :json t/write-handlers t/read-handlers)
        {:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket! (get-sch-adapter)
                                    {:packer     packer
                                     :user-id-fn (fn [ring-req]
                                                   (timbre/warn "sente/make-channel-socket! " (:client-id ring-req))
                                                   (:client-id ring-req))})
        component {:ring-ajax-post                ajax-post-fn
                   :ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn
                   :ch-chsk                       ch-recv   ; ChannelSocket's receive channel
                   :chsk-send!                    send-fn   ; ChannelSocket's send API fn
                   :connected-uids                connected-uids ; Watchable, read-only atom
                   :router                        (sente/start-server-chsk-router! ch-recv (partial -event-msg-handler params))}
        sender (sender-loop component params)]
    (assoc component :sender sender)))


(defmethod ig/halt-key! :farsund/sente [_ {:keys [sender router]}]
  (async/close! sender)
  (router)
  {})