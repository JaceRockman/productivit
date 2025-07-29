(ns ui.modal
  (:require ["react-native" :as rn]
            [data.queries :as queries]
            [ui.action-menu-ui :as action-menu]))


(def submit-cancel-button-style {:margin "5%" :flex 1
                                 :background-color "#3498db"
                                 :align-items :center :justify-content :center
                                 :padding 10
                                 :border-radius 8
                                 :elevation 3
                                 :shadow-color "#000"
                                 :shadow-offset {:width 0 :height 2}
                                 :shadow-opacity 0.25
                                 :shadow-radius 3.84})

(defn modal-submit-button
  [ds-conn {:keys [modal-type] :as modal-state} temp-node-data]
  (let [submit-fn (case modal-type
                    "node-creation" #(queries/transact-new-node ds-conn temp-node-data)
                    "node-edit" #(queries/update-node-data ds-conn temp-node-data)
                    #(println "Unknown modal type: " modal-type))]
    [:> rn/Pressable {:style submit-cancel-button-style
                      :on-press submit-fn}
     [:> rn/Text "Submit"]]))

(defn modal-cancel-button
  [ds-conn]
  [:> rn/Pressable {:style submit-cancel-button-style
                    :on-press #(do (queries/reset-node-creation-data ds-conn)
                                   (queries/toggle-modal ds-conn))}
   [:> rn/Text "Cancel"]])

(defn modal-content
  [ds-conn {:keys [modal-type] :as modal-state} temp-node-data]
  (case modal-type
    "node-edit" (action-menu/node-edit-window ds-conn temp-node-data)
    "node-creation" (action-menu/node-creation-window ds-conn temp-node-data)
    [:> rn/Text "Unknown modal type: " modal-type]))

(defn modal-pane
  [ds-conn modal-state temp-node-data]
  [:> rn/Pressable {:style {:background-color :white :width "80vw" :height "80vh"}}
   (modal-content ds-conn modal-state temp-node-data)
   [:> rn/View {:style {:flex 1}}]
   [:> rn/View {:style {:flex-direction :row}}
    (modal-cancel-button ds-conn)
    (modal-submit-button ds-conn modal-state temp-node-data)]])

(defn modal-background
  [ds-conn modal-content]
  [:> rn/Pressable {:style {:position :absolute :top 0 :left 0 :right 0 :bottom 0
                            :background-color "rgba(0, 0, 0, 0.5)"
                            :align-items :center :justify-content :center}
                    :on-press #(queries/toggle-modal ds-conn)}
   modal-content])

(def example-modal-state
  {:modal-type "node-creation"
   :display true
   :valid-input? true})

(def example-temp-node-data
  {:node-type "text"
   :parent-id 1
   :node-ref 5
   :text-value "Hello, world!"})

(defn modal-component
  [ds-conn]
  (let [{:keys [display] :as modal-state} (queries/get-modal-state ds-conn)
        temp-node-data (queries/get-temp-node-data ds-conn)]
    [:> rn/Modal {:visible display
                  :transparent true
                  :on-request-close #(queries/toggle-modal ds-conn)}
     (modal-background ds-conn
                       (modal-pane ds-conn modal-state temp-node-data))]))
