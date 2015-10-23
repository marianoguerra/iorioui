(ns iorioui.bs
  (:require [om.dom :as dom]))

(defn navbar [title]
  (dom/nav #js {:className "navbar navbar-inverse navbar-fixed-top"}
           (dom/div #js {:className "navbar-header"}
                    (dom/a #js {:className "navbar-brand" :href "#"}
                           title))))

(defn col-class [props]
  (let [{:keys [sm md lg sm-offset md-offset lg-offset class]} props]
    (str (when sm (str "col-sm-" sm))
         (when md (str " col-md-" md))
         (when lg (str " col-lg-" lg))
         (when sm-offset (str " col-sm-offset-" sm-offset))
         (when md-offset (str " col-md-offset-" md-offset))
         (when lg-offset (str " col-lg-offset-" lg-offset))
         (when class (str " " class)))))

(defn col [props & childs]
  (dom/div #js {:className (col-class props)} childs))

(defn sidebar-link [{:keys [active url content onClick]}]
  (dom/li #js {:className (if active "active" "") :onClick onClick}
          (dom/a #js {:href (if url url "#")}
                 content)))

(defn sidebar [items]
  (col {:sm 3 :md 2 :class "sidebar"}
          (dom/ul #js {:className "nav nav-sidebar"}
                  (map sidebar-link items))))

(defn page-header [title]
  (dom/h1 #js {:className "page-header"} title))

(defn page [title & content]
  (col {:sm 9 :sm-offset 3 :md 10 :md-offset 2 :class "main"}
          (page-header title)
          content))

(defn container-fluid [& items]
  (dom/div #js {:className "contaner-fluid"} items))

(defn row [& items]
  (dom/div #js {:className "row"} items))

(defn table [columns rows]
  (dom/div #js {:className "table-responsive"}
           (dom/table #js {:className "table table-striped"}
                      (dom/thead nil
                                 (dom/tr nil (map #(dom/th nil %) columns)))
                      (dom/tbody nil
                                 (map-indexed (fn [idx {:keys [key cols]}] 
                                        (dom/tr #js {:key key}
                                                (map #(dom/td nil %) cols)))
                                              rows)))))
(defn form [& items]
    (dom/form nil items))

(defn on-change-cb [callback]
  (when callback
    (fn [e]
      (let [target (.-target e)
            value (.-value target)]
        (callback value)))))

(defn on-toggle-cb [callback]
  (when callback
    (fn [e]
      (let [target (.-target e)
            value (.-checked target)]
        (callback value)))))

(defn on-click-cb [callback]
  (when callback (fn [e] (callback))))

(defn form-input [& {:keys [id label input-type placeholder on-change value
                            readonly]}]
    (dom/div #js {:className "form-group"}
             (dom/label #js {:htmlFor id} label)
             (dom/input #js {:type input-type :className "form-control"
                             :id id :placeholder placeholder :value value
                             :readOnly readonly
                             :onChange (when-not readonly
                                         (on-change-cb on-change))})))

(defn form-check [& {:keys [label value on-change]}]
  (dom/div #js {:className "checkbox"}
           (dom/label nil (dom/input #js {:type "checkbox"
                                          :checked value
                                          :onChange (on-toggle-cb on-change)}
                                     label))))

(defn button [& {:keys [type label level on-click]}]
  (dom/button #js {:type (or type "button")
                   :className (str "btn btn-" (name level))
                   :onClick (on-click-cb on-click)} label))

(defn data-list [& {:keys [id values]}]
  (dom/datalist #js {:id id}
                     (map (fn [{:keys [value label]}]
                               (dom/option #js {:value value :label label})))))

(defn on-slider-change-cb [on-change values]
  (when on-change
    (on-change-cb
      #(let [idx (js/parseInt % 10)
             i (dec idx)
             data (nth values i)]
         (on-change {:i i :idx idx :data data})))))

(defn slider [& {:keys [id value min max values values-id on-change]}]
  (let [values-id (or values-id (str id "-values"))]
    (dom/div nil
             (dom/input #js {:type "range" :value value :min 1 :list values-id
                             :max (count values) :id id :step 1
                             :onChange (on-slider-change-cb on-change values)})
             (data-list :id values-id values))))
