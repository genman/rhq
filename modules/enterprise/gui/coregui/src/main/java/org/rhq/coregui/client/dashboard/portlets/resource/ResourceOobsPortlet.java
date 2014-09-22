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
package org.rhq.coregui.client.dashboard.portlets.resource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.form.DynamicForm;
import com.smartgwt.client.widgets.form.fields.LinkItem;
import com.smartgwt.client.widgets.form.fields.StaticTextItem;
import com.smartgwt.client.widgets.form.fields.events.ClickEvent;
import com.smartgwt.client.widgets.form.fields.events.ClickHandler;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.common.EntityContext;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.criteria.ResourceCriteria;
import org.rhq.core.domain.dashboard.DashboardPortlet;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.composite.MeasurementOOBComposite;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.domain.resource.composite.ResourceComposite;
import org.rhq.core.domain.util.PageList;
import org.rhq.coregui.client.dashboard.Portlet;
import org.rhq.coregui.client.dashboard.PortletViewFactory;
import org.rhq.coregui.client.dashboard.portlets.PortletConfigurationEditorComponent.Constant;
import org.rhq.coregui.client.dashboard.portlets.groups.GroupOobsPortlet;
import org.rhq.coregui.client.gwt.GWTServiceLookup;
import org.rhq.coregui.client.inventory.common.detail.summary.AbstractActivityView;
import org.rhq.coregui.client.inventory.common.detail.summary.AbstractActivityView.ChartViewWindow;
import org.rhq.coregui.client.inventory.resource.detail.monitoring.D3GraphListView;
import org.rhq.coregui.client.inventory.resource.type.ResourceTypeRepository;
import org.rhq.coregui.client.util.GwtRelativeDurationConverter;
import org.rhq.coregui.client.util.Log;

/**This portlet allows the end user to customize the OOB display
 *
 * @author Simeon Pinder
 */
public class ResourceOobsPortlet extends GroupOobsPortlet {

    // A non-displayed, persisted identifier for the portlet
    public static final String KEY = "ResourceOobs";
    // A default displayed, persisted name for the portlet
    public static final String NAME = MSG.view_portlet_defaultName_resource_oobs();

    private int resourceId = -1;

    public ResourceOobsPortlet(int resourceId) {
        super(-1);
        this.resourceId = resourceId;
    }

    public static final class Factory implements PortletViewFactory {
        public static final PortletViewFactory INSTANCE = new Factory();

        public final Portlet getInstance(EntityContext context) {

            if (EntityContext.Type.Resource != context.getType()) {
                throw new IllegalArgumentException("Context [" + context + "] not supported by portlet");
            }

            return new ResourceOobsPortlet(context.getResourceId());
        }
    }

    /** Fetches OOB measurements and updates the DynamicForm instance with the latest N
     *  oob change details.
     */
    @Override
    protected void getRecentOobs() {
        final DashboardPortlet storedPortlet = this.portletWindow.getStoredPortlet();
        final Configuration portletConfig = storedPortlet.getConfiguration();
        final int resourceId = this.resourceId;

        //result count
        final String resultCount;
        String resultCountRaw = portletConfig.getSimpleValue(Constant.RESULT_COUNT, Constant.RESULT_COUNT_DEFAULT);
        resultCount = (resultCountRaw.trim().isEmpty()) ? Constant.RESULT_COUNT_DEFAULT :  resultCountRaw;

        ResourceCriteria criteria = new ResourceCriteria();
        criteria.addFilterId(resourceId);
        criteria.fetchResourceType(true);

        //locate the resource
        GWTServiceLookup.getResourceService().findResourceCompositesByCriteria(criteria,
                new AsyncCallback<PageList<ResourceComposite>>() {
                    @Override
                    public void onFailure(Throwable caught) {
                        Log.debug("Error retrieving resource resource composite for resource [" + resourceId + "]:"
                                + caught.getMessage());
                    }

                    @Override
                    public void onSuccess(PageList<ResourceComposite> results) {
                        if (!results.isEmpty()) {
                            final ResourceComposite resourceComposite = results.get(0);

                            final Resource resource = resourceComposite.getResource();
                            final ResourceType resourceType = resource.getResourceType();

                            ResourceTypeRepository.Cache.getInstance().getResourceTypes(
                                    resourceType.getId(),
                                    EnumSet.of(ResourceTypeRepository.MetadataType.measurements),
                                    new ResourceTypeRepository.TypeLoadedCallback() {
                                        public void onTypesLoaded(ResourceType type) {
                                            resource.setResourceType(type);
                                            //metric definitions
                                            final Set<MeasurementDefinition> definitions = type.getMetricDefinitions();

                                            //build id mapping for measurementDefinition instances Ex. Free Memory -> MeasurementDefinition[100071]
                                            HashMap<String, MeasurementDefinition> measurementDefMapTemp = new HashMap<String, MeasurementDefinition>();
                                            for (MeasurementDefinition definition : definitions) {
                                                if(null != definition){
                                                    measurementDefMapTemp.put(definition.getDisplayName(), definition);
                                                }
                                            }
                                            final HashMap<String, MeasurementDefinition> measurementDefMap = new HashMap<String, MeasurementDefinition>(measurementDefMapTemp);
                                            //bundle definition ids for asynch call.
                                            int[] definitionArrayIds = new int[definitions.size()];
                                            final String[] displayOrder = new String[definitions.size()];
                                            measurementDefMap.keySet().toArray(displayOrder);
                                            //sort the charting data ex. Free Memory, Free Swap Space,..System Load
                                            Arrays.sort(displayOrder);

                                            //organize definitionArrayIds for ordered request on server.
                                            int index = 0;
                                            for (String definitionToDisplay : displayOrder) {
                                                definitionArrayIds[index++] = measurementDefMap.get(definitionToDisplay)
                                                        .getId();
                                            }


                            GWTServiceLookup.getMeasurementDataService().getHighestNOOBsForResource(resourceId,
                                    Integer.valueOf(resultCount), new AsyncCallback<PageList<MeasurementOOBComposite>>() {
                                @Override
                                public void onFailure(Throwable caught) {
                                    Log.debug("Error retrieving out of bound metrics for resource [" + resourceId + "]:"
                                            + caught.getMessage());
                                    currentlyLoading = false;
                                }

                                @Override
                                public void onSuccess(PageList<MeasurementOOBComposite> result) {
                                    VLayout column = new VLayout();
                                    column.setHeight(10);
                                    column.setWidth100();
                                    if (!result.isEmpty()) {
                                        for (final MeasurementOOBComposite oob : result) {
                                            DynamicForm row = new DynamicForm();
                                            row.setWidth100();
                                            row.setNumCols(2);
                                            row.setColWidths("*", 100);

                                            final String title = oob.getScheduleName();
                                            LinkItem link = new LinkItem();
                                            link.setLinkTitle(title);
                                            link.setShowTitle(false);
                                            link.setClipValue(false);
                                            link.setWrap(true);
                                            link.addClickHandler(new ClickHandler() {
                                                @Override
                                                public void onClick(ClickEvent event) {
                                                    ChartViewWindow window = new ChartViewWindow(title);
                                                    D3GraphListView graphView = D3GraphListView
                                                            .createSingleGraph(resourceComposite.getResource(),
                                                                    oob.getDefinitionId(), true);

                                                    window.addItem(graphView);
                                                    window.show();
                                                }
                                            });

                                            StaticTextItem time = AbstractActivityView.newTextItem(GwtRelativeDurationConverter
                                                    .format(oob.getTimestamp()));

                                            row.setItems(link, time);
                                            column.addMember(row);
                                        }
                                        //insert see more link spinder(2/24/11): no page that displays all oobs... See More not possible.
                                    } else {
                                        DynamicForm row = AbstractActivityView
                                                .createEmptyDisplayRow(AbstractActivityView.RECENT_OOB_NONE);
                                        column.addMember(row);
                                    }
                                    recentOobContent.setContents("");
                                    for (Canvas child : recentOobContent.getChildren()) {
                                        child.destroy();
                                    }
                                    recentOobContent.addChild(column);
                                    currentlyLoading = false;
                                    recentOobContent.markForRedraw();
                                }
                            });
                        }
                    });
                        }
                    }
                });



    }
}