(ns farsund.routes.core
  (:require [integrant.core :as ig]
            [reitit.core :as reitit]
            [reitit.ring :as ring]
            [ring.util.http-response :as response]
            [ring.util.response :refer [resource-response]]
            [taoensso.timbre :as timbre]
            [farsund.layout :as layout]))


(defn all-routes [{:keys                                                  [db]
                   {:keys [ring-ajax-post ring-ajax-get-or-ws-handshake]} :sente}]
  (ring/ring-handler
    (ring/router
      [
       ["/chsk" {:get  {:handler (fn [req] (ring-ajax-get-or-ws-handshake req))}
                 :post {:handler (fn [req] (ring-ajax-post req))}}]
       ["/public/*" {:get {:handler (fn [_] (resource-response "public"))}}]
       ["/*all" {:get {:handler (fn [req]
                                  (-> (layout/home-page)
                                      (response/ok)
                                      (response/content-type "text/html; charset=utf-8")))}}]]
      {:conflicts nil})
    (ring/create-default-handler
      {:not-found (constantly {:status 404
                               :body   "page not found!"})})))



(defmethod ig/init-key :farsund/routes [_ {:keys [db sente] :as opts}]
  (all-routes opts))