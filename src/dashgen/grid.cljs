(ns dashgen.grid
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [dashgen.utils :refer [get-dates header-to-index-map filter-map index-of pretty-date]]))

(defn filter-tuples [filters headers]
  (map (fn [{:keys [selected id]}] [(index-of id headers) selected]) filters))

(defn filter-rows [header rows filters]
  (let [filter-map (filter-map filters)
        tuples (vec (filter-tuples (butlast filters) header))
        limit (:selected (get filter-map "Limit"))
        filter-row? (fn [row index] ;ugly SoB needed to improve performances
                      (loop [index 0]
                        (if (< index (count tuples))
                          (let [[index selected] (get tuples index)]
                            (if (not= (aget row index) selected)
                              false
                              (recur (+ index 1))))
                          true))
                      )]
    (take (:selected (get filter-map "Limit")) (filter filter-row? rows))))

(defn get-rank-mapping [main-header header rows]
  (let [header-map (header-to-index-map header)
        main-index (get header-map main-header)]
    (zipmap (map #(nth %1 main-index) rows) (iterate inc 0))))

(defn rank-diff [rows header main-header ref-ranks]
  (let [header-map (header-to-index-map header)
        main-index (get header-map main-header)
        rank (fn [base-rank row]
               (let [main-field (get row main-index)
                     ref-rank (get ref-ranks main-field)]
                 (cond
                   (= ref-rank nil) "warning"
                   (< base-rank ref-rank) "success"
                   (> base-rank ref-rank) "danger"
                   :else "")))]
    (map-indexed rank rows)))

(defn date-label [date-key date-offset]
  (let [date-offset (get date-offset 0)
        dates (-> date-offset get-dates date-key)]
    (dom/div #js {:className "row"}
             (dom/div #js {:className "col-md-12"}
                      (dom/h4 #js {:className "text-center"}
                              (str "Data for the week of " (pretty-date (get dates 0)) " to " (pretty-date (get dates 1))))))))

(defn legend [] (dom/div #js {:className "row text-center"}
                         (dom/span #js {:className "text-danger"} "Moved up in rank  ")
                         (dom/span #js {:className "text-success"} "Moved down in rank  ")
                         (dom/span #js {:className "text-warning"} "New")))

(defn grid-header [header]
  (dom/thead nil
             (apply dom/tr nil
                    (map (partial dom/th nil) header))))

(defn grid-body-row [row ranking]
  (apply dom/tr #js {:className ranking}
         (map (partial dom/td nil) row)))

(defn grid-body-row-NA [header]
  (dom/tr nil (dom/td #js {:colSpan (count header)} "No data found with the requested filtering criteria")))

(defn grid-body
  ([rows header] (grid-body rows header (repeat "equal")))
  ([rows header ranking]
   (if (seq rows)
     (apply dom/tbody nil (map grid-body-row rows ranking))
     (dom/tbody nil (grid-body-row-NA header)))))

(defn grid-widget [{:keys [current-week past-week sort-options filter-options main-header header date-offset] :as input} owner]
  (reify
    om/IRender
    (render [this]
      (let [current-filtered-rows (filter-rows header current-week filter-options)
            past-filtered-rows (filter-rows header past-week filter-options)
            past-ranking (get-rank-mapping main-header header past-filtered-rows)]
        (dom/div nil
                 (date-label :current-week date-offset)
                 (legend)
                 (dom/table #js {:className "table table-hover table-condensed table-responsive"}
                            (grid-header header)
                            (grid-body current-filtered-rows
                                       header
                                       (rank-diff current-filtered-rows
                                                  header
                                                  main-header
                                                  past-ranking)))
                 (date-label :past-week date-offset)
                 (dom/table #js {:className "table"}
                            (grid-header header)
                            (grid-body past-filtered-rows header)))))))
