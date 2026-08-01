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

import java.util.List;

import org.apache.helix.guardrail.GuardrailContext;
import org.apache.helix.guardrail.ValidationResult;
import org.apache.helix.guardrail.Violation;
import org.apache.helix.model.IdealState;
import org.apache.helix.zookeeper.datamodel.ZNRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link RequiredIdealStateFieldsGuardrailRule}. The rule is pure input validation on
 * the proposed {@link IdealState}, so the tests build real IdealState objects rather than mocking a
 * data accessor.
 */
public class TestRequiredIdealStateFieldsGuardrailRule {
  private static final String RESOURCE = "testResource";

  private final RequiredIdealStateFieldsGuardrailRule rule =
      new RequiredIdealStateFieldsGuardrailRule();

  @Test
  public void testNullProposedIdealStateIsFeasible() {
    // No IdealState in the context: the mutation is not resource creation, so the rule abstains.
    GuardrailContext context = GuardrailContext.newBuilder("cluster").build();
    ValidationResult result = rule.validate(context);
    Assert.assertTrue(result.isFeasible());
    Assert.assertTrue(result.getViolations().isEmpty());
  }

  @Test
  public void testValidIdealStateIsFeasible() {
    IdealState idealState = new IdealState(RESOURCE);
    idealState.setNumPartitions(4);
    idealState.setStateModelDefRef("MasterSlave");

    ValidationResult result = validate(idealState);

    Assert.assertTrue(result.isFeasible());
    Assert.assertTrue(result.getViolations().isEmpty());
  }

  @Test
  public void testMissingNumPartitionsIsInfeasible() {
    // NUM_PARTITIONS absent -> getNumPartitions() returns -1.
    IdealState idealState = new IdealState(RESOURCE);
    idealState.setStateModelDefRef("MasterSlave");

    ValidationResult result = validate(idealState);

    assertSingleViolationFor(result, "NUM_PARTITIONS");
  }

  @Test
  public void testZeroNumPartitionsIsInfeasible() {
    IdealState idealState = new IdealState(RESOURCE);
    idealState.setNumPartitions(0);
    idealState.setStateModelDefRef("MasterSlave");

    ValidationResult result = validate(idealState);

    assertSingleViolationFor(result, "NUM_PARTITIONS");
  }

  @Test
  public void testNegativeNumPartitionsIsInfeasible() {
    IdealState idealState = new IdealState(RESOURCE);
    idealState.setNumPartitions(-5);
    idealState.setStateModelDefRef("MasterSlave");

    ValidationResult result = validate(idealState);

    assertSingleViolationFor(result, "NUM_PARTITIONS");
  }

  @Test
  public void testMissingStateModelDefRefIsInfeasible() {
    // STATE_MODEL_DEF_REF absent -> getStateModelDefRef() returns null.
    IdealState idealState = new IdealState(RESOURCE);
    idealState.setNumPartitions(4);

    ValidationResult result = validate(idealState);

    assertSingleViolationFor(result, "STATE_MODEL_DEF_REF");
  }

  @Test
  public void testEmptyStateModelDefRefIsInfeasible() {
    IdealState idealState = new IdealState(RESOURCE);
    idealState.setNumPartitions(4);
    idealState.setStateModelDefRef("");

    ValidationResult result = validate(idealState);

    assertSingleViolationFor(result, "STATE_MODEL_DEF_REF");
  }

  @Test
  public void testBothMissingReportsBothViolations() {
    // A bare IdealState with neither required field: the rule reports both in one pass.
    IdealState idealState = new IdealState(RESOURCE);

    ValidationResult result = validate(idealState);

    Assert.assertFalse(result.isFeasible());
    Assert.assertEquals(result.getViolations().size(), 2);
    Assert.assertTrue(messageMentions(result.getViolations(), "NUM_PARTITIONS"));
    Assert.assertTrue(messageMentions(result.getViolations(), "STATE_MODEL_DEF_REF"));
    for (Violation violation : result.getViolations()) {
      Assert.assertEquals(violation.getRuleId(), RequiredIdealStateFieldsGuardrailRule.RULE_ID);
      Assert.assertEquals(violation.getResourceName(), RESOURCE);
    }
  }

  /**
   * Reproduces the real-world record that motivated this rule: a WAGED IdealState that carries a
   * state model and rebalancer settings but omits NUM_PARTITIONS. Exactly one violation, for the
   * missing partition count, must be reported.
   */
  @Test
  public void testRecordMissingNumPartitionsIsInfeasible() {
    ZNRecord record = new ZNRecord("10129");
    record.setSimpleField("IDEAL_STATE_MODE", "AUTO_REBALANCE");
    record.setSimpleField("INSTANCE_GROUP_TAG", "TAG_10129");
    record.setSimpleField("MIN_ACTIVE_REPLICAS", "2");
    record.setSimpleField("REBALANCER_CLASS_NAME",
        "org.apache.helix.controller.rebalancer.waged.WagedRebalancer");
    record.setSimpleField("REBALANCE_MODE", "FULL_AUTO");
    record.setSimpleField("REPLICAS", "3");
    record.setSimpleField("STATE_MODEL_DEF_REF", "AmbryLeaderStandby");

    ValidationResult result = validate(new IdealState(record));

    assertSingleViolationFor(result, "NUM_PARTITIONS");
    Assert.assertEquals(result.getViolations().get(0).getResourceName(), "10129");
  }

  private ValidationResult validate(IdealState proposedIdealState) {
    GuardrailContext context = GuardrailContext.newBuilder("cluster")
        .proposedIdealState(proposedIdealState)
        .build();
    return rule.validate(context);
  }

  private void assertSingleViolationFor(ValidationResult result, String field) {
    Assert.assertFalse(result.isFeasible());
    Assert.assertEquals(result.getViolations().size(), 1);
    Violation violation = result.getViolations().get(0);
    Assert.assertEquals(violation.getRuleId(), RequiredIdealStateFieldsGuardrailRule.RULE_ID);
    Assert.assertTrue(violation.getMessage().contains(field),
        "Expected violation message to mention " + field + " but was: " + violation.getMessage());
  }

  private boolean messageMentions(List<Violation> violations, String field) {
    return violations.stream().anyMatch(v -> v.getMessage().contains(field));
  }
}
