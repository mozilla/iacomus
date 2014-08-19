(ns dashgen.core
  (:require-macros  [cljs.core.async.macros :refer [go alt!]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer  [put! chan <!]]
            [goog.net.XhrIo :as xhr]
            [clojure.set]
            [dashgen.utils :refer [GET yyyymmdd get-dates index-of
                                   sort-data! load-data! load-csv load-config!
                                   edit-query-string get-prev-week get-next-week trimmed-date-str]]
            [dashgen.grid :refer [grid-widget]]))

(def app-state
  (atom
    {:throbber ["visible"]
     :state ["loading"]}))

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
                        :onChange (fn [e]
                                    (let [selected (.. e -target -value)]
                                      (edit-query-string {id selected})))}
                   (om/build-all select-option values))))

(defn sort-select [throbber {:keys [values file-prefixes selected] :as data}]
  (apply dom/select
         #js {:value selected
              :disabled (throbber-status throbber)
              :onChange (fn [e]
                          (let [selected (.. e -target -value)]
                            (edit-query-string {"sort" selected})))}
         (om/build-all select-option values)))

(defn filters-sorter-widget [{:keys [filter-options sort-options throbber]} owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/div #js {:className "col-md-12 text-center"}
             (dom/strong nil "Sort by: ")
             (sort-select throbber sort-options)
             (map (partial filter-select throbber) filter-options)))))

(defn date-selector-widget [{:keys [base-date throbber]} owner]
  (reify
    om/IRender
    (render [this]
      (dom/div #js {:className "col-md-12 text-center"}
               (dom/button #js {:className "btn btn-default"
                                :disabled (throbber-status throbber)
                                :onClick (fn [e]
                                           (let [current-week (js/Date. (get @base-date 0))
                                                 prev-week (get-prev-week current-week)]
                                             (edit-query-string {"base-date" (trimmed-date-str prev-week)})))}
                           "<-- Prev Week")
               (dom/img #js {:id "loading-indicator"
                             :src "images/loading.gif"
                             :style #js {:visibility (first throbber)}})
               (dom/button #js {:className "btn btn-default"
                                :disabled (throbber-status throbber)
                                :onClick (fn [e]
                                           (let [current-week (js/Date. (get @base-date 0))
                                                 next-week (get-next-week current-week)]
                                             (edit-query-string {"base-date" (trimmed-date-str next-week)})))}
                           "Next Week -->")))))

(defn header-title-widget [[title subtitle] owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "page-header"}
               (dom/h1 nil
                       (str title " ")
                       (dom/small nil subtitle))))))

(defn body-toolbar-widget [{:keys [base-date throbber]} owner]
  (reify
    om/IRender
    (render [this]
      (dom/div #js {:className "row"}
               (om/build date-selector-widget
                         {:base-date base-date
                          :throbber throbber})))))

(defn header-toolbar-widget [{:keys [sort-options filter-options throbber]} owner]
  (reify
    om/IRender
    (render [this]
      (dom/div #js {:className "row"}
               (om/build filters-sorter-widget
                         {:sort-options sort-options
                          :filter-options filter-options
                          :throbber throbber})))))

(defn error-layout [message]
  (dom/div #js {:className "row text-center"}
           (dom/div #js {:className "col-md-12"}
                    (dom/img #js {:src "images/monkey.gif"})
                    (dom/h1 #js {:className "text-danger"} "Whoops!")
                    (dom/h3 nil "The configuration file is either missing or invalid!")
                    (dom/h5 nil
                            "Use the config parameter to specify it, e.g.: "
                            (dom/code nil "index.html?config=http://yourconfig.json"))
                    (dom/h5 nil (str message)))))

(defn loading-layout []
  (dom/div #js {:className "row text-center"}
           (dom/div #js {:className "col-md-12"}
                    (dom/img #js {:src "images/monkey.gif"})
                    (dom/h1 #js {:className "text-danger"} "Loading..."))))

(defn navbar-widget [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go
        (<! (load-config! app))
        (om/update! app [:state 0] "loaded")))

    om/IRender
    (render [this]
      (let [state (get-in app [:state 0])
            error (get-in app [:severe-error 0])]
        (cond
          (= state "loading")
          (dom/div #js {:className "container"}
                   (loading-layout))

          error
          (dom/div #js {:className "container"}
                   (error-layout error))

          :else
          (dom/div #js {:className "container"}
                   (om/build header-title-widget
                             (:title app))
                   (om/build header-toolbar-widget
                             {:filter-options (:filter-options app)
                              :sort-options (:sort-options app)
                              :throbber (:throbber app)})
                   (dom/div #js {:className "row"} (dom/hr nil))
                   (om/build body-toolbar-widget
                             {:base-date (:base-date app)
                              :throbber (:throbber app)})
                   (om/build grid-widget {:current-week (:current-week app)
                                          :past-week (:past-week app)
                                          :filter-options (:filter-options app)
                                          :sort-options (:sort-options app)
                                          :primary-key (:primary-key app)
                                          :header (:header app)
                                          :base-date (:base-date app)}))))
      )))

(om/root navbar-widget app-state {:target (. js/document (getElementById "app"))})

