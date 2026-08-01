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

package org.apache.helix.guardrail.rules;

import java.util.ArrayList;
import java.util.List;

import org.apache.helix.guardrail.GuardrailContext;
import org.apache.helix.guardrail.GuardrailRule;
import org.apache.helix.guardrail.ValidationResult;
import org.apache.helix.guardrail.Violation;
import org.apache.helix.model.IdealState;

/**
 * Guard rail that blocks creating a resource whose IdealState omits a field that is
 * <em>unconditionally</em> required for the resource to be valid and rebalanceable: the number of
 * partitions ({@code NUM_PARTITIONS}) and the state model definition reference
 * ({@code STATE_MODEL_DEF_REF}).
 * <p>
 * The plain {@code addResource} endpoint writes the posted IdealState straight to ZooKeeper without
 * ever calling {@link IdealState#isValid()}. A record that omits {@code NUM_PARTITIONS} is therefore
 * accepted as written; {@link IdealState#getNumPartitions()} then reports {@code -1}, the resource
 * has nothing to place, and the omission only surfaces later as a controller/rebalance failure.
 * Likewise an absent {@code STATE_MODEL_DEF_REF} leaves the controller with no state machine to
 * drive partition transitions. This rule rejects such a resource up front, naming every missing
 * field so the caller can fix them in one pass.
 * <p>
 * The check is deliberately limited to the mode-independent invariants that {@code isValid()}
 * enforces but this write path does not. Fields with a safe fallback ({@code REBALANCE_MODE} derives
 * from {@code IDEAL_STATE_MODE}) or that are only conditionally required ({@code REPLICAS} matters
 * for {@code SEMI_AUTO}) are intentionally not enforced here.
 */
public class RequiredIdealStateFieldsGuardrailRule implements GuardrailRule {
  public static final String RULE_ID = "RESOURCE_MISSING_REQUIRED_IDEAL_STATE_FIELD";

  @Override
  public String getId() {
    return RULE_ID;
  }

  @Override
  public ValidationResult validate(GuardrailContext context) {
    IdealState proposedIdealState = context.getProposedIdealState();
    if (proposedIdealState == null) {
      // Not a resource-creation mutation carrying an IdealState; nothing for this rule to certify.
      return ValidationResult.feasible();
    }

    String resourceName = proposedIdealState.getResourceName();
    List<Violation> violations = new ArrayList<>();

    // NUM_PARTITIONS: absent -> getNumPartitions() returns -1. A resource with no positive partition
    // count has nothing to place and can never be rebalanced.
    int numPartitions = proposedIdealState.getNumPartitions();
    if (numPartitions <= 0) {
      violations.add(Violation.newBuilder(RULE_ID)
          .resource(resourceName)
          .message(String.format(
              "Resource %s is missing a valid NUM_PARTITIONS (got %d). Set NUM_PARTITIONS to a "
                  + "positive value; a resource without partitions can never be rebalanced.",
              resourceName, numPartitions))
          .build());
    }

    // STATE_MODEL_DEF_REF: absent -> null. Without a state model the controller has no state machine
    // to drive partition transitions.
    String stateModelDefRef = proposedIdealState.getStateModelDefRef();
    if (stateModelDefRef == null || stateModelDefRef.isEmpty()) {
      violations.add(Violation.newBuilder(RULE_ID)
          .resource(resourceName)
          .message(String.format(
              "Resource %s is missing STATE_MODEL_DEF_REF. Set it to the name of the state model "
                  + "that defines the resource's states and transitions.", resourceName))
          .build());
    }

    // Feasible iff no required field is missing. force=true overrides any violation upstream.
    return ValidationResult.of(violations);
  }
}
