(ns iorioui.state
  (:require [om.next :as om]))

(def clean-edit-user {:username "" :password "" :groups #{"g-authenticated"}})
(def clean-edit-group {:groupname "" :groups #{}})

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
          :nav-items [{:id :users :content "Users"}
                      {:id :groups :content "Groups"}
                      {:id :permissions :content "Permissions"}]}
     :user-details nil
     :users-list nil
     :groups-list nil
     :permissions-list {"iorio" ["put" "get" "delete"]}}))

(defn set-key [state k v]
  (swap! state #(assoc % k v)))

(defn set-in [state k v]
  (swap! state #(assoc-in % k v)))

(defn conj-in [state k v]
  (swap! state #(update-in % k (fn [old] (conj old v)))))

(defn disj-in [state k v]
  (swap! state #(update-in % k (fn [old] (disj old v)))))

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

(defmethod read :nav-info [{:keys [state]} _ _]
  (let [{:keys [nav-items nav-selected title loading brand-title]} (:ui @state)]
    {:value {:nav-items nav-items :nav-selected nav-selected
             :title title :loading loading :brand-title brand-title}}))

(defn set-group [state path name value]
  (if value
    (conj-in state path name)
    (disj-in state path name)))

(defmulti mutate om/dispatch)

(defmethod mutate 'ui/set-loading [{:keys [state]} _ {:keys [value]}]
  {:value [:ui]
   :action #(set-in state [:ui :loading] value)})

(defmethod mutate 'ui.user.edit/set-username
  [{:keys [state]} _ {:keys [value]}]
  {:value [:edit-user]
   :action #(set-in state [:ui :edit-user :username] value)})

(defmethod mutate 'ui.user.edit/set-password
  [{:keys [state]} _ {:keys [value]}]
  {:value [:edit-user]
   :action #(set-in state [:ui :edit-user :password] value)})

(defmethod mutate 'ui.user.edit/reset [{:keys [state]} _ _]
  {:value [:edit-user]
   :action #(set-in state [:ui :edit-user] clean-edit-user)})

(defmethod mutate 'ui.user.edit/set [{:keys [state]} _ {:keys [value]}]
  {:value [:edit-user]
   :action #(set-in state [:ui :edit-user] value)})

(defmethod mutate 'ui.user.edit/set-group
  [{:keys [state]} _ {:keys [name value]}]
  {:value [:edit-user]
   :action #(set-group state [:ui :edit-user :groups] name value)})

(defmethod mutate 'ui.group.edit/set-groupname
  [{:keys [state]} _ {:keys [value]}]
  {:value [:edit-group]
   :action #(set-in state [:ui :edit-group :groupname] value)})

(defmethod mutate 'ui.group.edit/reset [{:keys [state]} _ {:keys [value]}]
  {:value [:edit-group]
   :action #(set-in state [:ui :edit-group] clean-edit-group)})

(defmethod mutate 'ui.group.edit/set [{:keys [state]} _ {:keys [value]}]
  {:value [:edit-group]
   :action #(set-in state [:ui :edit-group] value)})

(defmethod mutate 'ui.group.edit/set-group
  [{:keys [state]} _ {:keys [name value]}]
  {:value [:edit-group]
   :action #(set-group state [:ui :edit-group :groups] name value)})

(defmethod mutate 'ui.users/set-user-details
  [{:keys [state]} _ {:keys [value]}]
  {:value [:user-details]
   :action #(set-key state :user-details value)})

(defmethod mutate 'ui.users/set-users [{:keys [state]} _ {:keys [value]}]
  {:value [:users-list]
   :action #(set-key state :users-list (sort-name-and-groups value))})

(defmethod mutate 'ui.groups/set-group-details
  [{:keys [state]} _ {:keys [value]}]
  {:value [:group-details]
   :action #(set-key state :group-details value)})

(defmethod mutate 'ui.groups/set-groups [{:keys [state]} _ {:keys [value]}]
  {:value [:groups-list]
   :action #(set-key state :groups-list (sort-name-and-groups value))})

(defmethod mutate 'ui.perms/set-perms [{:keys [state]} _ {:keys [value]}]
  {:value [:permissions-list]
   :action #(set-key state :permissions-list value)})

(defmethod mutate 'ui/navigate
  [{:keys [state]} _ {:keys [nav-selected title loading]}]
  {:value [:nav-info]
   :action #(swap! state (fn [old] (update-in old [:ui] assoc
                                              :nav-selected nav-selected
                                              :title title
                                              :loading loading)))})

(def reconciler
  (om/reconciler
    {:state app-state
     :parser (om/parser {:read read :mutate mutate})}))

(defn mutate! [query]
  (om/transact! reconciler query))
