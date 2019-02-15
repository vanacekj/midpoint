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

package com.evolveum.midpoint.notifications.impl.helpers;

import com.evolveum.midpoint.model.common.SystemObjectCache;
import com.evolveum.midpoint.model.impl.expr.ModelExpressionThreadLocalHolder;
import com.evolveum.midpoint.notifications.api.events.Event;
import com.evolveum.midpoint.notifications.impl.NotificationFunctionsImpl;
import com.evolveum.midpoint.notifications.impl.formatters.TextFormatter;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.repo.common.expression.Expression;
import com.evolveum.midpoint.repo.common.expression.ExpressionEvaluationContext;
import com.evolveum.midpoint.repo.common.expression.ExpressionFactory;
import com.evolveum.midpoint.repo.common.expression.ExpressionVariables;
import com.evolveum.midpoint.schema.constants.ExpressionConstants;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExpressionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.NotificationMessageAttachmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SystemConfigurationType;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.util.*;

/**
 * @author mederly
 */
@Component
public class NotificationExpressionHelper {

	private static final Trace LOGGER = TraceManager.getTrace(NotificationExpressionHelper.class);

	@Autowired private NotificationFunctionsImpl notificationsUtil;
	@Autowired private PrismContext prismContext;
	@Autowired private ExpressionFactory expressionFactory;
	@Autowired private TextFormatter textFormatter;
	@Autowired private SystemObjectCache systemObjectCache;

	// shortDesc = what is to be evaluated e.g. "event filter expression"
	public boolean evaluateBooleanExpressionChecked(ExpressionType expressionType, ExpressionVariables expressionVariables,
			String shortDesc, Task task, OperationResult result) {

		Throwable failReason;
		try {
			return evaluateBooleanExpression(expressionType, expressionVariables, shortDesc, task, result);
		} catch (ObjectNotFoundException | SchemaException | ExpressionEvaluationException | CommunicationException | ConfigurationException | SecurityViolationException e) {
			failReason = e;
		}

		LoggingUtils.logException(LOGGER, "Couldn't evaluate {} {}", failReason, shortDesc, expressionType);
		result.recordFatalError("Couldn't evaluate " + shortDesc, failReason);
		throw new SystemException(failReason);
	}

	public boolean evaluateBooleanExpression(ExpressionType expressionType, ExpressionVariables expressionVariables, String shortDesc,
			Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {

		QName resultName = new QName(SchemaConstants.NS_C, "result");
		PrismPropertyDefinition<Boolean> resultDef = prismContext.definitionFactory().createPropertyDefinition(resultName, DOMUtil.XSD_BOOLEAN);
		Expression<PrismPropertyValue<Boolean>,PrismPropertyDefinition<Boolean>> expression = expressionFactory.makeExpression(expressionType, resultDef, shortDesc, task, result);
		ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, expressionVariables, shortDesc, task, result);

		PrismValueDeltaSetTriple<PrismPropertyValue<Boolean>> exprResultTriple = ModelExpressionThreadLocalHolder
				.evaluateExpressionInContext(expression, params, task, result);

		Collection<PrismPropertyValue<Boolean>> exprResult = exprResultTriple.getZeroSet();
		if (exprResult.size() == 0) {
			return false;
		} else if (exprResult.size() > 1) {
			throw new IllegalStateException("Filter expression should return exactly one boolean value; it returned " + exprResult.size() + " ones");
		}
		Boolean boolResult = exprResult.iterator().next().getValue();
		return boolResult != null ? boolResult : false;
	}

	public List<String> evaluateExpressionChecked(ExpressionType expressionType, ExpressionVariables expressionVariables,
			String shortDesc, Task task, OperationResult result) {

		Throwable failReason;
		try {
			return evaluateExpression(expressionType, expressionVariables, shortDesc, task, result);
		} catch (ObjectNotFoundException | SchemaException | ExpressionEvaluationException | CommunicationException | ConfigurationException | SecurityViolationException e) {
			failReason = e;
		}

		LoggingUtils.logException(LOGGER, "Couldn't evaluate {} {}", failReason, shortDesc, expressionType);
		result.recordFatalError("Couldn't evaluate " + shortDesc, failReason);
		throw new SystemException(failReason);
	}

	private List<String> evaluateExpression(ExpressionType expressionType, ExpressionVariables expressionVariables,
			String shortDesc, Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {

		QName resultName = new QName(SchemaConstants.NS_C, "result");
		MutablePrismPropertyDefinition<String> resultDef = prismContext.definitionFactory().createPropertyDefinition(resultName, DOMUtil.XSD_STRING);
		resultDef.setMaxOccurs(-1);

		Expression<PrismPropertyValue<String>,PrismPropertyDefinition<String>> expression = expressionFactory.makeExpression(expressionType, resultDef, shortDesc, task, result);
		ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, expressionVariables, shortDesc, task, result);
		PrismValueDeltaSetTriple<PrismPropertyValue<String>> exprResult = ModelExpressionThreadLocalHolder
				.evaluateExpressionInContext(expression, params, task, result);

		List<String> retval = new ArrayList<>();
		for (PrismPropertyValue<String> item : exprResult.getZeroSet()) {
			retval.add(item.getValue());
		}
		return retval;
	}
	
	public List<NotificationMessageAttachmentType> evaluateNotificationMessageAttachmentTypeExpressionChecked(ExpressionType expressionType, ExpressionVariables expressionVariables,
			String shortDesc, Task task, OperationResult result) {

		Throwable failReason;
		try {
			return evaluateNotificationMessageAttachmentTypeExpression(expressionType, expressionVariables, shortDesc, task, result);
		} catch (ObjectNotFoundException | SchemaException | ExpressionEvaluationException | CommunicationException | ConfigurationException | SecurityViolationException e) {
			failReason = e;
		}

		LoggingUtils.logException(LOGGER, "Couldn't evaluate {} {}", failReason, shortDesc, expressionType);
		result.recordFatalError("Couldn't evaluate " + shortDesc, failReason);
		throw new SystemException(failReason);
	}

	public List<NotificationMessageAttachmentType> evaluateNotificationMessageAttachmentTypeExpression(ExpressionType expressionType, ExpressionVariables expressionVariables, String shortDesc,
			Task task, OperationResult result) throws ObjectNotFoundException, SchemaException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {

		QName resultName = new QName(SchemaConstants.NS_C, "result");
		PrismPropertyDefinition<NotificationMessageAttachmentType> resultDef = prismContext.definitionFactory().createPropertyDefinition(resultName, NotificationMessageAttachmentType.COMPLEX_TYPE);
		Expression<PrismPropertyValue<NotificationMessageAttachmentType>,PrismPropertyDefinition<NotificationMessageAttachmentType>> expression = expressionFactory.makeExpression(expressionType, resultDef, shortDesc, task, result);
		ExpressionEvaluationContext params = new ExpressionEvaluationContext(null, expressionVariables, shortDesc, task, result);

		PrismValueDeltaSetTriple<PrismPropertyValue<NotificationMessageAttachmentType>> exprResultTriple = ModelExpressionThreadLocalHolder
				.evaluateExpressionInContext(expression, params, task, result);

		Collection<PrismPropertyValue<NotificationMessageAttachmentType>> exprResult = exprResultTriple.getZeroSet();
		if (exprResult.size() == 0) {
			return null;
		} 
		
		List<NotificationMessageAttachmentType> retval = new ArrayList<>();
		for (PrismPropertyValue<NotificationMessageAttachmentType> item : exprResult) {
			retval.add(item.getValue());
		}
		return retval;
	}

	public ExpressionVariables getDefaultVariables(Event event, OperationResult result) {
		ExpressionVariables expressionVariables = new ExpressionVariables();
		Map<QName, Object> variables = new HashMap<>();
		event.createExpressionVariables(variables, result);
		variables.put(SchemaConstants.C_TEXT_FORMATTER, textFormatter);
		variables.put(SchemaConstants.C_NOTIFICATION_FUNCTIONS, notificationsUtil);
		variables.put(ExpressionConstants.VAR_CONFIGURATION, getSystemConfiguration(result));
		expressionVariables.addVariableDefinitions(variables);
		return expressionVariables;
	}

	@Nullable
	private PrismObject<SystemConfigurationType> getSystemConfiguration(OperationResult result) {
		try {
			return systemObjectCache.getSystemConfiguration(result);
		} catch (SchemaException e) {
			LoggingUtils.logUnexpectedException(LOGGER, "Couldn't get system configuration", e);
			return null;
		}
	}
}