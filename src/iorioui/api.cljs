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
  (let [{:keys [permissions grants]} body
        perms-str (into {} (map (fn [[k v]] [(name k) v]) permissions))]
    (bus/dispatch :new-permissions {:permissions perms-str :grants grants})))

(defn on-action-error [action name]
  (fn  [{:keys [status body]}]
    (js/alert (str "Error " action " " name))
    (.warn js/console "got status" status (str body))))

(defn on-loading-error [name] (on-action-error "loading" name))
(defn on-create-error [name] (on-action-error "creating" name))
(defn on-update-error [name] (on-action-error "updating" name))
(defn on-delete-error [name] (on-action-error "removing" name))

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

(defn http-delete [path-parts token]
  (http/delete (api-path path-parts)
               {:headers {"x-session" token "accepts" "application/json"}}))

(defn http-post [path-parts token body]
  (http/post (api-path path-parts)
             {:json-params body :headers {"x-session" token}}))

(defn http-put [path-parts token body]
  (http/put (api-path path-parts)
             {:json-params body :headers {"x-session" token}}))

(defn load-users [token]
  (bus/dispatch-req :users-response (http-get [:users] token)))

(defn load-user [token username]
  (bus/dispatch-req :user-response (http-get [:users username] token)))

(defn load-group [token group]
  (bus/dispatch-req :group-response (http-get [:groups group] token)))

(defn load-groups [token]
  (bus/dispatch-req :groups-response (http-get [:groups] token)))

(defn load-permissions [token]
  (bus/dispatch-req :permissions-response (http-get [:permissions] token)))

(defn clean-update-user [{:keys [password] :as user}]
  (if (= (clojure.string/trim (or password "")) "")
    (dissoc user :password)
    user))

(defn create-user [token user]
  (bus/dispatch-req :user-create-response (http-post [:users] token user)))

(defn update-user [token {:keys [username] :as user}]
  (bus/dispatch-req :user-update-response (http-put [:users username] token
                                                    (clean-update-user user))))

(defn delete-user [token {:keys [username]}]
  (bus/dispatch-req :user-delete-response
                    (http-delete [:users username] token)))

(defn delete-group [token {:keys [name]}]
  (bus/dispatch-req :group-delete-response
                    (http-delete [:groups name] token)))

(defn create-group [token group]
  (bus/dispatch-req :group-create-response (http-post [:groups] token group)))

(defn update-group [token {:keys [groupname] :as group}]
  (bus/dispatch-req :group-update-response
                    (http-put [:groups groupname] token group)))

(defn grant [token grant]
  (bus/dispatch-req :grant-response (http-post [:grants] token grant)))

(defn revoke [token grant]
  (bus/dispatch-req :revoke-response (http-post [:revokes] token grant)))

(defn dispatch-response [event-type]
  (fn [response] (bus/dispatch event-type {:response response})))

(defn subscribe-all []
  (bus/subscribe :users-response (with-status 200 on-users-response
                                   (on-loading-error "users")))

  (bus/subscribe :user-response (with-status 200 on-user-response
                                  (on-loading-error "user")))

  (bus/subscribe :user-create-response (with-status 201
                                         (dispatch-response :user-created)
                                         (on-create-error "user")))

  (bus/subscribe :user-update-response (with-status 200
                                         (dispatch-response :user-updated)
                                         (on-update-error "user")))

  (bus/subscribe :user-delete-response (with-status 204
                                         (dispatch-response :user-deleted)
                                         (on-delete-error "user")))

  (bus/subscribe :group-create-response (with-status 201
                                          (dispatch-response :group-created)
                                          (on-create-error "group")))

  (bus/subscribe :group-update-response (with-status 200
                                          (dispatch-response :group-updated)
                                          (on-update-error "group")))

  (bus/subscribe :group-delete-response (with-status 204
                                          (dispatch-response :group-deleted)
                                          (on-delete-error "group")))

  (bus/subscribe :group-response (with-status 200 on-group-response
                                   (on-loading-error "group")))

  (bus/subscribe :groups-response (with-status 200 on-groups-response
                                    (on-loading-error "groups")))

  (bus/subscribe :permissions-response (with-status 200 
                                         on-permissions-response
                                         (on-loading-error "permissions"))))
