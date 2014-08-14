(ns dashgen.core
  (:require-macros  [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer  [put! chan <!]]
            [goog.net.XhrIo :as xhr]
            [clojure.set]
            [dashgen.utils :refer [GET yyyymmdd get-dates index-of
                                   sort-data! load-data! load-csv load-config!]]
            [dashgen.grid :refer [grid-widget]]))


(def app-state
  (atom
    {:date-offset [0]
     :throbber ["visible"]}))

(defn throbber-status [throbber]
  (if (= (first throbber) "visible")
    "disabled"
    ""))

(defn select-option [option]
  (dom/option nil option))

(defn filter-select [throbber {:keys [id values file-prefixes selected] :as data}]
  (dom/span nil
            (dom/strong nil (str " " id ": "))
            (apply dom/select
                   #js {:id id
                        :value selected
                        :disabled (throbber-status throbber)
                        :onChange #(om/update! data :selected (.. %1 -target -value))}
                   (om/build-all select-option values))))

(defn sort-select [throbber {:keys [values file-prefixes selected] :as data} event-channel]
  (apply dom/select
         #js {:value selected
              :disabled (throbber-status throbber)
              :onChange (fn [e]
                          (let [selected (.. e -target -value)]
                            (go
                              (om/update! data :selected selected)
                              (put! event-channel [:sort selected]))))}
         (om/build-all select-option values)))

(defn filters-sorter-widget [{:keys [filter-options sort-options throbber]} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-channel] :as state}]
      (apply dom/div #js {:className "col-md-12 text-center"}
             (dom/strong nil "Sort by: ")
             (sort-select throbber sort-options event-channel)
             (map (partial filter-select throbber) filter-options)))))

(defn date-selector-widget [{:keys [date-offset throbber]} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-channel]}]
      (dom/div #js {:className "col-md-12 text-center"}
               (dom/button #js {:className "btn btn-default"
                                :disabled (throbber-status throbber)
                                :onClick (fn [e]
                                           (go
                                             (om/update! throbber ["visible"])
                                             (put! event-channel [:fetch (- (get @date-offset 0) 1)])))}
                           "<-- Prev Week")
               (dom/img #js {:id "loading-indicator"
                             :src "images/loading.gif"
                             :style #js {:visibility (first throbber)}})
               (dom/button #js {:className "btn btn-default"
                                :disabled (throbber-status throbber)
                                :onClick (fn [e]
                                           (go
                                             (om/update! throbber ["visible"])
                                             (put! event-channel [:fetch (+ (get @date-offset 0) 1)])))}
                           "Next Week -->")))))

(defn header-title-widget [[title subtitle] owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "page-header"}
               (dom/h1 nil
                       (str title " ")
                       (dom/small nil subtitle))))))

(defn body-toolbar-widget [{:keys [date-offset throbber]} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-channel]}]
      (dom/div #js {:className "row"}
               (om/build date-selector-widget
                         {:date-offset date-offset
                          :throbber throbber}
                         {:init-state {:event-channel event-channel}})))))

(defn header-toolbar-widget [{:keys [sort-options filter-options throbber]} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-channel]}]
      (dom/div #js {:className "row"}
               (om/build filters-sorter-widget
                         {:sort-options sort-options
                          :filter-options filter-options
                          :throbber throbber}
                         {:init-state {:event-channel event-channel}})))))

(defn error-layout [message]
  (dom/div #js {:className "row"}
           (dom/div #js {:className "col-md-12"}
                    (dom/img #js {:src "images/monkey.gif"})
                    (dom/h1 #js {:className "text-danger"} "Whoops!")
                    (dom/h3 nil "The configuration file is either missing or invalid!")
                    (dom/h5 nil
                            "Use the config parameter to specify it, e.g.: "
                            (dom/code nil "index.html?config=http://yourconfig.json"))
                    (dom/h5 nil (str message)))))

(defn navbar-widget [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:event-channel (chan)})

    om/IWillMount
    (will-mount [_]
      (let [event-channel (om/get-state owner :event-channel)]
        (go (loop []
              (let [[header message] (<! event-channel)]
                (cond
                  (= header :fetch) (<! (load-data! app message))
                  (= header :sort) (sort-data! app)
                  :else (println "Error: Unknown message received"))
                (recur))))
        (go
          (<! (load-config! app))
          (<! (load-data! app 0)))))

    om/IRenderState
    (render-state [this {:keys [event-channel]}]
      (if-let [error (get-in app [:severe-error 0])]
        (dom/div #js {:className "container"}
                 (error-layout error))
        (dom/div #js {:className "container"}
                 (om/build header-title-widget
                           (:title app))
                 (om/build header-toolbar-widget
                           {:filter-options (:filter-options app)
                            :sort-options (:sort-options app)
                            :throbber (:throbber app)}
                           {:init-state {:event-channel event-channel}})
                 (dom/div #js {:className "row"} (dom/hr nil))
                 (om/build body-toolbar-widget
                           {:date-offset (:date-offset app)
                            :throbber (:throbber app)}
                           {:init-state {:event-channel event-channel}})
                 (om/build grid-widget {:current-week (:current-week app)
                                        :past-week (:past-week app)
                                        :filter-options (:filter-options app)
                                        :sort-options (:sort-options app)
                                        :main-header (:main-header app)
                                        :header (:header app)
                                        :date-offset (:date-offset app)}))))))

(om/root navbar-widget app-state {:target (. js/document (getElementById "app"))})
