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
  (dom/a #js {:href "#" :onClick #(bus/dispatch :ui.role.user/selected {:name name})}
         name))

(defn group-link [name]
  (dom/a #js {:href "#" :onClick #(bus/dispatch :ui.role.group/selected {:name name})}
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

(defn revoke-link [grant role-type role]
  (dom/a #js {:href "#"  :className "btn btn-danger btn-sm" :role "button"
              :onClick #(bus/dispatch :ui.grant.edit/revoke
                                      {:grant grant :role-type role-type
                                       :role role})}
         "Revoke"))

(defn grant-role-link [role-type role]
  (let [[event label] (condp = role-type
                        "group" [:ui.role.group/selected role]
                        "user" [:ui.role.user/selected role]
                        "global" [:ui.role.global/selected "all"])
        args {:name role}
        d bus/dispatch]
    (dom/a #js {:href "#" :onClick #(bus/dispatch event args)} label)))

(defn grant-table [grants-list role-type role can-revoke?]
  (bs/table ["Role" "Bucket" "Key" "Grants" ""]
            (map (fn [{:keys [grants bucket key bucket_grant any
                              role] :as grant}]
                   {:key (str bucket "." key)
                    ; don't destructure role_type in keys since it clashes
                    ; with role-type variable
                    ; (dash seems to compile to underscore)
                    :cols [(grant-role-link (:role_type grant) role) bucket key
                           (clojure.string/join ", " grants)
                           (if can-revoke?
                             (revoke-link grant role-type role)
                             "")]})
                 grants-list)))

(defn grant-section [title grants-list role-type role can-revoke?]
  (dom/div #js {:className "grants-details"}
           (dom/h2 nil title)
           (if (empty? grants-list)
             (dom/p nil (str "No " title))
             (grant-table grants-list role-type role can-revoke?))))

(defn grants-details [{:keys [direct groups global]} role-type role]
  (dv
    (grant-section "Direct Grants" direct role-type role true)
    (grant-section "Groups Grants" groups role-type role false)
    (grant-section "Global Grants" global role-type role false)))

(defn user-ui [{:keys [username groups grants] :as user-details} edit-user groups-list]
  (dom/div #js {:className "user-details"}
           (bs/table ["", ""]
                     [{:key "username" :cols ["Username" username]}
                      {:key "groups" :cols ["Groups" (group-links groups)]}])

           (action-button "Delete User" :delete-user user-details :danger)
           (dom/h3 nil "Grants")
           (grants-details grants :user user-details)

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

(defn edit-group-form [{:keys [groupname groups] :as group} groups-list
                       {:keys [title action-label action-type update?]}]
  (dv (dom/h2 nil title)
      (bs/form
        (bs/form-input :id "group-groupname" :label "Name" :value groupname
                       :readonly update?
                       :on-change #(bus/dispatch :edit-group-set-groupname %))
        (group-selector groups (remove #(= groupname (:name %)) groups-list)
                        :edit-group-set-group)
        (action-button action-label action-type group))))

(defui EditGroup
  static om/IQuery
  (query [this] '[(:edit-group)])
  Object
  (render [this]
          (let [{:keys [edit-group groups-list opts]} (om/props this)]
                (edit-group-form edit-group groups-list opts))))

(def edit-group-ui (om/factory EditGroup))

(defn group-ui [{:keys [name groups grants] :as group-details}
                edit-group groups-list]
  (dom/div #js {:className "group-details"}
           (bs/table ["" ""]
                     [{:key "name" :cols ["Name" name]}
                      {:key "groups" :cols ["Groups" (group-links groups)]}])

           (action-button "Delete Group" :delete-group group-details :danger)

           (grants-details grants :group group-details)

           (edit-group-ui {:edit-group edit-group
                           :opts {:title "Edit Group"
                                  :update? true
                                  :action-label "Update"
                                  :action-type :update-group}
                           :groups-list groups-list})))

(defui GroupDetails
  static om/IQuery
  (query [this] '[(:edit-groups)])
  Object
  (render [this]
          (let [{:keys [edit-group groups-list group-details]} (om/props this)]
            (group-ui group-details edit-group groups-list))))

(def group-details (om/factory GroupDetails))

(defn users-ui [items user-data]
  (dv
    (bs/table ["Username", "Groups"]
              (map (fn [{:keys [name groups]}]
                     {:key name :cols [(user-link name) (group-links groups)]})
                   items))
    (edit-user-ui (assoc user-data :opts {:title "Create User"
                                          :action-label "Create"
                                          :action-type :create-user}))))

(defn edit-grant-form [{:keys [role role-type permission bucket key] :as grant}
                      permissions opts]
  (dv (dom/h2 nil "Add Grant")
      (bs/form
        (bs/form-row "add-grant-role-type" "Role Type"
                     (bs/form-radio :id "add-grant-role-type" :value role-type
                                    :on-change #(bus/dispatch
                                                  :ui.grant.edit.set/role-type %)
                                    :values [{:label "User" :value "user"}
                                             {:label "Group" :value "group"}
                                             {:label "Global" :value "global"}]))

        (bs/form-input :id "add-grant-role" :label "Role"
                       :value (if (= role-type "global") "*" role)
                       :readonly (= role-type "global")
                       :on-change #(bus/dispatch :ui.grant.edit.set/role %))

        (bs/form-input :id "add-grant-bucket" :label "Bucket" :value bucket
                       :on-change #(bus/dispatch :ui.grant.edit.set/bucket %))

        (bs/form-input :id "add-grant-key" :label "Key" :value key
                       :on-change #(bus/dispatch :ui.grant.edit.set/key %))

        (bs/form-row "add-grant-permission" "Permission"
                     (bs/form-select :id "add-grant-permission"
                                     :on-change #(bus/dispatch
                                                   :ui.grant.edit.set/permission %)
                                     :value permission
                                     :values permissions))

        (action-button "Add Grant" :ui.grant.edit/create grant))))

(defn permissions-to-options [items]
  (let [value-list (mapcat (fn [[app perms]] (map #(str app "." %) perms)) items)
        sorted-list (sort value-list)
        options-list (map (fn [v] {:value v :label v}) sorted-list)]
    options-list))

(defn maybe-set-default-perm [{:keys [permission] :as grant} permissions]
  (if (nil? permission)
    (assoc grant :permission (:value (first permissions)))
    grant))

(defui EditGrant
  static om/IQuery
  (query [this] '[(:edit-grant)])
  Object
  (render [this]
          (let [{:keys [edit-grant permissions-list opts]} (om/props this)
                permissions (permissions-to-options permissions-list)
                edit-grant-default-perm (maybe-set-default-perm edit-grant permissions)]
            (edit-grant-form edit-grant-default-perm permissions opts))))

(def edit-grant-ui (om/factory EditGrant))

(defn groups-ui [items group-data]
  (dv
    (bs/table ["Group" "Groups"]
              (map (fn [{:keys [name groups]}]
                     {:key name
                      :cols [(group-link name) (group-links groups)]}) items))

    (edit-group-ui (assoc group-data :opts {:title "Create Group"
                                            :action-label "Create"
                                            :action-type :create-group}))))

(defn perms-ui [{:keys [permissions grants]} edit-grant]
  (dv
    (grant-section "Global Grants" (:global grants) :global "all" true)
    (edit-grant-ui edit-grant)

    (dom/h2 nil "Available Permissions")
    (bs/table ["App", "Permissions"]
              (map (fn [[k perms]]
                     {:key k
                      :cols [k (perm-links name perms)]})
                   permissions))))

(defn main-panel [nav-selected {:keys [users-list edit-user
                                       groups-list edit-group
                                       edit-grant
                                       permissions-list]}]
  (when (nil? users-list) (bus/dispatch :reload-users {}))
  (when (nil? groups-list) (bus/dispatch :reload-groups {}))
  (when (nil? permissions-list) (bus/dispatch :reload-permissions {}))

  (dv (condp = nav-selected
        :users (users-ui users-list edit-user)
        :user (user-details edit-user)
        :groups (groups-ui groups-list edit-group)
        :group (group-details edit-group)
        :permissions (perms-ui permissions-list edit-grant)
        (.warn js/console "invalid nav " (str nav-selected)))))

(defn loading-sign []
  (dom/p #js {:className "bg-success"
              :style #js {:padding "1em" :textAlign "center"}} "Loading"))

(defui App
  static om/IQuery
  (query [this]
         (let [[edit-user-query] (om/get-query EditUser)
               [edit-group-query] (om/get-query EditGroup)
               [user-details-query] (om/get-query UserDetails)
               [group-details-query] (om/get-query GroupDetails)
               [edit-grant-query] (om/get-query EditGrant)]
           `[:ui
             ~edit-user-query
             ~edit-group-query
             ~user-details-query
             ~group-details-query
             ~edit-grant-query
             (:nav-info)
             (:users-list)
             (:groups-list)
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


(defn navigate [id nav-param title]
  (st/mutate!
    `[(ui/navigate {:nav-selected ~id :nav-param ~nav-param :title ~title
                    :loading true}) :ui]))

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
      (.warn js/console "invalid view" view))))

(defn refresh-current-view []
  (let [{:keys [nav-selected nav-param title]} (st/get-ui-state)]
    (update-view nav-selected nav-param)))

(defn on-user-changed []
  (api/load-users (get-token))
  (st/mutate! `[(ui.user.edit/reset) :ui]))

(defn on-group-changed []
  (api/load-groups (get-token))
  (st/mutate! `[(ui.user.edit/reset) :ui]))

(defn on-event-mutate [event value changes]
    (st/mutate! `[(~event {:value ~value}) ~changes]))

(defn to-mutate [event-type changes]
  (let [event-symbol (symbol (namespace event-type) (name event-type))]
    (bus/subscribe event-type #(on-event-mutate event-symbol %2 changes))))

(defn on-grant-create [_ {:keys [role role-type bucket key permission]}]
  (let [full-role (if (= role-type "global") "*" (str role-type "/" role))
        grant {:role full-role :permission permission
               :bucket bucket :key key}]
    (api/grant (get-token) grant)))

(defn on-revoke [_ {:keys [grant role-type role]}]
  (let [prefix (name role-type)
        role-name (condp = role-type
                    :user (str prefix "/" role)
                    :group (str prefix "/" role)
                    :global "*")
        {:keys [bucket key]} grant
        permission (-> grant :grants first)]

    (api/revoke (get-token) {:role role-name :permission permission
                             :bucket bucket :key key})))

(defn subscribe-all []
  (bus/unsubscribe-all)
  (bus/stop-dispatch-handler)

  (bus/start-dispatch-handler)
  (api/subscribe-all)

  (bus/subscribe :nav-click (fn [event {:keys [id title]}]
                              (update-view id)
                              (navigate id nil title)))

  (bus/subscribe :ui.role.user/selected (fn [_ {:keys [name]}]
                                          (update-view :user name)
                                          (navigate :user name (str "User " name))))

  (bus/subscribe :ui.role.group/selected (fn [_ {:keys [name]}]
                                           (update-view :group name)
                                           (navigate :group name (str "Group " name))))
  (bus/subscribe :ui.role.global/selected (fn [_ {:keys [name]}]
                                            (update-view :permissions)
                                            (navigate :permissions nil "Permissions")))

  (bus/subscribe :reload-groups (fn [_ _] (api/load-groups (get-token))))
  (bus/subscribe :reload-users (fn [_ _] (api/load-users (get-token))))
  (bus/subscribe :reload-permissions (fn [_ _] (api/load-permissions (get-token))))

  (bus/subscribe :create-user (fn [_ user] (api/create-user (get-token) user)))
  (bus/subscribe :user-created (fn [_ _] (on-user-changed)))

  (bus/subscribe :update-user (fn [_ user] (api/update-user (get-token) user)))
  (bus/subscribe :delete-user (fn [_ user] (api/delete-user (get-token) user)))
  (bus/subscribe :user-updated (fn [_ _]
                                 (on-user-changed)
                                 (navigate :users nil "Users")))
  (bus/subscribe :user-deleted (fn [_ _]
                                 (on-user-changed)
                                 (navigate :users nil "Users")))

  (bus/subscribe :create-group (fn [_ group]
                                (api/create-group (get-token) group)))
  (bus/subscribe :delete-group (fn [_ group]
                                 (api/delete-group (get-token) group)))
  (bus/subscribe :group-updated (fn [_ _]
                                  (on-group-changed)
                                  (navigate :groups nil "Groups")))
  (bus/subscribe :group-deleted (fn [_ _]
                                  (on-group-changed)
                                  (navigate :groups nil "Groups")))

  (bus/subscribe :group-created (fn [_ group]
                                 (api/load-groups (get-token))
                                 (st/mutate! `[(ui.group.edit/reset) :ui])))
  (bus/subscribe :update-group (fn [_ group]
                                 (api/update-group (get-token) group)))

  (bus/subscribe :grant-granted (fn [_ _] (refresh-current-view)))
  (bus/subscribe :grant-revoked (fn [_ _] (refresh-current-view)))

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

  (to-mutate :ui.grant.edit.set/role-type :ui)
  (to-mutate :ui.grant.edit.set/role :ui)
  (to-mutate :ui.grant.edit.set/bucket :ui)
  (to-mutate :ui.grant.edit.set/key :ui)
  (to-mutate :ui.grant.edit.set/permission :ui)
  (bus/subscribe :ui.grant.edit/create on-grant-create)
  (bus/subscribe :ui.grant.edit/revoke on-revoke)


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
                 (fn [_ group]
                   (let [{groupname :name groups :groups} group
                         edit-group {:groupname groupname :groups groups}]
                     (st/mutate! `[(ui.group.edit/set {:value ~edit-group}) :ui])
                     (st/mutate! `[(ui.groups/set-group-details {:value ~group})]))))

  (bus/subscribe :new-groups
                 #(st/mutate! `[(ui.groups/set-groups {:value ~%2})]))
  (bus/subscribe :new-permissions
                 #(st/mutate! `[(ui.perms/set-perms {:value ~%2})])))

(om/add-root! st/reconciler
  App (gdom/getElement "main-app-area"))

(subscribe-all)
