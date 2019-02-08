(ns farsund.data.cg
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [taoensso.encore :as e]
            [taoensso.timbre :as timbre]
            [com.rpl.specter :as sp]
            [dk.ative.docjure.spreadsheet :as xls]
            [cuerdas.core :as str]
            [clj-time.core :as dt]
            [clj-time.format :as dtf]
            [miner.ftp :as ftp]
            [farsund.data.utils :refer :all]
            [farsund.data.market :as market]
            [farsund.config :refer [config]]))


(defn download-cg-warehouse [{:keys [address user password]}]
  (timbre/debug :download-cg-warehouse address user password)
  (ftp/with-ftp [client (str "ftp://" user ":" password "@" address "/teas/cg")]
    (ftp/client-get client "mag.csv")))


(defn read-cg-data [path]
  (->> (read-csv-data path)
       (rest)
       (into {}
             (comp
               (filter (fn [[_ ean]]
                         (not-empty ean)))
               (map (fn [[name ean qty buy-price price-1 price-2]]
                      {ean {:name      (str/lower name)
                            :ean       ean
                            :stock     (Double/parseDouble qty)
                            :buy-price (Double/parseDouble buy-price)
                            :price-1   (Double/parseDouble price-1)
                            :price-2   (Double/parseDouble price-2)}}))))))


(defn read-cg-from-ftp [ftp-config]
  (if (download-cg-warehouse ftp-config)
    (let [data (read-cg-data "./mag.csv")]
      (when (.exists (io/as-file "./mag.csv"))
        (io/delete-file (io/as-file "./mag.csv") true))
      data)
    []))


(defmethod ig/init-key :farsund/cg [_ {:keys [db ftp] :as params}]
  (let [cg-warehouse (future (read-cg-from-ftp ftp))]
    (sp/setval [sp/ATOM :cg-warehouse/by-id] @cg-warehouse db)
    params))


(defn edi-header []
  (str "[Info]\r\n"
       "Program=SMALL BUSINESS 5.2.2730.8629\n"
       "Kodowanie=Windows 1250\r\n"
       "Nazwa=TEAS Sp.z o.o.                             59-500 Złotoryja                           ul.Podmiejska 1                            tel.76 87-83-226\r\n"
       "Nip=694-10-02-891\r\n"
       "Konto=BS 74-8658-0009-0000-4718-2000-0010\r\n"
       "Kod=59-500\r\n"
       "Miasto=Złotoryja\r\n"
       "Data=" (dtf/unparse (dtf/formatter "YY.MM.dd") (dt/now)) "\r\n"
       "Godz=" (dtf/unparse (dtf/formatter "HH:mm:ss") (dt/now)) "\r\n"
       "\r\n"
       "\r\n"
       "[Dokument]\r\n"
       "DataWyst=" (dtf/unparse (dtf/formatter "YY.MM.dd") (dt/now)) "\r\n"
       "Odbiorca=1\r\n"
       "NrDok=\r\n"
       "IdentyfikatorDok=" (Long/parseLong (dtf/unparse (dtf/formatter "YYMMddHHmmss") (dt/now))) "\r\n"
       "Magazyny=0\r\n"
       "\r\n"
       "\r\n"
       "[ZawartoscDokumentu]"
       "\r\n"))


(defn document->edi [doc]
  (str/join "\r\n"
            (into []
                  (comp (map-indexed (fn [i {:keys [qty ean]}]
                                       (str "[Poz" (inc i) "]\r\n"
                                            "Symbol=" ean "\r\n"
                                            "Ilosc=" qty "\r\n"
                                            "Mag=0\r\n"))))
                  doc)))


(defn document->ftp [{:keys [out-path user password address]} doc]
  (let [data (str (edi-header) (document->edi doc))
        file-name (str "./" (dtf/unparse (dtf/formatter "YYYY_MM_dd") (dt/now)) ".mm")]
    (spit file-name data)
    (let [send? (ftp/with-ftp [client (str "ftp://" user ":" password
                                           "@" address (str "/teas/" out-path))]
                  (ftp/client-put client (io/as-file file-name)))]
      (when (.exists (io/as-file file-name))
        (io/delete-file (io/as-file file-name) true))
      send?)))


(defn edi->document [edi]
  (let [data (clojure.string/split-lines edi)]
    (loop [[line & data] data
           result []
           elem {}
           n 0]
      (if line
        (cond
          (str/starts-with? line "[Poz")
          (recur data (conj result (assoc elem :position (inc n))) {} (inc n))
          (str/starts-with? line "Symbol=")
          (let [ean (last (str/split line "="))]
            (recur data result (assoc elem :ean ean) n))
          (str/starts-with? line "Ilosc=")
          (let [qty (-> line (str/split "=") (last) Double/parseDouble)]
            (recur data result (assoc elem :qty qty) n))
          :else (recur data result elem n))
        (into {}
              (comp (filter seq)
                    (remove #(nil? (:qty %)))
                    (map (juxt :ean identity)))
              result)))))


(defn list-mm-files [{:keys [address user password in-path]}]
  (ftp/with-ftp [client (str "ftp://" user ":" password
                             "@" address "/teas/" in-path)]
    (ftp/client-all-names client)))


(defn download-mm [{:keys [address user password in-path]} file-name]
  (timbre/debug :download-mm address user password file-name)
  (ftp/with-ftp [client (str "ftp://" user ":" password
                             "@" address "/teas/"
                             in-path "/")]
    (ftp/client-get client file-name)))


(defn delete-mm [{:keys [address user password in-path]} file-name]
  (timbre/debug :delete-mm address user password file-name)
  (ftp/with-ftp [client (str "ftp://" user ":" password
                             "@" address "/teas/"
                             in-path "/")]
    (ftp/client-delete client file-name)))


(defn get-mm-from-ftp [ftp-config file-name]
  (if (download-mm ftp-config file-name)
    (let [data (slurp (str "./" file-name) :encoding "cp1250")]
      (delete-mm ftp-config file-name)
      (when (.exists (io/as-file (str "./" file-name)))
        (io/delete-file (io/as-file (str "./" file-name)) true))
      data)
    []))

