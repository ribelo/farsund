(defproject farsund "0.4.5-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :plugins [[lein-tools-deps "0.4.5"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :main ^:skip-aot farsund.core
  :clean-targets ^{:protect false} ["resources/public/js" ".repl" "target"]
  :profiles {:uberjar {:omit-source  true
                       :prep-tasks   [["compile"]]
                       :aot          :all
                       :uberjar-name "farsund.jar"}})
