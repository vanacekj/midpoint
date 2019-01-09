/*
 * Copyright (c) 2010-2018 Evolveum
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
package com.evolveum.midpoint.model.impl.lens.projector.policy;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.api.context.ModelElementContext;
import com.evolveum.midpoint.model.api.context.ModelProjectionContext;
import com.evolveum.midpoint.repo.common.CounterManager;
import com.evolveum.midpoint.repo.common.CounterSepcification;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_4.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.PolicyActionType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.StopPolicyActionType;

/**
 * @author katka
 *
 */
@Component
public class PolicyRuleStopExecutor {

	private static final Trace LOGGER = TraceManager.getTrace(PolicyRuleStopExecutor.class);
	
	@Autowired private CounterManager counterManager;
	
	public <O extends ObjectType> void execute(@NotNull ModelContext<O> context, Task task, OperationResult result) throws PolicyViolationException {
		ModelElementContext<O> focusCtx = context.getFocusContext();
		CounterSepcification counterSpec = counterManager.getCounterSpec(task);

		int counter = 1;
		if (counterSpec != null) {
			counter = counterSpec.getCount();
		}
		
		LOGGER.info("counter: {}", counter);
		
		if (focusCtx == null || focusCtx.getPolicyRules() == null) {
			return;
		}
		
		for (EvaluatedPolicyRule policyRule : focusCtx.getPolicyRules()) {
			LOGGER.info("focus policy rules: {}", policyRule);
			counter = checkEvaluatedPolicyRule(policyRule, counter);
		}
		
		Collection<? extends ModelProjectionContext> projectionCtxs = context.getProjectionContexts();
		for (ModelProjectionContext projectionCtx : projectionCtxs) {
			Collection<EvaluatedPolicyRule> evaluatedPolicyRules = projectionCtx.getPolicyRules();
			for (EvaluatedPolicyRule policyRule : evaluatedPolicyRules) {
				LOGGER.info("projction policy rules: {}", policyRule);
				counter = checkEvaluatedPolicyRule(policyRule, counter);
			}
			
		}
	
		LOGGER.info("counter after: {}", counter);
		if (counterSpec != null) {
			counterSpec.setCount(counter);
		}
		
	}
	
	private synchronized int checkEvaluatedPolicyRule(EvaluatedPolicyRule policyRule, int counter) throws PolicyViolationException {
		for (PolicyActionType action : policyRule.getEnabledActions()) {
			LOGGER.info("action: {}", action);
		}
		if (policyRule.containsEnabledAction(StopPolicyActionType.class)) {
			LOGGER.info("counter increment: {}", policyRule);
			counter++;
			StopPolicyActionType stopAction = policyRule.getEnabledAction(StopPolicyActionType.class);
			if (stopAction.getCount() != null && stopAction.getCount().intValue() < counter) {
				throw new PolicyViolationException("Policy rule violation: " + policyRule.getPolicyRule());
			}
		}
		
		return counter;
	}
}
