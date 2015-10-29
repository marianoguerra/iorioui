(ns iorioui.state
  (:require [om.next :as om]))

(def clean-edit-user {:username "" :password "" :groups #{}})
(def clean-edit-group {:groupname "" :groups #{}})
(def clean-edit-grant {:role "" :role-type "group" :bucket "" :key ""
                       :permission nil})
(def clean-edit-login {:username "" :password "" :waiting false})

(def config (js->clj (or (.. js/window -_ioriouicfg) #js {})))
(def api-base (or (get config "apibase") "/admin"))

(defonce app-state
  (atom
    {:ui {:nav-selected :users
          :title "Users"
          :brand-title (or (get config "title") "Admin")
          :loading false
          :edit-user clean-edit-user
          :edit-group clean-edit-group
          :edit-grant clean-edit-grant
          :edit-login clean-edit-login
          :nav-items [{:id :users :content "Users"}
                      {:id :groups :content "Groups"}
                      {:id :permissions :content "Permissions"}]}
     :session {:token nil}
     :user-details nil
     :users-list nil
     :groups-list nil
     :permissions-list nil}))

(defn set-key [state k v]
  (swap! state #(assoc % k v)))

(defn set-in [state k v]
  (swap! state #(assoc-in % k v)))

(defn conj-in [state k v]
  (swap! state #(update-in % k (fn [old] (conj old v)))))

(defn disj-in [state k v]
  (swap! state #(update-in % k (fn [old] (disj old v)))))

(defn get-ui-state []
  (:ui @app-state))

(defn get-token []
  (get-in @app-state [:session :token]))

(defn read-in [state k]
  (let [st @state]
    (if-let [value (get-in st k)]
      {:value value}
      {:value :not-found})))

(defn sort-groups-attr [{:keys [groups] :as obj}]
  (if groups
    (assoc obj :groups (into [] (sort groups)))
    obj))

; sort the :groups attr inside each map, then sort by name
(defn sort-name-and-groups [items]
  (into [] (sort-by :name (map sort-groups-attr items))))

(defmulti read (fn [env key params] key))

(defmethod read :default
  [{:keys [state]} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defmethod read :edit-user [{:keys [state]} _ _]
  (let [{:keys [groups-list user-details ui]} @state
        edit-user (:edit-user ui)]
    {:value
     {:edit-user edit-user
      :groups-list groups-list
      :user-details user-details}}))

(defmethod read :edit-group [{:keys [state]} _ _]
  (let [{:keys [groups-list group-details ui]} @state
        edit-group (:edit-group ui)]
    {:value
     {:edit-group edit-group
      :groups-list groups-list
      :group-details group-details}}))

(defmethod read :edit-grant [{:keys [state]} _ _]
  (let [{:keys [permissions-list ui]} @state
        edit-grant (:edit-grant ui)]
    {:value
     {:edit-grant edit-grant
      :permissions-list (:permissions permissions-list)}}))

(defmethod read :edit-login [{:keys [state]} _ _]
  (let [{:keys [ui]} @state
        edit-login (:edit-login ui)]
    {:value {:edit-login edit-login}}))

(defmethod read :nav-info [{:keys [state]} _ _]
  (let [{:keys [nav-items nav-selected title loading brand-title]} (:ui @state)]
    {:value {:nav-items nav-items :nav-selected nav-selected
             :title title :loading loading :brand-title brand-title}}))

(defn set-group [state path name value]
  (if value
    (conj-in state path name)
    (disj-in state path name)))

(defn mutate-set [changes path {:keys [state]} {:keys [value]}]
  {:value [changes] :action #(set-in state path value)})

(defmulti mutate om/dispatch)

(defmethod mutate 'ui/set-loading [env _ args]
  (mutate-set :ui [:ui :loading] env args))
(defmethod mutate 'session/set [env _ args]
  (mutate-set :session [:session] env args))

(defmethod mutate 'ui.user.edit/set-username [env _ args]
  (mutate-set :edit-user [:ui :edit-user :username] env args))
(defmethod mutate 'ui.user.edit/set-password [env _ args]
  (mutate-set :edit-user [:ui :edit-user :password] env args))
(defmethod mutate 'ui.user.edit/reset [env _ _]
  (mutate-set :edit-user [:ui :edit-user] env {:value clean-edit-user}))
(defmethod mutate 'ui.user.edit/set [env _ args]
  (mutate-set :edit-user [:ui :edit-user] env args))

(defmethod mutate 'ui.user.edit/set-group
  [{:keys [state]} _ {:keys [name value]}]
  {:value [:edit-user]
   :action #(set-group state [:ui :edit-user :groups] name value)})

(defmethod mutate 'ui.group.edit/set-groupname [env _ args]
  (mutate-set :edit-group [:ui :edit-group :groupname] env args))
(defmethod mutate 'ui.group.edit/reset [env _ _]
  (mutate-set :edit-group [:ui :edit-group] env {:value clean-edit-group}))
(defmethod mutate 'ui.group.edit/set [env _ args]
  (mutate-set :edit-group [:ui :edit-group] env args))

(defmethod mutate 'ui.group.edit/set-group
  [{:keys [state]} _ {:keys [name value]}]
  {:value [:edit-group]
   :action #(set-group state [:ui :edit-group :groups] name value)})

(defmethod mutate 'ui.grant.edit.set/role [env _ args]
  (mutate-set :edit-grant [:ui :edit-grant :role] env args))
(defmethod mutate 'ui.grant.edit.set/bucket [env _ args]
  (mutate-set :edit-grant [:ui :edit-grant :bucket] env args))
(defmethod mutate 'ui.grant.edit.set/key [env _ args]
  (mutate-set :edit-grant [:ui :edit-grant :key] env args))
(defmethod mutate 'ui.grant.edit.set/role-type [env _ args]
  (mutate-set :edit-grant [:ui :edit-grant :role-type] env args))
(defmethod mutate 'ui.grant.edit.set/permission [env _ args]
  (mutate-set :edit-grant [:ui :edit-grant :permission] env args))

(defmethod mutate 'ui.login.edit.set/username [env _ args]
  (mutate-set :edit-login [:ui :edit-login :username] env args))
(defmethod mutate 'ui.login.edit.set/password [env _ args]
  (mutate-set :edit-login [:ui :edit-login :password] env args))
(defmethod mutate 'ui.login.edit.set/waiting [env _ args]
  (mutate-set :edit-login [:ui :edit-login :waiting] env args))

(defmethod mutate 'ui.users/set-user-details [env _ args]
  (mutate-set :user-details [:user-details] env args))

(defmethod mutate 'ui.users/set-users [{:keys [state]} _ {:keys [value]}]
  {:value [:users-list]
   :action #(set-key state :users-list (sort-name-and-groups value))})

(defmethod mutate 'ui.groups/set-group-details [env _ args]
  (mutate-set :group-details [:group-details] env args))

(defmethod mutate 'ui.groups/set-groups [{:keys [state]} _ {:keys [value]}]
  {:value [:groups-list]
   :action #(set-key state :groups-list (sort-name-and-groups value))})

(defmethod mutate 'ui.perms/set-perms [env _ args]
  (mutate-set :permissions-list [:permissions-list] env args))

(defmethod mutate 'ui/navigate
  [{:keys [state]} _ {:keys [nav-selected nav-param title loading]}]
  {:value [:nav-info]
   :action #(swap! state (fn [old] (update-in old [:ui] assoc
                                              :nav-selected nav-selected
                                              :nav-param nav-param
                                              :title title
                                              :loading loading)))})

(def reconciler
  (om/reconciler
    {:state app-state :parser (om/parser {:read read :mutate mutate})}))

(defn mutate! [query]
  (om/transact! reconciler query))
