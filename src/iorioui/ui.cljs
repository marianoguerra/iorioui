(ns iorioui.ui
  (:require
    [iorioui.bs :as bs]
    [iorioui.bus :as bus]
    [iorioui.api :as api]
    [iorioui.state :as st]
    [goog.dom :as gdom]
    [om.next :as om :refer-macros [defui]]
    [om.dom :as dom]
    [cljs.core.async :refer [<! >! put! chan]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(defn dv [& items]
  (apply dom/div nil items))

(defn nav-mark-selected [items selected]
  (map (fn [{:keys [id content] :as item}]
         (let [active (= id selected)]
               (assoc item :active active
                      :onClick #(bus/dispatch :nav-click
                                              {:id id :title content}))))
       items))

(defn user-link [name]
  (dom/a #js {:href "#" :onClick #(bus/dispatch :user-selected {:name name})}
         name))

(defn group-link [name]
  (dom/a #js {:href "#" :onClick #(bus/dispatch :group-selected {:name name})}
         name))

(defn perm-link [app name]
  (dom/a #js {:href "#"
              :onClick #(bus/dispatch :perm-selected {:app app :name name})}
         name))

(defn group-links [groups]
  (dv (map (fn [group] (dv (group-link group) " ")) groups)))

(defn perm-links [app perms]
  (dv (map (fn [perm] (dv (perm-link app perm) " ")) perms)))

(defn action-button [label type data & [button-type]]
  (dom/div #js {:className "buttons"}
           (bs/button :level (or button-type :primary) :label label
                      :on-click #(bus/dispatch type data))))

(defn group-selector [selected all-groups event-type]
  (map (fn [{group-name :name}]
         (bs/form-check :label group-name
                        :value (contains? selected group-name)
                        :on-change #(bus/dispatch
                                      event-type {:name group-name :value %})))
       all-groups))

(defn edit-user-form [{:keys [username password groups] :as user}
                      groups-list
                      {:keys [title action-label action-type update?]}]
  (dv
    (dom/h2 nil title)
    (bs/form
      (bs/form-input :id "user-username" :label "Username" :value username
                     :readonly update?
                     :on-change #(bus/dispatch :edit-user-set-username %))
      (bs/form-input :id "user-password" :label "Password"
                     :input-type "password" :value password
                     :on-change #(bus/dispatch :edit-user-set-password %))
      (group-selector groups groups-list :edit-user-set-group)
      (action-button action-label action-type user))))

(defui EditUser
  static om/IQuery
  (query [this] '[(:edit-user)])
  Object
  (render [this]
          (let [{:keys [edit-user groups-list opts]} (om/props this)]
            (edit-user-form edit-user groups-list opts))))

(def edit-user-ui (om/factory EditUser))

(defn grants-details [grants-list]
  (dom/div #js {:className "grants-details"}
           (bs/table ["Bucket" "Key" "Grants" "Bucket Grant" "Any Grant"]
           (map (fn [{:keys [grants bucket key bucket_grant any]}]
                  {:key (str bucket "." key)
                   :cols [bucket key (clojure.string/join ", " grants)
                                    (str bucket_grant) (str any)]})
                grants-list))))

(defn user-ui [{:keys [username groups grants] :as user-details} edit-user groups-list]
  (dom/div #js {:className "user-details"}
           (bs/table ["", ""]
                     [{:key "username" :cols ["Username" username]}
                      {:key "groups" :cols ["Groups" (group-links groups)]}])

           (action-button "Delete User" :delete-user user-details :danger)
           (dom/h3 nil "Grants")
           (grants-details grants)

           (edit-user-ui {:edit-user edit-user
                          :opts {:title "Edit User"
                                 :update? true
                                 :action-label "Update"
                                 :action-type :update-user}
                          :groups-list groups-list})))

(defui UserDetails
  static om/IQuery
  (query [this] '[(:edit-user)])
  Object
  (render [this]
          (let [{:keys [edit-user groups-list user-details]} (om/props this)]
            (user-ui user-details edit-user groups-list))))

(def user-details (om/factory UserDetails))


(defn edit-group-form [{:keys [groupname groups] :as group} groups-list]
  (bs/form
    (bs/form-input :id "group-groupname" :label "Name" :value groupname
                   :on-change #(bus/dispatch :edit-group-set-groupname %))
    (group-selector groups groups-list :edit-group-set-group)
    (action-button "Create" :create-group group)))

(defui CreateGroup
  static om/IQuery
  (query [this] '[(:edit-group)])
  Object
  (render [this]
          (let [{:keys [edit-group groups-list]} (om/props this)]
            (when (nil? groups-list)
              (bus/dispatch :reload-groups {:source :edit-group}))

            (dv (dom/h2 nil "Create Group")
                (edit-group-form edit-group groups-list)))))

(def edit-group-ui (om/factory CreateGroup))

(defn users-ui [items user-data]
  (dv
    (bs/table ["Username", "Groups"]
              (map (fn [{:keys [name groups]}]
                     {:key name :cols [(user-link name) (group-links groups)]})
                   items))
    (edit-user-ui
      (assoc user-data :opts {:title "Create User"
                              :action-label "Create"
                              :action-type :create-user}))))

(defn groups-ui [items group-data]
  (dv
    (bs/table ["Group", "Groups"]
              (map (fn [{:keys [name groups]}]
                     {:key name
                      :cols [(group-link name) (group-links groups)]}) items))

    (edit-group-ui group-data)))

(defn group-ui [{:keys [name groups direct_grants]}]
  (dom/div #js {:className "group-details"}
           (bs/table ["", ""]
                     [{:key "name" :cols ["Name" name]}
                      {:key "groups" :cols ["Groups" (group-links groups)]}])

           (if (empty? direct_grants)
             (dom/h3 nil "No direct grants")
             (dv (dom/h3 nil "Direct Grants")
                 (grants-details direct_grants)))))

(defn perms-ui [items]
  (bs/table ["App", "Permissions"]
            (map (fn [[name perms]]
                   {:key name
                    :cols [name (perm-links name perms)]})
                 items)))

(defn main-panel [nav-selected {:keys [users-list edit-user
                                       group-details groups-list edit-group
                                       permissions-list]}]
  (when (nil? users-list) (bus/dispatch :reload-users {:source :users-ui}))
  (when (nil? groups-list) (bus/dispatch :reload-groups {:source :edit-user}))

  (dv (condp = nav-selected
        :users (users-ui users-list edit-user)
        :user (user-details edit-user)
        :groups (groups-ui groups-list edit-group)
        :group (group-ui group-details)
        :permissions (perms-ui permissions-list)
        (.warn js/console "invalid nav " (str nav-selected)))))

(defn loading-sign []
  (dom/p #js {:className "bg-success"
              :style #js {:padding "1em" :textAlign "center"}} "Loading"))

(defui App
  static om/IQuery
  (query [this]
         (let [edit-user-query (first (om/get-query EditUser))
               edit-group-query (first (om/get-query CreateGroup))
               user-details-query (first (om/get-query UserDetails))]
           `[:ui
             ~edit-user-query
             ~edit-group-query
             ~user-details-query
             (:nav-info)
             (:users-list)
             (:group-details) (:groups-list)
             (:permissions-list)]))
  Object
  (render [this]
          (let [{:keys [ui nav-info] :as data} (om/props this)
                {:keys [nav-items nav-selected title loading brand-title]}
                nav-info]
            (bs/container-fluid
              (bs/row
                (bs/navbar brand-title)
                (bs/sidebar (nav-mark-selected nav-items nav-selected))
                (bs/page title
                         (if loading
                           (loading-sign)
                           (main-panel nav-selected data))))))))


(defn navigate [id title]
  (st/mutate!
    `[(ui/navigate {:nav-selected ~id :title ~title :loading true}) :ui]))

(defn get-token []
  ; TODO
  "atoken")

(defn update-view [view & [param1]]
  (let [token (get-token)]
    (condp = view
      :users (api/load-users token)
      :groups (api/load-groups token)
      :permissions (api/load-permissions token)
      :user (api/load-user token param1)
      :group (api/load-group token param1)
      (.warn js/console "invalid view" (name view)))))

(defn on-user-changed []
  (api/load-users (get-token))
  (st/mutate! `[(ui.user.edit/reset) :ui]))

(defn subscribe-all []
  (bus/unsubscribe-all)
  (bus/stop-dispatch-handler)

  (bus/start-dispatch-handler)
  (api/subscribe-all)

  (bus/subscribe :nav-click (fn [event {:keys [id title]}]
                              (update-view id)
                              (navigate id title)))

  (bus/subscribe :user-selected (fn [_ {:keys [name]}]
                                  (update-view :user name)
                                  (navigate :user (str "User " name))))

  (bus/subscribe :group-selected (fn [_ {:keys [name]}]
                                   (update-view :group name)
                                   (navigate :group (str "Group " name))))
  (bus/subscribe :reload-groups (fn [_ _] (api/load-groups (get-token))))
  (bus/subscribe :reload-groups (fn [_ _] (api/load-users (get-token))))

  (bus/subscribe :create-user (fn [_ user] (api/create-user (get-token) user)))
  (bus/subscribe :user-created (fn [_ _] (on-user-changed)))

  (bus/subscribe :update-user (fn [_ user] (api/update-user (get-token) user)))
  (bus/subscribe :delete-user (fn [_ user] (api/delete-user (get-token) user)))
  (bus/subscribe :user-updated (fn [_ _] (on-user-changed)))
  (bus/subscribe :user-deleted (fn [_ _]
                                 (on-user-changed)
                                 (navigate :users "Users")))

  (bus/subscribe :create-group (fn [_ group]
                                (api/create-group (get-token) group)))

  (bus/subscribe :group-created (fn [_ group]
                                 (api/load-groups (get-token))
                                 (st/mutate! `[(ui.group.edit/reset) :ui])))

  (bus/subscribe :api-loading-change
                 #(st/mutate! `[(ui/set-loading {:value ~%2}) :ui]))

  (bus/subscribe :edit-user-set-username
                 #(st/mutate! `[(ui.user.edit/set-username
                                  {:value ~%2}) :ui])) ; TODO
  (bus/subscribe :edit-user-set-password
                 #(st/mutate! `[(ui.user.edit/set-password
                                  {:value ~%2}) :ui])) ; TODO
  (bus/subscribe :edit-user-set-group
                 #(st/mutate! `[(ui.user.edit/set-group ~%2) :ui]))

  (bus/subscribe :edit-group-set-groupname
                 #(st/mutate! `[(ui.group.edit/set-groupname
                                  {:value ~%2}) :ui])) ; TODO

  (bus/subscribe :edit-group-set-group
                 #(st/mutate! `[(ui.group.edit/set-group ~%2) :ui])) ; TODO

  (bus/subscribe :new-user
                 (fn [_ user]
                   (let [edit-user (select-keys user [:username :groups :password])]
                     (st/mutate! `[(ui.user.edit/set {:value ~edit-user}) :ui])
                     (st/mutate! `[(ui.users/set-user-details {:value ~user})]))))
  (bus/subscribe :new-users
                 #(st/mutate! `[(ui.users/set-users {:value ~%2})]))

  (bus/subscribe :new-group
                 #(st/mutate! `[(ui.groups/set-group-details {:value ~%2})]))

  (bus/subscribe :new-groups
                 #(st/mutate! `[(ui.groups/set-groups {:value ~%2})]))
  (bus/subscribe :new-permissions
                 #(st/mutate! `[(ui.perms/set-perms {:value ~%2})])))

(om/add-root! st/reconciler
  App (gdom/getElement "main-app-area"))

(subscribe-all)
