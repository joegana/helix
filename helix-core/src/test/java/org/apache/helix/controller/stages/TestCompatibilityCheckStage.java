package org.apache.helix.controller.stages;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Arrays;
import java.util.List;

import org.apache.helix.Mocks;
import org.apache.helix.ZNRecord;
import org.apache.helix.PropertyKey.Builder;
import org.apache.helix.controller.pipeline.StageContext;
import org.apache.helix.controller.stages.CompatibilityCheckStage;
import org.apache.helix.controller.stages.ReadClusterDataStage;
import org.apache.helix.model.IdealState;
import org.apache.helix.model.LiveInstance;
import org.apache.helix.model.LiveInstance.LiveInstanceProperty;
import org.apache.helix.tools.DefaultIdealStateCalculator;
import org.testng.Assert;
import org.testng.annotations.Test;


public class TestCompatibilityCheckStage extends BaseStageTest
{
  private void prepare(String controllerVersion, String participantVersion)
  {
    List<String> instances = Arrays.asList("localhost_0", "localhost_1",
                                           "localhost_2", "localhost_3", "localhost_4");
    int partitions = 10;
    int replicas = 1;

    // set ideal state
    String resourceName = "testResource";
    ZNRecord record = DefaultIdealStateCalculator.calculateIdealState(
        instances, partitions, replicas, resourceName, "MASTER", "SLAVE");
    IdealState idealState = new IdealState(record);
    idealState.setStateModelDefRef("MasterSlave");
    
    Builder keyBuilder = accessor.keyBuilder();
    accessor.setProperty(keyBuilder.idealStates(resourceName), idealState);

    // set live instances
    record = new ZNRecord("localhost_0");
    if (participantVersion != null)
    {
      record.setSimpleField(LiveInstanceProperty.HELIX_VERSION.toString(), participantVersion);
    }
    LiveInstance liveInstance = new LiveInstance(record);
    liveInstance.setSessionId("session_0");
    accessor.setProperty(keyBuilder.liveInstance("localhost_0"), liveInstance);

    if (controllerVersion != null)
    {
      ((Mocks.MockManager)manager).setVersion(controllerVersion);
    }
    event.addAttribute("helixmanager", manager);
    runStage(event, new ReadClusterDataStage());
  }

  @Test
  public void testCompatible()
  {
    prepare("0.4.0", "0.4.0");
    CompatibilityCheckStage stage = new CompatibilityCheckStage();
    StageContext context = new StageContext();
    stage.init(context);
    stage.preProcess();
    try
    {
      stage.process(event);
    }
    catch (Exception e)
    {
      Assert.fail("Should not fail since versions are compatible");
    }
    stage.postProcess();
  }

  @Test
  public void testNullParticipantVersion()
  {
    prepare("0.4.0", null);
    CompatibilityCheckStage stage = new CompatibilityCheckStage();
    StageContext context = new StageContext();
    stage.init(context);
    stage.preProcess();
    try
    {
      stage.process(event);
    }
    catch (Exception e)
    {
      Assert.fail("Should not fail since only participant version is null");
    }
    stage.postProcess();
  }

  @Test
  public void testNullControllerVersion()
  {
    prepare(null, "0.4.0");
    CompatibilityCheckStage stage = new CompatibilityCheckStage();
    StageContext context = new StageContext();
    stage.init(context);
    stage.preProcess();
    try
    {
      stage.process(event);
      Assert.fail("Should fail since controller version is null");
    }
    catch (Exception e)
    {
      // OK
    }
    stage.postProcess();
  }

  @Test
  public void testControllerVersionLessThanParticipantVersion()
  {
    prepare("0.2.12", "0.3.4");
    CompatibilityCheckStage stage = new CompatibilityCheckStage();
    StageContext context = new StageContext();
    stage.init(context);
    stage.preProcess();
    try
    {
      stage.process(event);
      Assert.fail("Should fail since controller primary version is less than participant primary version");
    }
    catch (Exception e)
    {
      // OK
    }
    stage.postProcess();
  }

  @Test
  public void testIncompatible()
  {
    prepare("0.4.12", "0.3.4");
    CompatibilityCheckStage stage = new CompatibilityCheckStage();
    StageContext context = new StageContext();
    stage.init(context);
    stage.preProcess();
    try
    {
      stage.process(event);
      Assert.fail("Should fail since controller primary version is incompatible with participant primary version");
    }
    catch (Exception e)
    {
      // OK
    }
    stage.postProcess();
  }

}
