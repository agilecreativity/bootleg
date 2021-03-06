(defproject bootleg #= (clojure.string/trim #= (slurp ".meta/VERSION"))
  :description "Simple template processing command line tool to help build static websites"
  :url "https://github.com/retrogradeorbit/bootleg"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/data.json "0.2.6"]
                 [markdown-clj "1.10.0"]

                 ;; https://github.com/owainlewis/yaml/issues/35
                 [io.forward/yaml "1.0.9"
                  :exclusions [[org.yaml/snakeyaml]]]
                 [org.yaml/snakeyaml "1.25"]

                 [cljstache "2.0.4"]
                 [enlive "1.1.6"]
                 [hickory "0.7.1"]
                 [selmer "1.12.17"]

                 ;; for hiccup eval
                 [borkdude/sci "0.0.11-alpha.14" :exclusions [[borkdude/edamame]]]
                 [borkdude/edamame "0.0.9-alpha.2"]

                 ;; clojures pprint doesn't work under graal native-image
                 [fipp "0.6.21"]
                 [mvxcvi/puget "1.2.0"]
                 ]
  :main ^:skip-aot bootleg.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
