(ns ui.time-node
  (:require [cljs-time.format :as t-format]
            [cljs-time.core :as t]
            [ui.date-time-picker :refer [DateTimePicker]]
            ["react-native" :as rn]
            [data.queries :as queries]
            [datascript.core :as ds]))

(defn node-style
  [nesting-depth]
  {:align-content :center :width "100%" :height 40 :margin-left (* 15 nesting-depth)})

(def standard-time-format
  (t-format/formatter "MMM' 'dd', 'YYYY"))

(defn time-node-content
  [ds-conn {:keys [:db/id primary-sub-node sub-nodes start-time end-time on-time-start on-time-end] :as node-data} nesting-depth
   & {:keys [style-override]}]
  [:> rn/Text {:key id :style (merge (node-style nesting-depth) style-override)}
   (str "Time Value: "
        (t-format/unparse standard-time-format start-time))])

(def modal-style {:width "80vw" :height "80vh"
                  :background-color :white :opacity 1 :color :black
                  :text-align :center :font-size 20 :font-weight :bold})

(defn get-node-id-by-entity-type
  [{:keys [entity-type] :as node-data}]
  (if (= entity-type "new node")
    (or (:node-ref node-data) (ds/tempid :db/id))
    (:db/id node-data)))

(defn time-node-modal
  [ds-conn {:keys [entity-type start-time] :as node-data}]
  (let [node-id (get-node-id-by-entity-type node-data)]
    [:> rn/View {:style modal-style}
     [:> rn/Text "Time node"]
     [:> rn/Text (str "Start time: " start-time)]
     [:> DateTimePicker {:date-value (or start-time (t/now))
                         :on-change (fn [new-date]
                                      (queries/update-node-data ds-conn {:db/id node-id :start-time new-date})
                                      (queries/toggle-modal ds-conn))}]]))
