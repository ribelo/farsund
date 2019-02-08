(defproject farsund "0.4.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/math.combinatorics "0.1.4"]
                 [org.immutant/immutant "2.1.10" :exclusions [ch.qos.logback/logback-classic]]
                 [integrant "0.7.0"]
                 [com.taoensso/encore "2.102.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [metosin/reitit "0.2.2"]
                 [metosin/muuntaja "0.6.0"]
                 [metosin/ring-http-response "0.9.0"]
                 [com.fasterxml.jackson.core/jackson-core "2.9.6"]
                 [com.fasterxml.jackson.datatype/jackson-datatype-joda "2.9.6"]
                 [ring/ring-core "1.7.0"]
                 [ring/ring-defaults "0.3.2"]
                 [clj-time "0.15.1"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [cheshire "5.8.1"]
                 [clj-pdf "2.2.33"]
                 [com.taoensso/sente "1.13.1"]
                 [duratom "0.4.1"]
                 [net.cgrand/xforms "0.19.0"]
                 [com.rpl/specter "1.1.2"]
                 [dk.ative/docjure "1.13.0"]
                 [com.velisco/clj-ftp "0.3.12"]
                 [hawk "0.2.11"]
                 [funcool/cuerdas "2.0.6"]
                 [com.taoensso/nippy "2.14.0"]
                 [com.github.haifengl/smile-core "1.5.2"]
                 [ribelo/visby "0.1.1-SNAPSHOT"]
                 [ribelo/wombat "0.1.1-SNAPSHOT"]
                 [tech.tablesaw/tablesaw-core "0.30.2"]
                 [techascent/tech.ml-base "3.5"]]
  ;:jvm-opts ["-server" "-Xmx1024m"]
  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :main ^:skip-aot farsund.core
  :plugins [[lein-shell "0.5.0"]]
  :clean-targets ^{:protect false} ["resources/public/js" ".repl" "target"]
  :profiles {:uberjar {:omit-source  true
                       :prep-tasks   [["compile"]]
                       :aot          :all
                       :uberjar-name "farsund.jar"}
             :prod    {:source-paths ["env/prod/clj"]}
             :dev     {:source-paths ["env/dev/clj"]
                       :dependencies [[criterium "0.4.4"]]}})
