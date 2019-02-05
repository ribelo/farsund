(ns farsund.layout
  (:require [hiccup.core :refer :all]
            [hiccup.page :as page]
            [hiccup.element :refer [javascript-tag]]))


(defn base-head []
  [:head
   [:meta {:http-equiv "Content-Type"
           :content    "text/html; charset=UTF-8"}]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1"}]
   [:title "farsund"]])


(defn base-body []
  [:body {:style "height: 100%;"}
   [:div {:id    "app"
          :style "height: 100%;"}
    "Loading App..."]
   ;(page/include-css "/css/farsund.css")
   (page/include-js "/js/app.js")
   ])

(defn base-layout []
  (page/html5 {:lang  "pl"
               :style "height: 100%;"}
              (base-head)
              (base-body)))

(defn home-page []
  (base-layout))

