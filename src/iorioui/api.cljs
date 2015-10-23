(ns iorioui.api
  (:require
    [iorioui.bus :as bus]
    [cljs-http.client :as http]))

(defn on-users-response [{:keys [body]}]
  (let [users (:users body)
        users-list (mapv (fn [[k {:keys [groups]}]]
                           {:name (name k) :groups groups}) users)]
    (bus/dispatch :new-users users-list)))

(defn on-user-response [{:keys [body]}]
  (bus/dispatch :new-user (update (:user body) :groups set)))

(defn on-group-response [{:keys [body]}]
  (bus/dispatch :new-group (update (:group body) :groups set)))

(defn on-groups-response [{:keys [body]}]
  (let [groups (:groups body)
        groups-list (mapv (fn [[k {:keys [groups]}]]
                           {:name (name k) :groups groups}) groups)]
    (bus/dispatch :new-groups groups-list)))

(defn on-permissions-response [{:keys [body]}]
  (let [perms-str (into {} (map (fn [[k v]] [(name k) v]) body))]
    (bus/dispatch :new-permissions perms-str)))

(defn on-other-status [name]
  (fn  [{:keys [status body]}]
    (js/alert (str "Error loading " name))
    (.warn js/console "got status" status (str body))))

(defn on-create-error [name]
  (fn  [{:keys [status body]}]
    (js/alert (str "Error creating " name))
    (.warn js/console "got status" status (str body))))

(defn with-status [expected-status on-status other-status]
  (fn [event {:keys [status] :as response}]
    (bus/dispatch :api-loading-change false)
    (if (= status expected-status)
      (on-status response)
      (when other-status (other-status response)))))

(defn api-path [parts]
  (clojure.string/join "/" (concat [iorioui.state/api-base] (map name parts))))

(defn http-get [path-parts token]
  (http/get (api-path path-parts)
            {:headers {"x-session" token "accepts" "application/json"}}))

(defn http-post [path-parts token body]
  (http/post (api-path path-parts)
             {:json-params body :headers {"x-session" token}}))

(defn load-users [token]
  (bus/dispatch-req :users-response (http-get [:users] token)))

(defn load-user [token username]
  (bus/dispatch-req :user-response (http-get [:user username] token)))

(defn load-group [token group]
  (bus/dispatch-req :group-response (http-get [:group group] token)))

(defn load-groups [token]
  (bus/dispatch-req :groups-response (http-get [:groups] token)))

(defn load-permissions [token]
  (bus/dispatch-req :permissions-response (http-get [:permissions] token)))

(defn create-user [token user]
  (bus/dispatch-req :user-create-response (http-post [:users] token user)))

(defn create-group [token group]
  (bus/dispatch-req :group-create-response (http-post [:groups] token group)))

(defn on-user-create-response [response]
  (bus/dispatch :user-created {:response response}))

(defn on-group-create-response [response]
  (bus/dispatch :group-created {:response response}))

(defn subscribe-all []
  (bus/subscribe :users-response (with-status 200 on-users-response
                                   (on-other-status "users")))

  (bus/subscribe :user-response (with-status 200 on-user-response
                                  (on-other-status "user")))

  (bus/subscribe :user-create-response (with-status 201
                                         on-user-create-response
                                         (on-create-error "user")))

  (bus/subscribe :group-create-response (with-status 201
                                          on-group-create-response
                                          (on-create-error "group")))

  (bus/subscribe :group-response (with-status 200 on-group-response
                                   (on-other-status "group")))

  (bus/subscribe :groups-response (with-status 200 on-groups-response
                                    (on-other-status "groups")))

  (bus/subscribe :permissions-response (with-status 200 
                                         on-permissions-response
                                         (on-other-status "permissions"))))
