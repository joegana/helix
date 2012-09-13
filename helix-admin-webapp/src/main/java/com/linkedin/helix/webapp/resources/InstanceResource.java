/**
 * Copyright (C) 2012 LinkedIn Inc <opensource@linkedin.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.helix.webapp.resources;

import java.io.IOException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.StringRepresentation;
import org.restlet.resource.Variant;

import com.linkedin.helix.HelixException;
import com.linkedin.helix.PropertyKey;
import com.linkedin.helix.PropertyKey.Builder;
import com.linkedin.helix.manager.zk.ZkClient;
import com.linkedin.helix.tools.ClusterSetup;
import com.linkedin.helix.webapp.RestAdminApplication;

public class InstanceResource extends Resource
{
  private final static Logger LOG = Logger.getLogger(InstanceResource.class);

  public InstanceResource(Context context, Request request, Response response)
  {
    super(context, request, response);
    getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    getVariants().add(new Variant(MediaType.APPLICATION_JSON));
  }

  @Override
  public boolean allowGet()
  {
    return true;
  }

  @Override
  public boolean allowPost()
  {
    return true;
  }

  @Override
  public boolean allowPut()
  {
    return false;
  }

  @Override
  public boolean allowDelete()
  {
    return true;
  }

  @Override
  public Representation represent(Variant variant)
  {
    StringRepresentation presentation = null;
    try
    {
      presentation = getInstanceRepresentation();
    }
    catch (Exception e)
    {
      String error = ClusterRepresentationUtil.getErrorAsJsonStringFromException(e);
      presentation = new StringRepresentation(error, MediaType.APPLICATION_JSON);

      LOG.error("", e);
    }
    return presentation;
  }

  StringRepresentation getInstanceRepresentation() throws JsonGenerationException,
      JsonMappingException,
      IOException
  {
    String clusterName = (String) getRequest().getAttributes().get("clusterName");
    String instanceName = (String) getRequest().getAttributes().get("instanceName");
    Builder keyBuilder = new PropertyKey.Builder(clusterName);
    ZkClient zkClient =
        (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);

    String message =
        ClusterRepresentationUtil.getClusterPropertyAsString(zkClient,
                                                             clusterName,
                                                             MediaType.APPLICATION_JSON,
                                                             keyBuilder.instanceConfig(instanceName));

    StringRepresentation representation =
        new StringRepresentation(message, MediaType.APPLICATION_JSON);

    return representation;
  }

  @Override
  public void acceptRepresentation(Representation entity)
  {
    try
    {
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      String instanceName = (String) getRequest().getAttributes().get("instanceName");

      JsonParameters jsonParameters = new JsonParameters(entity);
      String command = jsonParameters.getCommand();
      if (command.equalsIgnoreCase(ClusterSetup.enableInstance))
      {
        jsonParameters.verifyCommand(ClusterSetup.enableInstance);

        boolean enabled =
            Boolean.parseBoolean(jsonParameters.getParameter(JsonParameters.ENABLED));

        ZkClient zkClient =
            (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
        ClusterSetup setupTool = new ClusterSetup(zkClient);
        setupTool.getClusterManagementTool().enableInstance(clusterName,
                                                            instanceName,
                                                            enabled);
      }
      else if (command.equalsIgnoreCase(ClusterSetup.enablePartition))
      {
        jsonParameters.verifyCommand(ClusterSetup.enablePartition);
 
        boolean enabled =
             Boolean.parseBoolean(jsonParameters.getParameter(JsonParameters.ENABLED));

        String[] partitions = 
            jsonParameters.getParameter(JsonParameters.PARTITION).split(";");
        String resource = 
            jsonParameters.getParameter(JsonParameters.RESOURCE);

        ZkClient zkClient =
            (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
        ClusterSetup setupTool = new ClusterSetup(zkClient);
        setupTool.getClusterManagementTool().enablePartition(enabled,
                                                             clusterName,
                                                             instanceName,
                                                             resource,
                                                             Arrays.asList(partitions));
      }
      else if (command.equalsIgnoreCase(ClusterSetup.resetPartition))
      {
        jsonParameters.verifyCommand(ClusterSetup.resetPartition);

        String resource = 
            jsonParameters.getParameter(JsonParameters.RESOURCE);

        ZkClient zkClient =
            (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
        ClusterSetup setupTool = new ClusterSetup(zkClient);
        String[] partitionNames = 
            jsonParameters.getParameter(JsonParameters.PARTITION).split("\\s+");
        setupTool.getClusterManagementTool()
                 .resetPartition(clusterName,
                                 instanceName,
                                 resource,
                                 Arrays.asList(partitionNames));
      }
      else if (command.equalsIgnoreCase(ClusterSetup.resetInstance))
      {
        jsonParameters.verifyCommand(ClusterSetup.resetInstance);

        ZkClient zkClient =
            (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);
        ClusterSetup setupTool = new ClusterSetup(zkClient);
        setupTool.getClusterManagementTool().resetInstance(clusterName,
                                                           Arrays.asList(instanceName));
      }
      else
      {
        throw new HelixException("Unsupported command: " + command
            + ". Should be one of [" + ClusterSetup.enableInstance + ", "
            + ClusterSetup.enablePartition + ", " + ClusterSetup.resetInstance + "]");
      }

      getResponse().setEntity(getInstanceRepresentation());
      getResponse().setStatus(Status.SUCCESS_OK);
    }
    catch (Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
                              MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
      LOG.error("", e);
    }
  }

  @Override
  public void removeRepresentations()
  {
    try
    {
      String clusterName = (String) getRequest().getAttributes().get("clusterName");
      String instanceName = (String) getRequest().getAttributes().get("instanceName");
      ZkClient zkClient =
          (ZkClient) getContext().getAttributes().get(RestAdminApplication.ZKCLIENT);

      ClusterSetup setupTool = new ClusterSetup(zkClient);
      setupTool.dropInstanceFromCluster(clusterName, instanceName);
      getResponse().setStatus(Status.SUCCESS_OK);
    }
    catch (Exception e)
    {
      getResponse().setEntity(ClusterRepresentationUtil.getErrorAsJsonStringFromException(e),
                              MediaType.APPLICATION_JSON);
      getResponse().setStatus(Status.SUCCESS_OK);
      LOG.error("Error in remove", e);
    }
  }
}
