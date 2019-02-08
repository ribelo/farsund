(ns farsund.repl
  (:require [clojure.core.async :as async :refer [go thread <! <!! >! >!!]]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [taoensso.encore :as e]
            [taoensso.timbre :as timbre]
            [com.rpl.specter :as sp]
            [cuerdas.core :as str]
            [clojure.spec.alpha :as s]
            [reitit.core :as reitit]
            [clj-time.core :as dt]
            [clj-time.coerce :as dtc]
            [farsund.config :refer [config]]
            [farsund.core]
            [farsund.db]
            [farsund.nrepl]
            [farsund.middleware]
            [farsund.handler]
            [farsund.server]
            [farsund.routes.core]
            [farsund.ws.core]
            [farsund.data.core]
            [farsund.data.events]
            [farsund.data.market]
            [farsund.data.market :as market]
            [farsund.data.invoice :as invoice]

            [net.cgrand.xforms :as x]
            [farsund.data.sales :as sales]
            [farsund.ml.core :as ml]
            [farsund.ml.estimate :as estimate]
            [farsund.db :as db]
    ;[farsund.firestore.core :as f]
            ))



(timbre/merge-config! {:level :info})
;(s/check-asserts true)
(def system (atom nil))

(declare tmp-db)
(defn run [] (do (reset! system (ig/init config [:farsund/db
                                                 ;:farsund/firestore
                                                 :pubsub/channel
                                                 :pubsub/publisher
                                                 :farsund/webserver
                                                 :farsund/cg
                                                 :farsund/market-watcher
                                                 :farsund/invoice-watcher
                                                 :farsund/estimator
                                                 ]))
                 (def tmp-db (:farsund/db @system))))
(defn stop [] (ig/halt! @system))
(defn reload [] (stop) (run))

(run)
(stop)


(count (:market-report/by-id @tmp-db))

(into (sorted-map-by (fn [{:keys [stock optimal]}]
                       (- stock optimal)))
      [{:stock 1 :optimal 0}
       {:stock 1 :optimal 0}])