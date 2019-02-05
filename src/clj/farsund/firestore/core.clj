(ns farsund.firestore.core
  (:require [clojure.java.io :as io]
            [clojure.walk :refer [postwalk]]

            [taoensso.encore :as e]
            [cuerdas.core :as str]
            [integrant.core :as ig]
            [taoensso.encore :as e])
  ;(:import (com.google.firebase FirebaseOptions$Builder
  ;                              FirebaseApp)
  ;         (com.google.auth.oauth2 GoogleCredentials)
  ;         (com.google.cloud.firestore FirestoreOptions)
  ;         (java.io FileInputStream))
  )


;(defmethod ig/init-key :firebase/credentials [_ ^String path]
;  (let [config (FileInputStream. path)]
;    (GoogleCredentials/fromStream config)))
;
;
;(defmethod ig/init-key :farsund/firestore [_ {:keys [credentials]}]
;  (let [
;        firebase-options (-> (FirebaseOptions$Builder.)
;                             (.setCredentials credentials)
;                             (.setDatabaseUrl "https://farsund-54092.firebaseio.com")
;                             (.build))
;        _ (FirebaseApp/initializeApp firebase-options)
;        options (-> (FirestoreOptions/newBuilder)
;                    (.setCredentials credentials)
;                    (.setTimestampsInSnapshotsEnabled true)
;                    (.build))]
;    (.getService options)))
;
;
;(defn clojurify
;  [exp]
;  (cond
;    (instance? java.util.Map exp) (into {} (for [[k v] exp] [(str/keyword k) v]))
;    (instance? java.util.List exp) (into [] exp)
;    (instance? java.lang.Float exp) (e/round* :round 3 exp)
;    (instance? java.lang.Double exp) (e/round* :round 3 exp)
;    :else exp))
;
;
;(declare clj->java)
;
;
;(defn javify
;  [exp]
;  (cond
;    (instance? java.util.Map exp) (java.util.HashMap. (into {} (for [[k v] exp] [(name k) (clj->java v)])))
;    (instance? java.util.List exp) (java.util.LinkedList. (map clj->java exp))
;    (instance? java.lang.Float exp) (e/round* :round 3 exp)
;    (instance? java.lang.Double exp) (e/round* :round 3 exp)
;    :else exp))
;
;
;(defn java->clj
;  [data]
;  (clojure.walk/prewalk clojurify data))
;
;
;(defn clj->java
;  [data]
;  (clojure.walk/prewalk javify data))
;
;
;(defmulti path->field (fn [path] (type path)))
;
;(defmethod path->field java.util.Collection
;  [path]
;  (str/join "/" (map name path)))
;
;(defmethod path->field clojure.lang.Keyword
;  [path]
;  (name path))
;
;(defmethod path->field String
;  [path]
;  path)
;
;
;(defn document-reference [fs path]
;  (.document fs (path->field path)))
;
;
;(defn collection-reference [fs path]
;  (.collection fs (path->field path)))
;
;
;(defn doc->clj [doc]
;  (-> doc
;      (.get)
;      (.get)
;      (.getData)
;      (java->clj)))
;
;
;(defn document-exists? ^Boolean [fs path]
;  (-> (document-reference fs path)
;      (.get)
;      (.get)
;      (.exists)))
;
;
;(defn get-document [fs path]
;  (-> fs
;      (document-reference path)
;      (doc->clj)))
;
;
;(defn get-collection [fs path]
;  (-> fs
;      (collection-reference path)
;      (.get)
;      (.get)
;      (.getDocuments)
;      (->> (map #(-> % (.getData) (java->clj))))))
;
;
;(defn delete-collection [fs path]
;  (let [docs (-> fs
;                 (collection-reference path)
;                 (.limit 10)
;                 (.get)
;                 (.get)
;                 (.getDocuments))]
;    (doseq [doc docs]
;      (-> doc (.getReference) (.delete)))
;    (when (>= (count docs) 10)
;      (recur fs path))))
;
;
;(defn add-document [fs path doc]
;  (-> fs
;      (collection-reference path)
;      (.add (clj->java doc))))
;
;
;(defn set-document [fs path doc]
;  (-> fs
;      (document-reference path)
;      (.set (clj->java doc))))
;
;
;(defn update-document [fs path doc]
;  (-> fs
;      (document-reference path)
;      (.update (clj->java doc))))
;
;
;(defn delete-document [fs path]
;  (-> fs
;      (document-reference path)
;      (.delete)))



;(->
;    (.document (path->field ["debug-document" "test"]))
;    (.set (clj->java {:a 1})))
;
;(path->field ["collection" "document"])
;
;
