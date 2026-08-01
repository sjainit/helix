/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.helix.guardrail;

import org.apache.helix.HelixDataAccessor;
import org.apache.helix.model.IdealState;

/**
 * Immutable bundle of everything a {@link GuardrailRule} needs to evaluate a proposed mutation.
 * <p>
 * The context is intentionally small: it carries the cluster name, a read-only
 * {@link HelixDataAccessor} for the target cluster, and the target instance name for
 * instance-scoped operations. When rules need the actual object a mutation would write (rather than
 * only current cluster state read through the accessor), that <em>proposed</em> object is supplied
 * here as well; {@code proposedIdealState} is such a field. New object types are added the same way,
 * through the {@link Builder}, without breaking existing rules.
 */
public class GuardrailContext {
  private final String clusterName;
  private final HelixDataAccessor dataAccessor;
  private final String instanceName;
  private final IdealState proposedIdealState;

  private GuardrailContext(Builder builder) {
    this.clusterName = builder.clusterName;
    this.dataAccessor = builder.dataAccessor;
    this.instanceName = builder.instanceName;
    this.proposedIdealState = builder.proposedIdealState;
  }

  public String getClusterName() {
    return clusterName;
  }

  public HelixDataAccessor getDataAccessor() {
    return dataAccessor;
  }

  /** The instance targeted by an instance-scoped mutation, or {@code null} if not applicable. */
  public String getInstanceName() {
    return instanceName;
  }

  /**
   * The ideal state a mutation proposes to write, or {@code null} if the operation does not create
   * or replace one. Rules validate the to-be-written IdealState from here rather than from ZK, since
   * the object does not exist in ZK yet at pre-validation time.
   */
  public IdealState getProposedIdealState() {
    return proposedIdealState;
  }

  public static Builder newBuilder(String clusterName) {
    return new Builder(clusterName);
  }

  public static final class Builder {
    private final String clusterName;
    private HelixDataAccessor dataAccessor;
    private String instanceName;
    private IdealState proposedIdealState;

    private Builder(String clusterName) {
      this.clusterName = clusterName;
    }

    public Builder dataAccessor(HelixDataAccessor dataAccessor) {
      this.dataAccessor = dataAccessor;
      return this;
    }

    public Builder instanceName(String instanceName) {
      this.instanceName = instanceName;
      return this;
    }

    public Builder proposedIdealState(IdealState proposedIdealState) {
      this.proposedIdealState = proposedIdealState;
      return this;
    }

    public GuardrailContext build() {
      return new GuardrailContext(this);
    }
  }
}
