(ns farsund.ws.reponse
  (:require [clojure.spec.alpha :as s]
            [ring.util.http-status :as status]))


(s/def ::id keyword?)
(s/def ::success boolean?)
(s/def ::reponse (s/keys :req-un [::id ::success]))
(s/def ::status int?)


(defn success [id data]
  {:id     id
   :status status/ok
   :data   data})


(defn no-content [id]
  {:id     id
   :status status/no-content
   :data   nil})


(defn internal-server-error [id e]
  {:id     id
   :status status/internal-server-error
   :data   (.getMessage e)})


(defn unauthorized [id]
  {:id     id
   :status status/unauthorized
   :data   (status/get-name status/unauthorized)})