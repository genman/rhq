/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.coregui.client.dashboard.portlets.groups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.ContentsType;
import com.smartgwt.client.types.Overflow;
import com.smartgwt.client.types.VerticalAlignment;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.Window;
import com.smartgwt.client.widgets.events.CloseClickEvent;
import com.smartgwt.client.widgets.events.CloseClickHandler;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.events.SubmitValuesEvent;
import com.smartgwt.client.widgets.form.events.SubmitValuesHandler;
import com.smartgwt.client.widgets.form.fields.CanvasItem;
import com.smartgwt.client.widgets.form.fields.LinkItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.form.fields.events.ClickEvent;
import com.smartgwt.client.widgets.form.fields.events.ClickHandler;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.common.EntityContext;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.criteria.MeasurementScheduleCriteria;
import org.rhq.core.domain.criteria.ResourceGroupCriteria;
import org.rhq.core.domain.dashboard.DashboardPortlet;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.measurement.composite.MeasurementDataNumericHighLowComposite;
import org.rhq.core.domain.resource.group.GroupCategory;
import org.rhq.core.domain.resource.group.composite.ResourceGroupComposite;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;
import org.rhq.coregui.client.LinkManager;
import org.rhq.coregui.client.components.measurement.CustomConfigMeasurementRangeEditor;
import org.rhq.coregui.client.dashboard.AutoRefreshPortlet;
import org.rhq.coregui.client.dashboard.AutoRefreshUtil;
import org.rhq.coregui.client.dashboard.CustomSettingsPortlet;
import org.rhq.coregui.client.dashboard.Portlet;
import org.rhq.coregui.client.dashboard.PortletViewFactory;
import org.rhq.coregui.client.dashboard.PortletWindow;
import org.rhq.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent;
import org.rhq.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent.Constant;
import org.rhq.coregui.client.gwt.GWTServiceLookup;
import org.rhq.coregui.client.inventory.common.detail.summary.AbstractActivityView;
import org.rhq.coregui.client.inventory.common.graph.CustomDateRangeState;
import org.rhq.coregui.client.inventory.groups.detail.monitoring.table.CompositeGroupD3GraphListView;
import org.rhq.coregui.client.inventory.groups.detail.monitoring.table.CompositeGroupD3MultiLineGraph;
import org.rhq.coregui.client.util.BrowserUtility;
import org.rhq.coregui.client.util.Log;
import org.rhq.coregui.client.util.async.Command;
import org.rhq.coregui.client.util.async.CountDownLatch;
import org.rhq.coregui.client.util.enhanced.EnhancedVLayout;

/**
 * This portlet allows the end user to customize the metric display
 *
 * @author Simeon Pinder
 */
public class GroupMetricsPortlet extends EnhancedVLayout implements CustomSettingsPortlet, AutoRefreshPortlet {

    public static final String CHART_TITLE = MSG.common_title_metric_chart();
    private EntityContext context;
    protected Canvas recentMeasurementsContent = new Canvas();
    protected boolean currentlyLoading = false;
    // A non-displayed, persisted identifier for the portlet
    public static final String KEY = "GroupMetrics";
    // A default displayed, persisted name for the portlet
    public static final String NAME = MSG.view_portlet_defaultName_group_metrics();
    public static final String ID = "id";

    // set on initial configuration, the window for this portlet view.
    protected PortletWindow portletWindow;
    //instance ui widgets

    protected Timer refreshTimer;

    private volatile List<MeasurementSchedule> enabledSchedules = null;
    private volatile boolean renderChart = false;

    // final version needed to pass to anon classes
    // so we can call refresh in anon callback handler
    final protected GroupMetricsPortlet refreshablePortlet;

    //defines the list of configuration elements to load/persist for this portlet
    protected static List<String> CONFIG_INCLUDE = new ArrayList<String>();
    static {
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_BEGIN_END_FLAG);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_ENABLE);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_LASTN);
        CONFIG_INCLUDE.add(Constant.METRIC_RANGE_UNIT);
    }

    public GroupMetricsPortlet(EntityContext context) {
        super();
        this.context = context;
        this.refreshablePortlet = this;
    }

    @Override
    protected void onInit() {
        setRefreshing(true);
        initializeUi();
        loadData();
    }

    /**Defines layout for the portlet page.
     */
    protected void initializeUi() {
        setPadding(5);
        setMembersMargin(5);
        addMember(recentMeasurementsContent);
    }

    /** Responsible for initialization and lazy configuration of the portlet values
     */
    public void configure(PortletWindow portletWindow, DashboardPortlet storedPortlet) {
        //populate portlet configuration details
        if (null == this.portletWindow && null != portletWindow) {
            this.portletWindow = portletWindow;
        }

        if ((null == storedPortlet) || (null == storedPortlet.getConfiguration())) {
            return;
        }

        Configuration portletConfig = storedPortlet.getConfiguration();

        //lazy init any elements not yet configured.
        for (String key : PortletConfigurationEditorComponent.CONFIG_PROPERTY_INITIALIZATION.keySet()) {
            if ((portletConfig.getSimple(key) == null) && CONFIG_INCLUDE.contains(key)) {
                portletConfig.put(new PropertySimple(key,
                    PortletConfigurationEditorComponent.CONFIG_PROPERTY_INITIALIZATION.get(key)));
            }
        }
    }

    public Canvas getHelpCanvas() {
        return new HTMLFlow(MSG.view_portlet_help_metrics());
    }

    public static final class Factory implements PortletViewFactory {
        public static final PortletViewFactory INSTANCE = new Factory();

        public final Portlet getInstance(EntityContext context) {

            if (EntityContext.Type.ResourceGroup != context.getType()) {
                throw new IllegalArgumentException("Context [" + context + "] not supported by portlet");
            }

            return new GroupMetricsPortlet(context);
        }
    }

    protected void loadData() {
        currentlyLoading = true;
        getRecentMetrics();
    }

    /** Builds custom config UI, using shared widgets
     */
    @Override
    public DynamicForm getCustomSettingsForm() {
        //root form.
        DynamicForm customSettings = new DynamicForm();
        //embed range editor in it own container
        EnhancedVLayout page = new EnhancedVLayout();
        final DashboardPortlet storedPortlet = this.portletWindow.getStoredPortlet();
        final Configuration portletConfig = storedPortlet.getConfiguration();
        final CustomConfigMeasurementRangeEditor measurementRangeEditor = PortletConfigurationEditorComponent
            .getMeasurementRangeEditor(portletConfig);

        //submit handler
        customSettings.addSubmitValuesHandler(new SubmitValuesHandler() {
            @Override
            public void onSubmitValues(SubmitValuesEvent event) {
                //retrieve range editor values
                Configuration updatedConfig = AbstractActivityView.saveMeasurementRangeEditorSettings(
                    measurementRangeEditor, portletConfig);

                //persist
                storedPortlet.setConfiguration(updatedConfig);
                configure(portletWindow, storedPortlet);
                refresh();
            }
        });
        page.addMember(measurementRangeEditor);
        customSettings.addChild(page);
        return customSettings;
    }

    /** Fetches recent metric information and updates the DynamicForm instance with i)sparkline information,
     * ii) link to recent metric graph for more details and iii) last metric value formatted to show significant
     * digits.
     */
    protected void getRecentMetrics() {

        renderChart = true;

        //display container
        final VLayout column = new VLayout();
        column.setHeight(10);//pack

        final CountDownLatch latch = CountDownLatch.create(2, new Command() {
            @Override
            public void execute() {
                if (enabledSchedules.isEmpty() || !renderChart) {
                    DynamicForm row = getEmptyDataForm();
                    column.addMember(row);
                    return;
                }
                //build id mapping for measurementDefinition instances Ex. Free Memory -> MeasurementDefinition[100071]
                final HashMap<String, MeasurementDefinition> measurementDefMap = new HashMap<String, MeasurementDefinition>();
                for (MeasurementSchedule schedule : enabledSchedules) {
                    measurementDefMap.put(schedule.getDefinition().getDisplayName(), schedule.getDefinition());
                }
                Set<String> displayNamesSet = measurementDefMap.keySet();
                //bundle definition ids for async call.
                int[] definitionArrayIds = new int[displayNamesSet.size()];
                final String[] displayOrder = new String[displayNamesSet.size()];
                displayNamesSet.toArray(displayOrder);
                //sort the charting data ex. Free Memory, Free Swap Space,..System Load
                Arrays.sort(displayOrder);

                //organize definitionArrayIds for ordered request on server.
                int index = 0;
                for (String definitionToDisplay : displayOrder) {
                    definitionArrayIds[index++] = measurementDefMap.get(definitionToDisplay).getId();
                }

                fetchEnabledMetrics(enabledSchedules, definitionArrayIds, displayOrder, measurementDefMap, column);
            }
        });

        //fetch only enabled schedules
        fetchEnabledSchedules(latch);

        //fetch the resource type
        fetchResourceType(latch, column);

        //cleanup
        for (Canvas child : recentMeasurementsContent.getChildren()) {
            child.destroy();
        }
        recentMeasurementsContent.addChild(column);
        recentMeasurementsContent.markForRedraw();
    }

    private void fetchEnabledSchedules(final CountDownLatch latch) {
        MeasurementScheduleCriteria criteria = new MeasurementScheduleCriteria();
        criteria.addFilterEnabled(true);
        criteria.fetchDefinition(true);
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        addFilterKey(criteria);
        GWTServiceLookup.getMeasurementDataService().findMeasurementSchedulesByCriteria(criteria,
            new AsyncCallback<PageList<MeasurementSchedule>>() {

                @Override
                public void onSuccess(PageList<MeasurementSchedule> result) {
                    enabledSchedules = result;
                    latch.countDown();
                }

                @Override
                public void onFailure(Throwable caught) {
                    latch.countDown();
                }
            });
    }

    protected void fetchResourceType(final CountDownLatch latch, final VLayout layout) {
        //locate resourceGroupRef
        ResourceGroupCriteria criteria = new ResourceGroupCriteria();
        criteria.addFilterId(context.getGroupId());
        // for autoclusters and autogroups we need to add more criteria
        if (context.isAutoCluster()) {
            criteria.addFilterVisible(false);
        } else if (context.isAutoGroup()) {
            criteria.addFilterVisible(false);
            criteria.addFilterPrivate(true);
        }

        criteria.fetchConfigurationUpdates(false);
        criteria.fetchExplicitResources(false);
        criteria.fetchGroupDefinition(false);
        criteria.fetchOperationHistories(false);

        //locate the resource group
        GWTServiceLookup.getResourceGroupService().findResourceGroupCompositesByCriteria(criteria,
            new AsyncCallback<PageList<ResourceGroupComposite>>() {
                @Override
                public void onFailure(Throwable caught) {
                    Log.debug("Error retrieving resource group composite for group [" + context.getGroupId() + "]:"
                        + caught.getMessage());
                    setRefreshing(false);
                    latch.countDown();
                }

                @Override
                public void onSuccess(PageList<ResourceGroupComposite> results) {
                    if (results.isEmpty()
                        || results.get(0).getResourceGroup().getGroupCategory() != GroupCategory.COMPATIBLE) {
                        renderChart = false;
                    }
                    latch.countDown();
                }
            });
    }

    @Override
    public void startRefreshCycle() {
        refreshTimer = AutoRefreshUtil.startRefreshCycleWithPageRefreshInterval(this, this, refreshTimer);

        //call out to 3rd party javascript lib
        BrowserUtility.graphSparkLines();
        recentMeasurementsContent.markForRedraw();
    }

    @Override
    protected void onDestroy() {
        AutoRefreshUtil.onDestroy(refreshTimer);

        super.onDestroy();
    }

    @Override
    public boolean isRefreshing() {
        return this.currentlyLoading;
    }

    @Override
    public void refresh() {
        if (!isRefreshing()) {
            loadData();
        }
    }

    protected void setRefreshing(boolean currentlyRefreshing) {
        this.currentlyLoading = currentlyRefreshing;
    }

    public static class ChartViewWindow extends Window {

        public ChartViewWindow(String title, String windowTitle, final GroupMetricsPortlet portlet) {
            super();
            if ((windowTitle != null) && (!windowTitle.trim().isEmpty())) {
                setTitle(windowTitle + ": " + title);
            } else {
                setTitle(CHART_TITLE + ": " + title);
            }
            setShowMinimizeButton(false);
            setShowMaximizeButton(false);
            setShowCloseButton(true);
            setIsModal(true);
            setShowModalMask(true);
            setWidth(950);
            setHeight(550);
            setShowResizer(true);
            setCanDragResize(true);
            centerInPage();

            addCloseClickHandler(new CloseClickHandler() {
                @Override
                public void onCloseClick(CloseClickEvent event) {
                    try {
                        ChartViewWindow.this.destroy();
                        portlet.refresh();

                    } catch (Throwable e) {
                        Log.warn("Cannot destroy chart display window.", e);
                    }
                }
            });

        }
    }

    protected void fetchEnabledMetrics(List<MeasurementSchedule> schedules, int[] definitionArrayIds,
        final String[] displayOrder, final Map<String, MeasurementDefinition> measurementDefMap, final VLayout layout) {
        GWTServiceLookup.getMeasurementDataService().findDataForCompatibleGroup(context.getGroupId(),
            definitionArrayIds, CustomDateRangeState.getInstance().getStartTime(),
            CustomDateRangeState.getInstance().getEndTime(), 60,
            new AsyncCallback<List<List<MeasurementDataNumericHighLowComposite>>>() {
                @Override
                public void onFailure(Throwable caught) {
                    Log.debug("Error retrieving recent metrics charting data for group [" + context.getGroupId() + "]:"
                        + caught.getMessage());
                    setRefreshing(false);
                }

                @Override
                public void onSuccess(List<List<MeasurementDataNumericHighLowComposite>> results) {
                    renderData(results, displayOrder, measurementDefMap, layout);
                }
            });
    }

    protected void renderData(List<List<MeasurementDataNumericHighLowComposite>> results, String[] displayOrder,
        Map<String, MeasurementDefinition> measurementDefMap, VLayout layout) {
        if (!results.isEmpty() && !measurementDefMap.isEmpty()) {
            boolean someChartedData = false;

            layout.setWidth100();

            //iterate over the retrieved charting data
            for (int index = 0; index < displayOrder.length; index++) {
                //retrieve the correct measurement definition
                final MeasurementDefinition md = measurementDefMap.get(displayOrder[index]);

                //load the data results for the given metric definition
                List<MeasurementDataNumericHighLowComposite> data = results.get(index);

                //locate last and minimum values.
                double lastValue = -1;
                double minValue = Double.MAX_VALUE;
                //collapse the data into comma delimited list for consumption by third party javascript library(jquery.sparkline)
                String commaDelimitedList = "";
                for (MeasurementDataNumericHighLowComposite d : data) {
                    if ((!Double.isNaN(d.getValue())) && (!String.valueOf(d.getValue()).contains("NaN"))) {
                        commaDelimitedList += d.getValue() + ",";
                        if (d.getValue() < minValue) {
                            minValue = d.getValue();
                        }
                        lastValue = d.getValue();
                    }
                }
                DynamicForm row = new DynamicForm();
                row.setNumCols(3);
                row.setColWidths(65, "*", 100);
                row.setWidth100();
                row.setAutoHeight();
                row.setOverflow(Overflow.VISIBLE);
                HTMLFlow graph = new HTMLFlow();
                String contents = "<span id='sparkline_" + index + "' class='dynamicsparkline' width='0' " + "values='"
                    + commaDelimitedList + "'>...</span>";
                graph.setContents(contents);
                graph.setContentsType(ContentsType.PAGE);
                //disable scrollbars on span
                graph.setScrollbarSize(0);

                CanvasItem graphContainer = new CanvasItem();
                graphContainer.setShowTitle(false);
                graphContainer.setHeight(16);
                graphContainer.setWidth(60);
                graphContainer.setCanvas(graph);

                final String title = md.getDisplayName();
                LinkItem link = new LinkItem();
                link.setLinkTitle(title);
                link.setShowTitle(false);
                link.setClipValue(false);
                link.setWrap(true);
                if (!BrowserUtility.isBrowserPreIE9()) {

                    link.addClickHandler(new ClickHandler() {
                        @Override
                        public void onClick(ClickEvent event) {
                            showPopupWithChart(title, md);
                        }
                    });
                } else {
                    link.disable();
                }

                //Value
                String convertedValue = AbstractActivityView.convertLastValueForDisplay(lastValue, md);
                StaticTextItem value = AbstractActivityView.newTextItem(convertedValue);
                value.setVAlign(VerticalAlignment.TOP);
                value.setAlign(Alignment.RIGHT);
                value.setWidth("100%");

                row.setItems(graphContainer, link, value);

                //if graph content returned
                if ((!md.getName().trim().contains("Trait.")) && (lastValue != -1)) {
                    layout.addMember(row);
                    someChartedData = true;
                }
            }
            if (!someChartedData) {// when there are results but no chartable entries.
                DynamicForm row = getEmptyDataForm();
                layout.addMember(row);
            } else {
                //insert see more link
                DynamicForm row = new DynamicForm();
                String link = getSeeMoreLink();
                AbstractActivityView.addSeeMoreLink(row, link, layout);
            }
            //call out to 3rd party javascript lib
            new Timer() {
                @Override
                public void run() {
                    BrowserUtility.graphSparkLines();
                }
            }.schedule(200);

        } else {
            DynamicForm row = getEmptyDataForm();
            layout.addMember(row);
        }
        setRefreshing(false);
    }

    protected void showPopupWithChart(final String title, final MeasurementDefinition md) {
        ChartViewWindow window = new ChartViewWindow(title, "", refreshablePortlet);
        CompositeGroupD3GraphListView graph = new CompositeGroupD3MultiLineGraph(context, md.getId());
        window.addItem(graph);
        graph.populateData();
        window.show();
    }

    protected DynamicForm getEmptyDataForm() {
        return AbstractActivityView.createEmptyDisplayRow(AbstractActivityView.RECENT_MEASUREMENTS_GROUP_NONE);
    }

    protected String getSeeMoreLink() {
        return LinkManager.getGroupMonitoringGraphsLink(context);
    }

    protected MeasurementScheduleCriteria addFilterKey(MeasurementScheduleCriteria criteria) {
        criteria.addFilterResourceGroupId(context.getGroupId());
        return criteria;
    }
}
