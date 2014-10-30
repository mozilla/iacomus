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

(defn get-prev-week [date]
  (let [tmp (js/Date. date)]
    (.setDate tmp (- (.getDate tmp) 7))
    tmp))

(defn get-next-week [date]
  (let [tmp (js/Date. date)]
    (.setDate tmp (+ (.getDate tmp) 7))
    tmp))

(defn trimmed-date-str [date]
  (-> date str (subs 0 15)))

(defn get-dates [base-date]
  (let [week-offset 7
        yesterday (let [tmp (js/Date. base-date)]
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
      (-> num
          (* 100)
          js/Math.round
          (/ 100)))))

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
                 (let [a (coerce (aget a index))
                       b (coerce (aget b index))
                       op (if (string? a) < >)]
                   (cond
                    (op b a) 1
                    (= b a) 0
                    :else -1)))]
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
  (defn load-csv [prefix date-key base-date]
    (go
      (let [dates (-> base-date get-dates date-key)
            filename (str prefix "_" (-> dates first yyyymmdd) ".csv.gz")]
        (if-let [file (get-in @csv-store [filename])]
          file
          (let [data (parse-csv (<! (GET filename)))]
            (swap! csv-store conj [filename data])
            data))))))

(defn load-data! [app base-date]
  (go
    (let [url-prefix (:url-prefix @app)
          current-week (<! (load-csv url-prefix :current-week base-date))
          past-week (<! (load-csv url-prefix :past-week base-date))]
      (when (seq current-week) (sort-raw-data! app current-week))
      (when (seq past-week) (sort-raw-data! app past-week))
      (om/transact! app #(assoc %1
                                :current-week current-week
                                :past-week past-week
                                :base-date [(trimmed-date-str base-date)]
                                :throbber ["hidden"])))))

(defn query-string []
  (.substring (aget js/window "location" "hash") 1))

(defn query-string->params []
  (let [input (query-string)
        kv-seq (re-seq #"[?&]?([^=&]*)=([^=&]*)" input)
        params (reduce conj {} (map (fn [[_ k v]]
                                      {k v})
                                    kv-seq))]
    params))

(defn params->query-string [params]
  (let [params (->> params
                    (map (fn [[key value]] (str (name key) "=" value "&")))
                    (apply str)
                    (js/encodeURIComponent))]
    (apply str "#?" params)))

(defn edit-query-string [params]
  (let [current-params (query-string->params)
        params (merge current-params params)
        new-query-string (params->query-string params)]
    (aset js/window "location" "hash" new-query-string)))

(defn update-state-from-query-string! [state]
  ;update only relevant part of the state
  (go
    (let [params (query-string->params)]
      (when-let [selected (get params "sort")]
        (when (not= selected (get-in @state [:sort-options :selected]))
          (om/update! state [:sort-options :selected] selected)
          (sort-data! state)))
      (when-let [selected (get params "base-date")]
        (when (not= selected (get-in @state [:base-date 0]))
          (om/update! state :throbber ["visible"])
          (<! (load-data! state selected))))
      (doall
        (map-indexed (fn [idx descr]
                       (when-let [selected (get params (:id descr))]
                         (when (not= selected (get-in @state [:filter-options idx :selected]))
                           (om/update! state [:filter-options idx :selected] selected))))
                     (:filter-options @state))))))

(defn update-query-string-from-state [state]
  (let [params {}
        params (if-let [selected (get-in @state [:sort-options :selected])]
                     (assoc params "sort" selected)
                     params)
        params (if-let [selected (get-in @state [:base-date 0])]
                     (assoc params "base-date" selected)
                     params)
        params (into params (for [descr (:filter-options @state)]
                              {(get descr :id) (get descr :selected)}))]
    (edit-query-string params)))

(defn load-config! [app]
  (go
    (if-let [filename (get (query-string->params) "config")]
      (let [data (<! (GET filename))
            config (try
                     (keywordize-keys (js->clj (.parse js/JSON data)))
                     (catch js/Error e
                       (om/update! app :severe-error [e])
                       nil))]
        (when (seq config)
          ; configurations are merged with replacement on top of each other:
          ; app-state -> loaded-config -> query-string

          (om/transact! app (fn [state] (-> state (merge config))))
          (<! (update-state-from-query-string! app))
          (when-not (get-in @app [:base-date 0])
            (<! (load-data! app (-> (js/Date.) trimmed-date-str))))
          (update-query-string-from-state app)
          (aset js/window "onpopstate" (fn [e]
                                       (let [query (query-string)]
                                         (update-state-from-query-string! app))))))
      (om/update! app :severe-error ["Configuration file is missing!"]))))
