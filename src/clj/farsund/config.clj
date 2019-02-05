(ns farsund.config
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]))


(def config
  (ig/read-string (slurp "./config.edn")))