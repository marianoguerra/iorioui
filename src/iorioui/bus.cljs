(ns iorioui.bus
  (:require
    [cljs.core.async :refer [<! >! put! chan]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

(def dispatch-chan (chan 128))

(defn dispatch [event & args]
  (put! dispatch-chan {:event event :args args})
  nil)

(def dispatch-handlers (atom {}))

(defn subscribe [event callback]
  (swap! dispatch-handlers
         #(update % event (fn [old] (conj old callback)))))

(defn start-dispatch-handler []
  (go (loop []
        (let [{:keys [event args]} (<! dispatch-chan)
              handlers (get @dispatch-handlers event)]
          (doseq [handler handlers]
            (try
              (apply handler event args)
              (catch js/Error error
                (prn "Error calling handler" handler error))))
          (recur)))))

(defn dispatch-req [event-key req]
  (go (let [response (<! req)]
        (dispatch event-key response))))

