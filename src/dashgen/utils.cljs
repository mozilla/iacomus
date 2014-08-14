(ns dashgen.utils
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.net.XhrIo :as xhr]
            [cljs.core.async :refer  [chan close! >!]]
            [om.core :as om :include-macros true]
            [clojure.walk :as walk :refer [keywordize-keys]]))

(enable-console-print!)

(defn multi-nth [values indices]
  (map (partial nth values) indices))

(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (let [res (-> event .-target .getResponseText)]
                  (go (>! ch res) (close! ch)))))
    ch))

(defn pretty-date [date]
  (let [pretty (.toUTCString date)
        pretty (subs pretty 0 16)]
    pretty))

(defn yyyymmdd [date]
  (let [year (.getUTCFullYear date)
        month (+ 1 (.getUTCMonth date))
        day (.getUTCDate date)
        zpad #(str (if (< %1 10) "0" "") %1)]
    (str year (zpad month) (zpad day))))

(defn get-dates [base-offset]
  (let [week-offset (+ (- (* base-offset 7)) 7)
        yesterday (let [tmp (js/Date.)]
                    (.setDate tmp (- (.getDate tmp) week-offset))
                    tmp)
        dow-offset (rem (- (.getUTCDay yesterday) 1) 7)
        current-week-start (let [tmp (js/Date. yesterday)]
                             (.setDate tmp (- (.getDate tmp) dow-offset))
                             tmp)
        current-week-end (let [tmp (js/Date. current-week-start)]
                           (.setDate tmp (+ (.getDate tmp) 6))
                           tmp)
        last-week-start (let [tmp (js/Date. current-week-start)]
                          (.setDate tmp (- (.getDate tmp) 7))
                          tmp)
        last-week-end (let [tmp (js/Date. current-week-end)]
                        (.setDate tmp (- (.getDate tmp) 7))
                        tmp)]
  {:current-week [current-week-start current-week-end]
   :past-week [last-week-start last-week-end]}))

(defn index-of [value coll]
  (loop [idx 0]
    (if (< idx (count coll))
      (if (= (nth coll idx) value)
        idx
        (recur (+ idx 1)))
      nil)))

(defn coerce [value]
  (let [num (js/parseFloat value)]
    (if (js/isNaN num)
      value
      num)))

(defn time-wrap [fun]
  (fn [& args]
    (let [start (.getTime (js/Date.))
          res (apply fun args)
          end (.getTime (js/Date.))]
      (println (- end start))
      res)))

(defn header-to-index-map [headers]
  (zipmap headers (iterate inc 0)))

(defn filter-map [filters]
  (reduce conj (map-indexed (fn [idx in] {(:id in) (assoc in :index idx)}) filters)))

(defn sort-array-by-index! [array index]
  (let [sortfn (fn [a b]
                 (- (coerce (aget b index))
                    (coerce (aget a index))))]
    (.sort array sortfn)))

(defn sort-raw-data! [app data]
  (let [column (get-in @app [:sort-options :selected])
        index (index-of column (:header @app)) ]
    (sort-array-by-index! data index)))

(defn sort-data! [app]
  (let [ current-week (:current-week @app)
        past-week (:past-week @app)]
    (when (seq current-week) (sort-raw-data! app current-week))
    (when (seq past-week) (sort-raw-data! app past-week))))

(defn parse-csv [data]
  (try
    (let [arrays (.toArrays (.-csv js/$) data)]
      (.shift arrays)
      arrays)
    (catch js/Error e
      (println e)
      [])))

(let [csv-store (atom {})]
  (defn load-csv [prefix date-key date-offset]
    (go
      (let [dates (-> date-offset get-dates date-key)
            filename (str prefix "_" (-> dates first yyyymmdd) ".csv.gz")]
        (if-let [file (get-in @csv-store [filename])]
          file
          (let [data (parse-csv (<! (GET filename)))]
            (swap! csv-store conj [filename data])
            data))))))

(defn load-data! [app date-offset]
  (go
    (let [url-prefix (:url-prefix @app)
          current-week (<! (load-csv url-prefix :current-week date-offset))
          past-week (<! (load-csv url-prefix :past-week date-offset))]
      (when (seq current-week) (sort-raw-data! app current-week))
      (when (seq past-week) (sort-raw-data! app past-week))
      (om/transact! app #(assoc %1
                                :current-week current-week
                                :past-week past-week
                                :date-offset [date-offset]
                                :throbber ["hidden"])))))

(defn load-config! [app]
  (go
    (if-let [filename (js/getParameterByName "config")]
      (let [data (<! (GET filename))
            config (try
                     (keywordize-keys (js->clj (.parse js/JSON data)))
                     (catch js/Error e
                       (om/update! app :severe-error [e])
                       nil))]
        (when (seq config)
          (om/transact! app #(merge %1 config))))
      (om/update! app :severe-error ["Configuration file is missing!"]))))

