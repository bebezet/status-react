(ns status-im.ui.screens.group.views
  (:require [cljs.spec.alpha :as spec]
            [clojure.string :as string]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [status-im.constants :as constants]
            [status-im.i18n :as i18n]
            [status-im.ui.components.button :as button]
            [status-im.ui.components.chat-icon.screen :as chat-icon]
            [status-im.ui.components.checkbox.view :as checkbox]
            [status-im.ui.components.colors :as colors]
            [status-im.ui.components.contact.contact :as contact]
            [status-im.ui.components.keyboard-avoid-presentation
             :as
             kb-presentation]
            [status-im.ui.components.list-item.views :as list-item]
            [status-im.ui.components.list-selection :as list-selection]
            [status-im.ui.components.list.views :as list]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.search-input.view :as search]
            [status-im.ui.components.toolbar :as bottom-toolbar]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.ui.components.topbar :as topbar]
            [status-im.ui.screens.add-new.styles :as add-new.styles]
            [status-im.ui.screens.group.styles :as styles]
            [status-im.utils.debounce :as debounce]
            [status-im.utils.platform :as platform])
  (:require-macros [status-im.utils.views :as views]))

(defn- render-contact [row]
  [list-item/list-item
   {:title (contact/format-name row)
    :icon  [chat-icon/contact-icon-contacts-tab row]}])

(defn- on-toggle [allow-new-users? checked? public-key]
  (cond

    checked?
    (re-frame/dispatch [:deselect-contact public-key allow-new-users?])

    ;; Only allow new users if not reached the maximum
    (and (not checked?)
         allow-new-users?)
    (re-frame/dispatch [:select-contact public-key allow-new-users?])))

(defn- on-toggle-participant [allow-new-users? checked? public-key]
  (cond

    checked?
    (re-frame/dispatch [:deselect-participant public-key allow-new-users?])

   ;; Only allow new users if not reached the maximum
    (and (not checked?)
         allow-new-users?)
    (re-frame/dispatch [:select-participant public-key allow-new-users?])))

(defn- toggle-item []
  (fn [allow-new-users? subs-name {:keys [public-key] :as contact} on-toggle]
    (let [contact-selected? @(re-frame/subscribe [subs-name public-key])]
      [list-item/list-item
       {:title       (contact/format-name contact)
        :icon        [chat-icon/contact-icon-contacts-tab contact]
        :on-press    #(on-toggle allow-new-users? contact-selected? public-key)
        :accessories [[checkbox/checkbox {:checked? contact-selected?}]]}])))

(defn- group-toggle-contact [allow-new-users? contact]
  [toggle-item allow-new-users? :is-contact-selected? contact on-toggle])

(defn- group-toggle-participant [allow-new-users? contact]
  [toggle-item allow-new-users? :is-participant-selected? contact on-toggle-participant])

(defn- handle-invite-friends-pressed []
  (if platform/desktop?
    (re-frame/dispatch [:navigate-to :new-contact])
    (list-selection/open-share {:message (i18n/label :t/get-status-at)})))

(defn toggle-list [{:keys [contacts render-fn]}]
  [react/scroll-view {:flex 1}
   (if platform/desktop?
     (for [contact contacts]
       ^{:key (:public-key contact)}
       (render-fn contact))
     [list/flat-list {:data                      contacts
                      :key-fn                    :public-key
                      :render-fn                 render-fn
                      :keyboardShouldPersistTaps :always}])])

(defn no-contacts [{:keys [no-contacts]}]
  [react/view {:style styles/no-contacts}
   [react/text
    {:style (styles/no-contact-text)}
    no-contacts]
   (when-not platform/desktop?
     [button/button
      {:type     :secondary
       :on-press handle-invite-friends-pressed
       :label    :t/invite-friends}])])

(defn filter-contacts [filter-text contacts]
  (let [lower-filter-text (string/lower-case (str filter-text))
        filter-fn         (fn [{:keys [name alias]}]
                            (or
                             (string/includes? (string/lower-case (str name)) lower-filter-text)
                             (string/includes? (string/lower-case (str alias)) lower-filter-text)))]
    (if filter-text
      (filter filter-fn contacts)
      contacts)))

;; Set name of new group-chat
(views/defview new-group []
  (views/letsubs [contacts   [:selected-group-contacts]
                  group-name [:new-chat-name]]
    (let [save-btn-enabled? (and (spec/valid? :global/not-empty-string group-name) (pos? (count contacts)))]
      [kb-presentation/keyboard-avoiding-view  {:style styles/group-container}
       [react/view {:flex 1}
        [toolbar/toolbar {}
         toolbar/default-nav-back
         [react/view {:style styles/toolbar-header-container}
          [react/view
           [react/text (i18n/label :t/new-group-chat)]]
          [react/view
           [react/text {:style (styles/toolbar-sub-header)}
            (i18n/label :t/group-chat-members-count
                        {:selected (inc (count contacts))
                         :max      constants/max-group-chat-participants})]]]]

        [react/view {:style {:padding-top 16
                             :flex             1}}
         [react/view {:style {:padding-horizontal 16}}
          [react/view (add-new.styles/input-container)
           [react/text-input
            {:auto-focus          true
             :on-change-text      #(re-frame/dispatch [:set :new-chat-name %])
             :default-value       group-name
             :placeholder         (i18n/label :t/set-a-topic)
             :style               add-new.styles/input
             :accessibility-label :chat-name-input}]]
          [react/text {:style (styles/members-title)}
           (i18n/label :t/members-title)]]
         [react/view {:style {:margin-top 8
                              :flex       1}}
          [list/flat-list {:data                         contacts
                           :key-fn                       :address
                           :render-fn                    render-contact
                           :bounces                      false
                           :keyboard-should-persist-taps :always
                           :enable-empty-sections        true}]]]
        [bottom-toolbar/toolbar
         {:show-border? true
          :left         {:type                :previous
                         :accessibility-label :previous-button
                         :label               (i18n/label :t/back)
                         :on-press            #(re-frame/dispatch [:navigate-back])}
          :right        {:type                :secondary
                         :container-style     {:padding-horizontal 16}
                         :text-style          {:font-weight "500"}
                         :accessibility-label :create-group-chat-button
                         :label               (i18n/label :t/create-group-chat)
                         :disabled?           (not save-btn-enabled?)
                         :on-press            #(debounce/dispatch-and-chill [:group-chats.ui/create-pressed group-name]
                                                                            300)}}]]])))

(defn searchable-contact-list []
  (let [search-value (reagent/atom nil)]
    (fn [{:keys [contacts no-contacts-label toggle-fn allow-new-users?]}]
      [react/view {:style {:flex 1}}
       [react/view {:style (styles/search-container)}
        [search/search-input {:on-cancel #(reset! search-value nil)
                              :on-change #(reset! search-value %)}]]
       [react/view {:style {:flex             1
                            :padding-vertical 8}}
        (if (seq contacts)
          [toggle-list {:contacts  (filter-contacts @search-value contacts)
                        :render-fn (partial toggle-fn allow-new-users?)}]
          [no-contacts {:no-contacts no-contacts-label}])]])))

;; Start group chat
(views/defview contact-toggle-list []
  (views/letsubs [contacts                [:contacts/active]
                  selected-contacts-count [:selected-contacts-count]]
    [kb-presentation/keyboard-avoiding-view {:style styles/group-container}
     [toolbar/toolbar {:border-bottom-color colors/white}
      toolbar/default-nav-back
      [react/view {:style styles/toolbar-header-container}
       [react/view
        [react/text (i18n/label :t/new-group-chat)]]
       [react/view
        [react/text {:style (styles/toolbar-sub-header)}
         (i18n/label :t/group-chat-members-count
                     {:selected (inc selected-contacts-count)
                      :max      constants/max-group-chat-participants})]]]]
     [searchable-contact-list
      {:contacts          contacts
       :no-contacts-label (i18n/label :t/group-chat-no-contacts)
       :toggle-fn         group-toggle-contact
       :allow-new-users?  (< selected-contacts-count
                             (dec constants/max-group-chat-participants))}]
     [bottom-toolbar/toolbar
      {:show-border? true
       :right        {:type                :next
                      :accessibility-label :next-button
                      :label               (i18n/label :t/next)
                      :disabled?           (zero? selected-contacts-count)
                      :on-press            #(re-frame/dispatch [:navigate-to :new-group])}}]]))

;; Add participants to existing group chat
(views/defview add-participants-toggle-list []
  (views/letsubs [contacts [:contacts/all-contacts-not-in-current-chat]
                  current-chat [:chats/current-chat]
                  selected-contacts-count [:selected-participants-count]]
    (let [current-participants-count (count (:contacts current-chat))]
      [kb-presentation/keyboard-avoiding-view  {:style styles/group-container}
       [toolbar/toolbar {:border-bottom-color colors/white}
        toolbar/default-nav-back
        [react/view {:style styles/toolbar-header-container}
         [react/view
          [react/text (i18n/label :t/add-members)]]
         [react/view
          [react/text {:style (styles/toolbar-sub-header)}
           (i18n/label :t/group-chat-members-count
                       {:selected (+ current-participants-count selected-contacts-count)
                        :max      constants/max-group-chat-participants})]]]]
       [searchable-contact-list
        {:contacts          contacts
         :no-contacts-label (i18n/label :t/group-chat-all-contacts-invited)
         :toggle-fn         group-toggle-participant
         :allow-new-users?  (< (+ current-participants-count
                                  selected-contacts-count)
                               constants/max-group-chat-participants)}]
       [bottom-toolbar/toolbar
        {:show-border? true
         :center       {:type                :secondary
                        :accessibility-label :next-button
                        :label               (i18n/label :t/add)
                        :disabled?           (zero? selected-contacts-count)
                        :on-press            #(re-frame/dispatch [:group-chats.ui/add-members-pressed])}}]])))

(views/defview edit-group-chat-name []
  (views/letsubs [{:keys [name chat-id]} [:chats/current-chat]
                  new-group-chat-name (reagent/atom nil)]
    [kb-presentation/keyboard-avoiding-view  {:style styles/group-container}
     [topbar/topbar
      {:title  :t/edit-group
       :modal? true}]
     [react/view {:style {:padding 16
                          :flex    1}}
      [react/view {:style (add-new.styles/input-container)}
       [react/text-input
        {:on-change-text      #(reset! new-group-chat-name %)
         :default-value       name
         :on-submit-editing   #(when (seq @new-group-chat-name)
                                 (re-frame/dispatch [:group-chats.ui/name-changed chat-id @new-group-chat-name]))
         :placeholder         (i18n/label :t/enter-contact-code)
         :style               add-new.styles/input
         :accessibility-label :new-chat-name
         :return-key-type     :go}]]]
     [react/view {:style {:flex 1}}]
     [bottom-toolbar/toolbar
      {:show-border? true
       :center       {:type                :secondary
                      :accessibility-label :done
                      :label               (i18n/label :t/done)
                      :disabled?           (and (<= (count @new-group-chat-name) 1)
                                                (not (nil? @new-group-chat-name)))
                      :on-press            #(cond
                                              (< 1 (count @new-group-chat-name))
                                              (re-frame/dispatch [:group-chats.ui/name-changed chat-id @new-group-chat-name])

                                              (nil? @new-group-chat-name)
                                              (re-frame/dispatch [:navigate-back]))}}]]))
