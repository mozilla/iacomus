(defproject telemetry-dashboard-generator "0.1.0"
  :author "Roberto Agostino Vitillo <rvitillo@mozilla.com>"
  :description "Dashboard generator for Telemetry analyses"
  :url "https://www.github.com/vitillo/telemetry-dashboard-generator"
  :license {:name "Mozilla Public License 2.0"
            :url "https://www.mozilla.org/MPL/2.0/index.txt"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2280"]
                 [figwheel "0.1.3-SNAPSHOT"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.7.0"]]

  :jvm-opts ["-Xmx1G"]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.3-SNAPSHOT"]
            [com.cemerick/austin "0.1.4"]]

  :figwheel {
    :http-server-root "public"
    :port 3449
    :css-dirs ["resources/public/css"]}

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/dashgen" "src/figwheel" "src/brepl"]
              :compiler {
                :output-to "resources/public/dashgen_dev.js"
                :output-dir "resources/public/out"
                :optimizations :none
                :pretty-print true
                :source-map true}}
             {:id "release"
              :source-paths ["src/dashgen"]
              :compiler {
                :output-to "resources/public/dashgen.js"
                :output-dir "resources/public/prod-out"
                :optimizations :simple
                :externs ["resources/public/externs/react_externs.js resources/public/externs/jquery_externs.js"]
                :pretty-print false
                :source-map "resources/public/dashgen.js.map"}} ]})
