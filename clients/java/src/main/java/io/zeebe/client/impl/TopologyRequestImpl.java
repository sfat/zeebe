/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.client.api.commands.TopologyRequestStep1;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;
import java.util.ArrayList;
import java.util.List;

public class TopologyRequestImpl implements TopologyRequestStep1 {

  private GatewayBlockingStub blockingStub;

  public TopologyRequestImpl(final GatewayBlockingStub blockingStub) {
    this.blockingStub = blockingStub;
  }

  @Override
  public ZeebeFuture<Topology> send() {
    final HealthRequest request = HealthRequest.getDefaultInstance();
    final HealthResponse response = blockingStub.health(request);

    // TODO: Issue #1125: Async clients - https://github.com/zeebe-io/zeebe/issues/1125
    final ZeebeClientFutureImpl<Topology> future = new ZeebeClientFutureImpl<>();
    future.complete(
        new Topology() {
          @Override
          public List<BrokerInfo> getBrokers() {
            final ArrayList<BrokerInfo> brokers = new ArrayList<>();
            for (final GatewayOuterClass.BrokerInfo broker : response.getBrokersList()) {
              brokers.add(new BrokerInfoImpl(broker));
            }
            return brokers;
          }
        });

    return future;
  }
}