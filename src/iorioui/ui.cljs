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
  (dom/div nil items))

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
  (dom/div nil (map (fn [group] (dom/div nil (group-link group) " ")) groups)))

(defn perm-links [app perms]
  (dom/div nil (map (fn [perm] (dom/div nil (perm-link app perm) " ")) perms)))

(defn create-user-form [{:keys [username password groups] :as user} groups-list]
  (bs/form
    (bs/form-input :id "user-username" :label "Username"
                   :value username
                   :on-change #(bus/dispatch
                                 :create-user-edit-username %))
    (bs/form-input :id "user-password" :label "Password" :input-type "password"
                   :value password
                   :on-change #(bus/dispatch
                                 :create-user-edit-password %))
    (map (fn [{group-name :name}]
           (bs/form-check :label group-name
                          :value (contains? groups group-name)
                          :on-change #(bus/dispatch
                                        :create-user-edit-group
                                        {:name group-name :value %})))
         groups-list)

    (bs/button :level :primary :label "Create"
               :on-click #(bus/dispatch :create-user user))))

(defui CreateUser
  static om/IQuery
  (query [this] '[(:create-user)])
  Object
  (render [this]
          (let [{:keys [create-user groups-list]} (om/props this)]
            (when (nil? groups-list)
              (bus/dispatch :reload-groups {:source :create-user}))

            (dom/div nil
                     (dom/h2 nil "Create User")
                     (create-user-form create-user groups-list)))))

(def create-user-ui (om/factory CreateUser))

(defn create-group-form [{:keys [groupname groups] :as group} groups-list]
  (bs/form
    (bs/form-input :id "group-groupname" :label "Name"
                   :value groupname
                   :on-change #(bus/dispatch
                                 :create-group-edit-groupname %))
    (map (fn [{group-name :name}]
           (bs/form-check :label group-name
                          :value (contains? groups group-name)
                          :on-change #(bus/dispatch
                                        :create-group-edit-group
                                        {:name group-name :value %})))
         groups-list)

    (bs/button :level :primary :label "Create"
               :on-click #(bus/dispatch :create-group group))))

(defui CreateGroup
  static om/IQuery
  (query [this] '[(:create-group)])
  Object
  (render [this]
          (let [{:keys [create-group groups-list]} (om/props this)]
            (when (nil? groups-list)
              (bus/dispatch :reload-groups {:source :create-group}))

            (dom/div nil
                     (dom/h2 nil "Create Group")
                     (create-group-form create-group groups-list)))))

(def create-group-ui (om/factory CreateGroup))

(defn users-ui [items user-data]
  (dom/div nil
           (bs/table ["Username", "Groups"]
                     (map (fn [{:keys [name groups]}]
                            {:key name
                             :cols [(user-link name)
                                    (group-links groups)]})
                          items))
           (create-user-ui user-data)))

(defn grants-details [grants-list]
  (dom/div #js {:className "grants-details"}
           (bs/table ["Bucket" "Key" "Grants" "Bucket Grant" "Any Grant"]
           (map (fn [{:keys [grants bucket key bucket_grant any]}]
                  {:key (str bucket "." key)
                   :cols [bucket key (clojure.string/join ", " grants)
                                    (str bucket_grant) (str any)]})
                grants-list))))

(defn user-ui [{:keys [username groups grants]}]
  (dom/div #js {:className "user-details"}
           (bs/table ["", ""]
                     [{:key "username" :cols ["Username" username]}
                      {:key "groups" :cols ["Groups" (group-links groups)]}])

           (dom/h3 nil "Grants")
           (grants-details grants)))

(defn groups-ui [items group-data]
  (dom/div nil
           (bs/table ["Group", "Groups"]
                     (map (fn [{:keys [name groups]}]
                            {:key name
                             :cols [(group-link name)
                                    (group-links groups)]}) items))

           (create-group-ui group-data)))

(defn group-ui [{:keys [name groups direct_grants]}]
  (dom/div #js {:className "group-details"}
           (bs/table ["", ""]
                     [{:key "name" :cols ["Name" name]}
                      {:key "groups" :cols ["Groups" (group-links groups)]}])

           (if (empty? direct_grants)
             (dom/h3 nil "No direct grants")
             (dom/div nil
                      (dom/h3 nil "Direct Grants")
                      (grants-details direct_grants)))))

(defn perms-ui [items]
  (bs/table ["App", "Permissions"]
            (map (fn [[name perms]]
                   {:key name
                    :cols [name (perm-links name perms)]})
                 items)))

(defn main-panel [nav-selected
                  {:keys [user-details users-list create-user
                          group-details groups-list create-group
                          permissions-list]}]
  (dom/div nil (condp = nav-selected
                 :users (users-ui users-list create-user)
                 :user (user-ui user-details)
                 :groups (groups-ui groups-list create-group)
                 :group (group-ui group-details)
                 :permissions (perms-ui permissions-list)
                 (.warn js/console "invalid nav " (str nav-selected)))))

(defn loading-sign []
  (dom/p #js {:className "bg-success"
              :style #js {:padding "1em" :textAlign "center"}} "Loading"))

(defui App
  static om/IQuery
  (query [this]
         (let [create-user-query (first (om/get-query CreateUser))
               create-group-query (first (om/get-query CreateGroup))]
           `[:ui
             ~create-user-query
             ~create-group-query
             (:nav-info)
             (:user-details) (:users-list)
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

(defn subscribe-all []
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
  (bus/subscribe :reload-groups (fn [_ _params]
                                  (api/load-groups (get-token))))

  (bus/subscribe :create-user (fn [_ user]
                                (api/create-user (get-token) user)))

  (bus/subscribe :user-created (fn [_ user]
                                 (api/load-users (get-token))
                                 (st/mutate! `[(ui.user.create/reset) :ui])))

  (bus/subscribe :create-group (fn [_ group]
                                (api/create-group (get-token) group)))

  (bus/subscribe :group-created (fn [_ group]
                                 (api/load-groups (get-token))
                                 (st/mutate! `[(ui.group.create/reset) :ui])))

  (bus/subscribe :api-loading-change
                 #(st/mutate! `[(ui/set-loading {:value ~%2}) :ui]))

  (bus/subscribe :create-user-edit-username
                 #(st/mutate! `[(ui.user.create/set-username
                                  {:value ~%2}) :create-user-username]))

  (bus/subscribe :create-user-edit-password
                 #(st/mutate! `[(ui.user.create/set-password
                                  {:value ~%2}) :create-user-password]))
  (bus/subscribe :create-user-edit-group
                 #(st/mutate! `[(ui.user.create/set-group ~%2) :ui]))

  (bus/subscribe :create-group-edit-groupname
                 #(st/mutate! `[(ui.group.create/set-groupname
                                  {:value ~%2}) :create-group-groupname]))

  (bus/subscribe :create-group-edit-group
                 #(st/mutate! `[(ui.group.create/set-group ~%2) :ui]))

  (bus/subscribe :new-user
                 #(st/mutate! `[(ui.users/set-user-details {:value ~%2})]))
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
