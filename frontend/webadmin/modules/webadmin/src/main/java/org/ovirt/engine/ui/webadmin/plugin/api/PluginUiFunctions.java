package org.ovirt.engine.ui.webadmin.plugin.api;

import java.util.List;

import org.ovirt.engine.ui.common.presenter.AddTabActionButtonEvent;
import org.ovirt.engine.ui.common.presenter.RedrawDynamicTabContainerEvent;
import org.ovirt.engine.ui.common.presenter.SetDynamicTabAccessibleEvent;
import org.ovirt.engine.ui.common.widget.AlertManager;
import org.ovirt.engine.ui.common.widget.Align;
import org.ovirt.engine.ui.common.widget.action.AbstractButtonDefinition;
import org.ovirt.engine.ui.common.widget.action.ActionButtonDefinition;
import org.ovirt.engine.ui.common.widget.panel.AlertPanel;
import org.ovirt.engine.ui.uicommonweb.models.ApplySearchStringEvent;
import org.ovirt.engine.ui.webadmin.plugin.entity.EntityObject;
import org.ovirt.engine.ui.webadmin.plugin.entity.EntityType;
import org.ovirt.engine.ui.webadmin.plugin.entity.TagObject;
import org.ovirt.engine.ui.webadmin.plugin.jsni.JsFunctionResultHelper;
import org.ovirt.engine.ui.webadmin.section.main.presenter.DynamicUrlContentProxyFactory;
import org.ovirt.engine.ui.webadmin.section.main.presenter.DynamicUrlContentTabProxyFactory;
import org.ovirt.engine.ui.webadmin.section.main.presenter.MenuPresenterWidget;
import org.ovirt.engine.ui.webadmin.section.main.presenter.SetDynamicTabContentUrlEvent;
import org.ovirt.engine.ui.webadmin.section.main.presenter.popup.CloseDynamicPopupEvent;
import org.ovirt.engine.ui.webadmin.section.main.presenter.popup.DynamicUrlContentPopupPresenterWidget;
import org.ovirt.engine.ui.webadmin.section.main.presenter.popup.SetDynamicPopupContentUrlEvent;
import org.ovirt.engine.ui.webadmin.uicommon.model.TagModelProvider;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.GwtEvent.Type;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.gwtplatform.mvp.client.ChangeTabHandler;
import com.gwtplatform.mvp.client.RequestTabsHandler;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;
import com.gwtplatform.mvp.client.proxy.RevealRootPopupContentEvent;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;

/**
 * Contains UI related functionality exposed to UI plugins through the plugin API.
 */
public class PluginUiFunctions implements HasHandlers {

    private final EventBus eventBus;

    private final DynamicUrlContentTabProxyFactory dynamicUrlContentTabProxyFactory;
    private final DynamicUrlContentProxyFactory dynamicUrlContentProxyFactory;
    private final Provider<DynamicUrlContentPopupPresenterWidget> dynamicUrlContentPopupPresenterWidgetProvider;

    private final TagModelProvider tagModelProvider;
    private final PlaceManager placeManager;
    private final AlertManager alertManager;
    private final MenuPresenterWidget menuPresenterWidget;

    @Inject
    public PluginUiFunctions(EventBus eventBus,
            DynamicUrlContentTabProxyFactory dynamicUrlContentTabProxyFactory,
            DynamicUrlContentProxyFactory dynamicUrlContentProxyFactory,
            Provider<DynamicUrlContentPopupPresenterWidget> dynamicUrlContentPopupPresenterWidgetProvider,
            PlaceManager placeManager,
            AlertManager alertManager,
            MenuPresenterWidget menuPresenterWidget,
            TagModelProvider tagModelProvider) {
        this.eventBus = eventBus;
        this.dynamicUrlContentTabProxyFactory = dynamicUrlContentTabProxyFactory;
        this.dynamicUrlContentProxyFactory = dynamicUrlContentProxyFactory;
        this.dynamicUrlContentPopupPresenterWidgetProvider = dynamicUrlContentPopupPresenterWidgetProvider;
        this.tagModelProvider = tagModelProvider;
        this.placeManager = placeManager;
        this.alertManager = alertManager;
        this.menuPresenterWidget = menuPresenterWidget;
    }

    @Override
    public void fireEvent(GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    /**
     * Adds new dynamic main content view that shows contents of the given URL.
     */
    @Deprecated
    public void addMainTab(String label, String historyToken,
            String contentUrl, TabOptions options) {
        addMainContentView(label, historyToken, contentUrl, options.getPriority().intValue());
    }

    /**
     * Adds new dynamic main content view that shows contents of the given URL.
     */
    public void addMainContentView(String label, String historyToken,
            String contentUrl, int priority) {
        menuPresenterWidget.addMenuItem(priority, label, historyToken);
        // Not interested in the actual proxy, it will register itself.
        dynamicUrlContentProxyFactory.create(historyToken, contentUrl);
    }

    /**
     * Adds new dynamic sub tab that shows contents of the given URL.
     */
    public void addSubTab(EntityType entityType, String label,
            String historyToken, String contentUrl, TabOptions options) {
        Type<RequestTabsHandler> requestTabsEventType = entityType.getSubTabPanelRequestTabs();
        Type<ChangeTabHandler> changeTabEventType = entityType.getSubTabPanelChangeTab();
        Type<RevealContentHandler<?>> slot = entityType.getSubTabPanelContentSlot();

        if (requestTabsEventType != null && changeTabEventType != null && slot != null) {
            addTab(requestTabsEventType, changeTabEventType, slot,
                    label, historyToken, contentUrl, options);
        }
    }

    void addTab(Type<RequestTabsHandler> requestTabsEventType,
            Type<ChangeTabHandler> changeTabEventType,
            Type<RevealContentHandler<?>> slot,
            String label, String historyToken,
            String contentUrl, TabOptions options) {
        // Create and bind tab presenter proxy
        dynamicUrlContentTabProxyFactory.create(
                requestTabsEventType, changeTabEventType, slot,
                label, options.getPriority().floatValue(),
                historyToken, contentUrl,
                options.getAlignRight() ? Align.RIGHT : Align.LEFT,
                options.getSearchPrefix());

        // Redraw the corresponding tab container
        RedrawDynamicTabContainerEvent.fire(this, requestTabsEventType);
    }

    /**
     * Sets the content URL for existing dynamic tab.
     */
    public void setTabContentUrl(final String historyToken, final String contentUrl) {
        Scheduler.get().scheduleDeferred(() -> SetDynamicTabContentUrlEvent.fire(PluginUiFunctions.this,
                historyToken, contentUrl));
    }

    /**
     * Updates tab/place accessibility for existing dynamic tab.
     */
    public void setTabAccessible(final String historyToken, final boolean tabAccessible) {
        Scheduler.get().scheduleDeferred(() -> SetDynamicTabAccessibleEvent.fire(PluginUiFunctions.this,
                historyToken, tabAccessible));
    }

    /**
     * Adds new action button to standard table-based main tab.
     */
    public void addMainTabActionButton(EntityType entityType, String label,
            ActionButtonInterface actionButtonInterface) {
        String historyToken = entityType.getMainHistoryToken();

        if (historyToken != null) {
            AddTabActionButtonEvent.fire(this, historyToken,
                    createButtonDefinition(label, actionButtonInterface));
        }
    }

    /**
     * Adds new action button to standard table-based sub tab.
     */
    public void addSubTabActionButton(EntityType mainTabEntityType, EntityType subTabEntityType,
            String label, ActionButtonInterface actionButtonInterface) {
        String historyToken = mainTabEntityType.getSubTabHistoryToken(subTabEntityType);

        if (historyToken != null) {
            AddTabActionButtonEvent.fire(this, historyToken,
                    createButtonDefinition(label, actionButtonInterface));
        }
    }

    <T> ActionButtonDefinition<T> createButtonDefinition(String label,
            final ActionButtonInterface actionButtonInterface) {
        return new AbstractButtonDefinition<T>(eventBus,
                label, actionButtonInterface.getLocation()) {

            @Override
            public void onClick(List<T> selectedItems) {
                actionButtonInterface.onClick().invoke(
                        EntityObject.arrayFrom(selectedItems), null);
            }

            @Override
            public boolean isEnabled(List<T> selectedItems) {
                return JsFunctionResultHelper.invokeAndGetResultAsBoolean(
                        actionButtonInterface.isEnabled(),
                        EntityObject.arrayFrom(selectedItems), null, true);
            }

            @Override
            public boolean isAccessible(List<T> selectedItems) {
                return JsFunctionResultHelper.invokeAndGetResultAsBoolean(
                        actionButtonInterface.isAccessible(),
                        EntityObject.arrayFrom(selectedItems), null, true);
            }

        };
    }

    /**
     * Shows a modal dialog with content loaded from the given URL.
     */
    public void showDialog(String title, String dialogToken, String contentUrl,
            String width, String height, DialogOptions options) {
        // Create and initialize the popup
        DynamicUrlContentPopupPresenterWidget popup = dynamicUrlContentPopupPresenterWidgetProvider.get();
        popup.init(dialogToken, title, width, height,
                options.getCloseIconVisible(),
                options.getCloseOnEscKey());
        popup.setContentUrl(contentUrl);

        // Add dialog buttons
        JsArray<DialogButtonInterface> buttons = options.getButtons();
        for (int i = 0; i < buttons.length(); i++) {
            final DialogButtonInterface dialogButtonInterface = buttons.get(i);

            if (dialogButtonInterface != null) {
                popup.addFooterButton(dialogButtonInterface.getLabel(),
                        event -> dialogButtonInterface.onClick().invoke(null, null));
            }
        }

        // Reveal the popup
        RevealRootPopupContentEvent.fire(this, popup);
    }

    /**
     * Sets the content URL for existing modal dialog.
     */
    public void setDialogContentUrl(final String dialogToken, final String contentUrl) {
        Scheduler.get().scheduleDeferred(() -> SetDynamicPopupContentUrlEvent.fire(PluginUiFunctions.this,
                dialogToken, contentUrl));
    }

    /**
     * Closes an existing modal dialog.
     */
    public void closeDialog(final String dialogToken) {
        Scheduler.get().scheduleDeferred(() -> CloseDynamicPopupEvent.fire(PluginUiFunctions.this,
                dialogToken));
    }

    /**
     * Reveals the application place denoted by {@code historyToken}.
     */
    public void revealPlace(final String historyToken) {
        Scheduler.get().scheduleDeferred(() -> placeManager.revealPlace(new PlaceRequest.Builder().nameToken(historyToken).build()));
    }

    /**
     * Applies the given search string, which triggers transition to the corresponding application place.
     */
    public void setSearchString(final String searchString) {
        Scheduler.get().scheduleDeferred(() -> ApplySearchStringEvent.fire(PluginUiFunctions.this, searchString));
    }

    /**
     * Shows an application-wide alert message.
     */
    public void showAlert(final AlertPanel.Type type, final String message, final AlertOptions options) {
        Scheduler.get().scheduleDeferred(() -> alertManager.showAlert(type, SafeHtmlUtils.fromString(message), options.getAutoHideMs().intValue()));
    }

    /**
     * Returns the current locale string in <a href="http://tools.ietf.org/html/rfc5646">standard format</a>,
     * e.g. {@code en-US}.
     */
    public String getCurrentLocale() {
        String currentLocale = LocaleInfo.getCurrentLocale().getLocaleName();

        // Replace "default" with "en_US"
        if ("default".equals(currentLocale)) { //$NON-NLS-1$
            currentLocale = "en_US"; //$NON-NLS-1$
        }

        // Replace "_" with "-"
        currentLocale = currentLocale.replace('_', '-');

        return currentLocale;
    }

    public TagObject getRootTagNode() {
        return TagObject.from(tagModelProvider.getModel().getRootNode());
    }
}
