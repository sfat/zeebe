/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.broker.ZeebeBrokerContainer;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.event.Level;

public class UpgradeTest {

  private static String lastVersion = "0.22.0-alpha2";

  @Rule public TemporaryFolder temp = new TemporaryFolder();
  private ZeebeBrokerContainer zeebe;

  @Rule(order = Integer.MIN_VALUE)
  public TestWatcher watchman =
      new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
          if (zeebe != null) {
            System.out.print(zeebe.getLogs());
          }
        }
      };

  @BeforeClass
  public static void beforeClass() {
    lastVersion = System.getProperty("lastVersion");
  }

  @Test(timeout = 60 * 1000)
  public void shouldCompleteJob() {
    assertThat(lastVersion).isNotEmpty();
    ZeebeClient client = startZeebe(lastVersion);

    final String processId = "process";
    final String resourceName = processId + ".bpmn";
    final String task = "task";

    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(task, t -> t.zeebeTaskType("test"))
            .endEvent()
            .done();

    // when
    client.newDeployCommand().addWorkflowModel(workflow, resourceName).send().join();
    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();

    final ActivateJobsResponse jobsResponse =
        client.newActivateJobsCommand().jobType("test").maxJobsToActivate(1).send().join();
    zeebe.close();
    client.close();

    client = startZeebe("current-test");
    client.newCompleteCommand(jobsResponse.getJobs().get(0).getKey()).send().join();

    final String[] lines = zeebe.getLogs().split("\n");
    boolean foundCompleted = false;
    for (int i = lines.length - 1; i >= 0; --i) {
      if (lines[i].contains(String.format("\"elementId\":\"%s\"", task))
          && lines[i].contains("\"intent\":\"COMPLETED\"")) {
        foundCompleted = true;
        break;
      }
    }

    if (!foundCompleted) {
      for (String l : lines) {
        System.out.println(l);
      }
    }

    zeebe.close();
    assertThat(foundCompleted).isTrue();
  }

  private ZeebeClient startZeebe(final String version) {
    zeebe =
        new ZeebeBrokerContainer(version)
            .withFileSystemBind(temp.getRoot().getPath(), "/usr/local/zeebe/data")
            .withDebug(true)
            .withLogLevel(Level.DEBUG);

    zeebe.start();

    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(zeebe.getExternalAddress(ZeebePort.GATEWAY))
        .usePlaintext()
        .build();
  }
}
