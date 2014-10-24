(ns dashgen.grid
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [dashgen.utils :refer [get-dates header-to-index-map multi-nth
                                   filter-map index-of pretty-date]]))

(defn filter-tuples [filters headers]
  (->> filters
       (filter (fn [{:keys [selected id]}]
                 (cond
                   (= id "Limit") false
                   (= selected "") false
                   :else true)))
       (map (fn [{:keys [selected id]}]
              [(index-of id headers) selected]))))

(defn filter-rows [header rows filters]
  (let [filter-map (filter-map filters)
        tuples (vec (filter-tuples filters header))
        limit (:selected (get filter-map "Limit" 10))
        filter-row? (fn [row index] ;ugly SoB needed to improve performances
                      (loop [cnt 0]
                        (if (< cnt (count tuples))
                          (let [[index selected] (get tuples cnt)]
                            (if (not= (aget row index) selected)
                              false
                              (recur (+ cnt 1))))
                          true)))]
    (take limit (filter filter-row? rows))))

(defn get-rank-mapping [primary-key header rows]
  (let [header-map (header-to-index-map header)
        pk-indices (map header-map primary-key)]
    (zipmap (map #(multi-nth %1 pk-indices) rows) (iterate inc 0))))

(defn rank-diff [rows header primary-key ref-ranks]
  (let [header-map (header-to-index-map header)
        pk-indices (map header-map primary-key)
        rank (fn [base-rank row]
               (let [pk-value (multi-nth row pk-indices)
                     ref-rank (get ref-ranks pk-value)]
                 (cond
                   (= ref-rank nil) "warning"
                   (< base-rank ref-rank) "success"
                   (> base-rank ref-rank) "danger"
                   :else "")))]
    (map-indexed rank rows)))

(defn date-label [date-key base-date]
  (let [base-date (get base-date 0)
        dates (-> base-date get-dates date-key)]
    (dom/div #js {:className "row"}
             (dom/div #js {:className "col-md-12"}
                      (dom/h4 #js {:className "text-center"}
                              (str "Data for " (pretty-date (get dates 0))))))))

(defn legend [] (dom/div #js {:className "row text-center"}
                         (dom/span #js {:className "text-success"} "Moved up in rank  ")
                         (dom/span #js {:className "text-danger"} "Moved down in rank  ")
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

(defn grid-widget [{:keys [current-week past-week sort-options filter-options primary-key header base-date] :as input} owner]
  (reify
    om/IRender
    (render [this]
      (let [current-filtered-rows (filter-rows header current-week filter-options)
            past-filtered-rows (filter-rows header past-week filter-options)
            past-ranking (get-rank-mapping primary-key header past-filtered-rows)
            ranking-diff (rank-diff current-filtered-rows header primary-key past-ranking)]
        (dom/div nil
                 (date-label :current-week base-date)
                 (legend)
                 (dom/table #js {:className "table table-hover table-condensed table-responsive"}
                            (grid-header header)
                            (grid-body current-filtered-rows header ranking-diff))
                 (date-label :past-week base-date)
                 (dom/table #js {:className "table table-hover table-condensed table-responsive"}
                            (grid-header header)
                            (grid-body past-filtered-rows header)))))))
