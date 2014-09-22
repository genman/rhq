/*
 * RHQ Management Platform
 * Copyright (C) 2005-2013 Red Hat, Inc.
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
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package org.rhq.enterprise.server.configuration;

import java.util.Map;

import javax.ejb.Remote;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PluginConfigurationUpdate;
import org.rhq.core.domain.configuration.ResourceConfigurationUpdate;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.configuration.group.GroupPluginConfigurationUpdate;
import org.rhq.core.domain.configuration.group.GroupResourceConfigurationUpdate;
import org.rhq.core.domain.criteria.GroupPluginConfigurationUpdateCriteria;
import org.rhq.core.domain.criteria.GroupResourceConfigurationUpdateCriteria;
import org.rhq.core.domain.util.PageList;
import org.rhq.enterprise.server.resource.ResourceNotFoundException;

/**
 * The configuration manager which allows you to request resource configuration changes, view current resource
 * configuration and previous update history and view/edit plugin configuration.
 *
 * @author John Mazzitelli
 * @author Ian Springer
 */
@Remote
public interface ConfigurationManagerRemote {

    /**
     * @param subject
     * @param criteria
     * @return not null
     *
     * @see {@link #getLatestPluginConfigurationUpdate(Subject, int)}
     *
     * @since 4.10
     */
    PageList<GroupPluginConfigurationUpdate> findGroupPluginConfigurationUpdatesByCriteria(Subject subject,
        GroupPluginConfigurationUpdateCriteria criteria);

    /**
     * Note that when setting {@link GroupPluginConfigurationUpdateCriteria#fetchConfigurationUpdates(boolean)} to true,
     * the current resource configurations are returned.  The "current" configuration is the one most recently reported
     * and stored server-side, the agent is not queried.  Recent, unreported changes in the "live" configuration will
     * not be reported.  Querying the agent is slower and not typically necessary, as configuration changes infrequently.
     *
     * @param subject
     * @param criteria
     * @return not null
     *
     * @see {@link #getLatestResourceConfigurationUpdate(Subject, int)}
     * @see {@link #getLiveResourceConfiguration(Subject, int, boolean)}
     *
     * @since 4.10
     */
    PageList<GroupResourceConfigurationUpdate> findGroupResourceConfigurationUpdatesByCriteria(Subject subject,
        GroupResourceConfigurationUpdateCriteria criteria);

    /**
     * @param subject
     * @param configurationUpdateId
     * @return object
     * @deprecated use {@link #findGroupPluginConfigurationUpdatesByCriteria(org.rhq.core.domain.auth.Subject, org.rhq.core.domain.criteria.GroupPluginConfigurationUpdateCriteria)}
     */
    @Deprecated
    GroupPluginConfigurationUpdate getGroupPluginConfigurationUpdate(Subject subject, int configurationUpdateId);

    /**
     * @param subject
     * @param configurationUpdateId
     * @return object
     * @deprecated use {@link #findGroupResourceConfigurationUpdatesByCriteria(org.rhq.core.domain.auth.Subject, org.rhq.core.domain.criteria.GroupResourceConfigurationUpdateCriteria)}
     */
    @Deprecated
    GroupResourceConfigurationUpdate getGroupResourceConfigurationUpdate(Subject subject, int configurationUpdateId);

    /**
     * Get the "current" plugin configuration for the {@link Resource} with the given id, or <code>null</code> if the
     * resource's plugin configuration is not yet initialized.  The "current" configuration is that which has been
     * most recently reported and stored server-side, the agent is not queried.
     *
     * @param  subject     the user who wants to see the information
     * @param  resourceId a {@link Resource} id
     *
     * @return the current plugin configuration for the {@link Resource} with the given id, or <code>null</code> if the
     *         resource's plugin configuration is not yet initialized
     *
     * @see {@link #getLatestPluginConfigurationUpdate(Subject, int)}
     */
    Configuration getPluginConfiguration(Subject subject, int resourceId);

    /**
     * Get the "current" resource configuration for the {@link Resource} with the given id, or <code>null</code> if the
     * resource's resource configuration is not yet initialized.  The "current" configuration is that which has been
     * most recently reported and stored server-side, the agent is not queried.
     *
     * @param  subject     the user who wants to see the information
     * @param  resourceId  a {@link Resource} id
     *
     * @return the current resource configuration for the {@link Resource} with the given id, or <code>null</code> if the
     *         resource's resource configuration is not yet initialized
     *
     * @see {@link #getLatestResourceConfigurationUpdate(Subject, int)}
     * @see {@link #getLiveResourceConfiguration(Subject, int, boolean)}
     */
    Configuration getResourceConfiguration(Subject subject, int resourceId);

    /**
     * Get the {@link PluginConfigurationUpdate} for the "current" plugin configuration for the {@link Resource} with
     * the given id.  This is the same as {@link #getPluginConfiguration(Subject, int)} but returns the full update
     * information as opposed to just the configuration.
     *
     * @param  subject     the user who wants to see the information
     * @param  resourceId a {@link Resource} id
     *
     * @return the {@link PluginConfigurationUpdate} for the "current" plugin configuration
     *
     * @see {@link #getPluginConfiguration(Subject, int)}
     */
    PluginConfigurationUpdate getLatestPluginConfigurationUpdate(Subject subject, int resourceId);

    /**
     * Get the most recent attainable resource configuration for the {@link Resource} with the given id.  Retrieves
     * and returns live resource configuration from the Agent, if possible.  Otherwise returns the most recent
     * server-side configuration (from the database).  If the live availability differs from the server-side
     * the database will be updated to match the live configuration.  This can return <code>null</code> if an initial
     * resource configuration has yet to be stored in the database and a live configuration can not be retrieved (e.g.
     * in the case the agent or resource is down).
     * </p>
     * This differs from {@link #getLiveResourceConfiguration(Subject, int, boolean)} in that it will return the latest
     * stored configuration if the agent can not be queried.  This differs from {@link #getResourceConfiguration(Subject, int)}
     * in that it will attempt to query the agent to capture the "live" configuration.
     *
     * @param  subject     the user who wants to see the information
     * @param  resourceId a {@link Resource} id
     *
     * @return the current configuration (along with additional information about the configuration) for the
     *         {@link Resource} with the given id, or <code>null</code> if the resource's configuration is not yet
     *         initialized and its live configuration could not be determined.  The configuration can be accessed
     *         via {@link ResourceConfigurationUpdate#getConfiguration()}.
     *
     * @see {@link #getLiveResourceConfiguration(Subject, int, boolean)}
     * @see {@link #getResourceConfiguration(Subject, int)}
     */
    ResourceConfigurationUpdate getLatestResourceConfigurationUpdate(Subject subject, int resourceId);

    /**
     * Get whether the the specified resource is in the process of updating its configuration.
     * @param subject          The logged in user's subject.
     * @param resourceId       A resource id.
     * @return True if in progress, else False.
     */
    boolean isResourceConfigurationUpdateInProgress(Subject subject, int resourceId);

    /**
     * @param subject
     * @param resourceGroupId
     * @return True if in progress, else False
     */
    boolean isGroupResourceConfigurationUpdateInProgress(Subject subject, int resourceGroupId);

    /**
     * Schedules jobs to update the plugin configuration of resources in a compatible group.
     *
     * @param subject logged in user
     * @param compatibleGroupId the compatible group id
     * @param pluginConfigurationUpdate {@link Configuration} objects mapped by resource id
     * @return the {@link GroupPluginConfigurationUpdate} id
     */
    int scheduleGroupPluginConfigurationUpdate(Subject subject, int compatibleGroupId,
        Map<Integer, Configuration> pluginConfigurationUpdate);

    /**
     * @param subject
     * @param compatibleGroupId
     * @param newResourceConfigurationMap
     * @return GroupResourceConfigurationUpdate id
     */
    int scheduleGroupResourceConfigurationUpdate(Subject subject, int compatibleGroupId,
        Map<Integer, Configuration> newResourceConfigurationMap);

    /**
     * Updates the plugin configuration used to connect and communicate with the resource. The given <code>
     * newConfiguration</code> is usually a modified version of a configuration returned by
     * {@link #getPluginConfiguration(Subject, int)}.
     *
     * @param  subject          The logged in user's subject.
     * @param  resourceId       a {@link Resource} id
     * @param  newConfiguration the new plugin configuration
     *
     * @return the plugin configuration update item corresponding to this request
     * @throws ResourceNotFoundException
     * @throws PermissionException If the caller lacks MODIFY permission on the Resource.
     * @throws BadArgumentException If attempting to change a readOnly property value.
     */
    PluginConfigurationUpdate updatePluginConfiguration(Subject subject, int resourceId, Configuration newConfiguration)
        throws ResourceNotFoundException;

    /**
     * This method is called when a user has requested to change the resource configuration for an existing resource.
     *
     * <p>This will not wait for the agent to finish the configuration update. This will return after the request is
     * sent.</p>
     *
     * @param  subject          The logged in user's subject.
     * @param  resourceId       identifies the resource to be updated
     * @param  newConfiguration the resource's desired new configuration
     *
     * @return the resource configuration update item corresponding to this request
     * @throws ResourceNotFoundException
     * @throws ConfigurationUpdateStillInProgressException
     * @throws PermissionException If the caller lacks CONFIGURE_WRITE permission on the Resource.
     * @throws BadArgumentException If attempting to change a readOnly property value.
     */
    ResourceConfigurationUpdate updateResourceConfiguration(Subject subject, int resourceId,
        Configuration newConfiguration) throws ResourceNotFoundException, ConfigurationUpdateStillInProgressException;

    /**
     * Get the "live" resource configuration for the {@link Resource} with the given id.  The live configuration is
     * that of the resource at the time this call is made.  The agent is queried and will collect the configuration
     * by inspecting the resource.
     * </p>
     * Unlike {@link #getLatestResourceConfigurationUpdate(Subject, int)} this method has no affect on the "current"
     * resource configuration stored server-side.
     *
     * @param  subject        the user who wants to see the information
     * @param  resourceId     a {@link Resource} id
     * @param  pingAgentFirst true if the underlying Agent should be pinged successfully before attempting to retrieve
     *                        the configuration, or false otherwise
     *
     * @return the "live" resource configuration, or null if the "live" configuration can not be determined due to
     *         agent or resource being unavailable.
     *
     * @see {@link #getLatestResourceConfigurationUpdate(Subject, int)}
     * @see {@link #getLiveResourceConfiguration(Subject, int, boolean)}
     */
    Configuration getLiveResourceConfiguration(Subject subject, int resourceId, boolean pingAgentFirst)
        throws Exception;

    /**
     * Return the resource configuration definition for the {@link org.rhq.core.domain.resource.ResourceType} with the
     * specified id.
     *
     * @param  subject         the user who is requesting the resource configuration definition
     * @param  resourceTypeId identifies the resource type whose resource configuration definition is being requested
     *
     * @return the resource configuration definition for the {@link org.rhq.core.domain.resource.ResourceType} with the
     *         specified id, or <code>null</code> if the ResourceType does not define a resource configuration
     */
    ConfigurationDefinition getResourceConfigurationDefinitionForResourceType(Subject subject, int resourceTypeId);

    /**
     * Return the resource configuration definition for the {@link org.rhq.core.domain.resource.ResourceType} with the
     * specified id. The templates will be loaded in the definition returned from this call.
     *
     * @param  subject         the user who is requesting the resource configuration definition
     * @param  resourceTypeId identifies the resource type whose resource configuration definition is being requested
     *
     * @return the resource configuration definition for the {@link org.rhq.core.domain.resource.ResourceType} with the
     *         specified id, or <code>null</code> if the ResourceType does not define a resource configuration
     */
    ConfigurationDefinition getResourceConfigurationDefinitionWithTemplatesForResourceType(Subject subject,
        int resourceTypeId);

    /**
     * Return the plugin configuration definition for the {@link org.rhq.core.domain.resource.ResourceType} with the
     * specified id.
     *
     * @param  subject         the user who is requesting the plugin configuration definition
     * @param  resourceTypeId identifies the resource type whose plugin configuration definition is being requested
     *
     * @return the plugin configuration definition for the {@link org.rhq.core.domain.resource.ResourceType} with the
     *         specified id, or <code>null</code> if the ResourceType does not define a plugin configuration
     */
    ConfigurationDefinition getPluginConfigurationDefinitionForResourceType(Subject subject, int resourceTypeId);

    /**
     * Return the deploy configuration definition for the {@link org.rhq.core.domain.content.PackageType} with the
     * specified id.
     *
     * @param  subject        the user who is requesting the plugin configuration definition
     * @param  packageTypeId  identifies the package type whose configuration definition is being requested
     *
     * @return the  the deploy configuration definition for the {@link org.rhq.core.domain.content.PackageType} with the
     * specified id.
     */
    ConfigurationDefinition getPackageTypeConfigurationDefinition(Subject subject, int packageTypeId);

    /**
     * @param subject
     * @param resourceId
     * @param configuration
     * @param fromStructured
     * @return the translated configuration
     * @throws ResourceNotFoundException
     * @deprecated this feature was never full implemented and will be removed.
     */
    @Deprecated
    Configuration translateResourceConfiguration(Subject subject, int resourceId, Configuration configuration,
        boolean fromStructured) throws ResourceNotFoundException;
}
