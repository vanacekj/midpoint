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

package com.evolveum.midpoint.provisioning.impl.errorhandling;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.provisioning.api.ChangeNotificationDispatcher;
import com.evolveum.midpoint.provisioning.api.ProvisioningOperationOptions;
import com.evolveum.midpoint.provisioning.impl.ProvisioningContext;
import com.evolveum.midpoint.provisioning.impl.ProvisioningOperationState;
import com.evolveum.midpoint.provisioning.impl.ResourceManager;
import com.evolveum.midpoint.provisioning.ucf.api.GenericFrameworkException;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.result.AsynchronousOperationResult;
import com.evolveum.midpoint.schema.result.AsynchronousOperationReturnValue;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.CommunicationException;
import com.evolveum.midpoint.util.exception.ConfigurationException;
import com.evolveum.midpoint.util.exception.ExpressionEvaluationException;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_4.AvailabilityStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.PendingOperationExecutionStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.PendingOperationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.ShadowType;

/**
 * Handler for provisioning errors. The handler can invoke additional functionality to
 * handle the error, transform the error, turn critical errors to non-critical, and so on.
 * 
 * The handler may "swallow" and re-throw the exception. If the exception is "swallowed" then
 * the operation continues. In that case the relevant information is in opState and result.
 * This will usually indicate "in progress" operation (e.g. operation prepared to be retried).
 * 
 * If exception is thrown from the handler then this means the end of the operation.
 * No more retries, no more attempts.
 * 
 * @author Katka Valalikova
 * @author Radovan Semancik
 *
 */
public abstract class ErrorHandler {
	
	private static final Trace LOGGER = TraceManager.getTrace(ErrorHandler.class);
	
	@Autowired protected ChangeNotificationDispatcher changeNotificationDispatcher;
	@Autowired private ResourceManager resourceManager;
	@Autowired protected PrismContext prismContext;
	
	public abstract PrismObject<ShadowType> handleGetError(ProvisioningContext ctx,
			PrismObject<ShadowType> repositoryShadow,
			GetOperationOptions rootOptions,
			Exception cause,
			Task task,
			OperationResult parentResult) 
					throws SchemaException, GenericFrameworkException, CommunicationException,
					ObjectNotFoundException, ObjectAlreadyExistsException, ConfigurationException,
					SecurityViolationException, PolicyViolationException, ExpressionEvaluationException;
			
	
	public abstract OperationResultStatus handleAddError(ProvisioningContext ctx,
			PrismObject<ShadowType> shadowToAdd,
			ProvisioningOperationOptions options,
			ProvisioningOperationState<AsynchronousOperationReturnValue<PrismObject<ShadowType>>> opState,
			Exception cause,
			OperationResult failedOperationResult,
			Task task,
			OperationResult parentResult)
				throws SchemaException, GenericFrameworkException, CommunicationException,
				ObjectNotFoundException, ObjectAlreadyExistsException, ConfigurationException,
				SecurityViolationException, PolicyViolationException, ExpressionEvaluationException;
	
	protected OperationResultStatus postponeAdd(ProvisioningContext ctx,
			PrismObject<ShadowType> shadowToAdd,
			ProvisioningOperationState<AsynchronousOperationReturnValue<PrismObject<ShadowType>>> opState,
			OperationResult failedOperationResult,
			OperationResult result) {
		LOGGER.trace("Postponing ADD operation for {}", shadowToAdd);
		opState.setExecutionStatus(PendingOperationExecutionStatusType.EXECUTING);
		AsynchronousOperationReturnValue<PrismObject<ShadowType>> asyncResult = new AsynchronousOperationReturnValue<>();
		asyncResult.setOperationResult(failedOperationResult);
		asyncResult.setOperationType(PendingOperationTypeType.RETRY);
		opState.setAsyncResult(asyncResult);
		if (opState.getAttemptNumber() == null) {
			opState.setAttemptNumber(1);
		}
		result.recordInProgress();
		return OperationResultStatus.IN_PROGRESS;
	}

	public abstract OperationResultStatus handleModifyError(ProvisioningContext ctx,
			PrismObject<ShadowType> repoShadow,
			Collection<? extends ItemDelta> modifications,
			ProvisioningOperationOptions options,
			ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue>>>> opState,
			Exception cause,
			OperationResult failedOperationResult,
			Task task,
			OperationResult parentResult)
				throws SchemaException, GenericFrameworkException, CommunicationException,
				ObjectNotFoundException, ObjectAlreadyExistsException, ConfigurationException,
				SecurityViolationException, PolicyViolationException, ExpressionEvaluationException;
	
	protected OperationResultStatus postponeModify(ProvisioningContext ctx,
			PrismObject<ShadowType> repoShadow,
			Collection<? extends ItemDelta> modifications,
			ProvisioningOperationState<AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue>>>> opState,
			OperationResult failedOperationResult,
			OperationResult result) {
		LOGGER.trace("Postponing MODIFY operation for {}", repoShadow);
		opState.setExecutionStatus(PendingOperationExecutionStatusType.EXECUTING);
		AsynchronousOperationReturnValue<Collection<PropertyDelta<PrismPropertyValue>>> asyncResult = new AsynchronousOperationReturnValue<>();
		asyncResult.setOperationResult(failedOperationResult);
		asyncResult.setOperationType(PendingOperationTypeType.RETRY);
		opState.setAsyncResult(asyncResult);
		if (opState.getAttemptNumber() == null) {
			opState.setAttemptNumber(1);
		}
		result.recordInProgress();
		return OperationResultStatus.IN_PROGRESS;
	}
	
	protected OperationResultStatus postponeDelete(ProvisioningContext ctx,
			PrismObject<ShadowType> repoShadow,
			ProvisioningOperationState<AsynchronousOperationResult> opState,
			OperationResult failedOperationResult,
			OperationResult result) {
		LOGGER.trace("Postponing DELETE operation for {}", repoShadow);
		opState.setExecutionStatus(PendingOperationExecutionStatusType.EXECUTING);
		AsynchronousOperationResult asyncResult = new AsynchronousOperationResult();
		asyncResult.setOperationResult(failedOperationResult);
		asyncResult.setOperationType(PendingOperationTypeType.RETRY);
		opState.setAsyncResult(asyncResult);
		if (opState.getAttemptNumber() == null) {
			opState.setAttemptNumber(1);
		}
		result.recordInProgress();
		return OperationResultStatus.IN_PROGRESS;
	}

	public abstract OperationResultStatus handleDeleteError(ProvisioningContext ctx,
			PrismObject<ShadowType> repoShadow,
			ProvisioningOperationOptions options,
			ProvisioningOperationState<AsynchronousOperationResult> opState,
			Exception cause,
			OperationResult failedOperationResult,
			Task task,
			OperationResult parentResult)
				throws SchemaException, GenericFrameworkException, CommunicationException,
				ObjectNotFoundException, ObjectAlreadyExistsException, ConfigurationException,
				SecurityViolationException, PolicyViolationException, ExpressionEvaluationException;
	
	/**
	 * Throw exception of appropriate type.
	 * If exception is thrown then this is definitive end of the operation.
	 * No more retries, no more attempts.
	 */
	protected abstract void throwException(Exception cause, ProvisioningOperationState<? extends AsynchronousOperationResult> opState, OperationResult result)
			throws SchemaException, GenericFrameworkException, CommunicationException,
			ObjectNotFoundException, ObjectAlreadyExistsException, ConfigurationException,
			SecurityViolationException, PolicyViolationException, ExpressionEvaluationException;
	
	/**
	 * Record error that completes the operation. If such error is recorded then this is definitive end of the operation.
	 * No more retries, no more attempts.
	 */
	protected void recordCompletionError(Exception cause,
			ProvisioningOperationState<? extends AsynchronousOperationResult> opState, OperationResult result) {
		result.recordFatalError(cause);
		if (opState != null) {
			opState.setExecutionStatus(PendingOperationExecutionStatusType.COMPLETED);
		}
	}
		
	protected void markResourceDown(ResourceType resource, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, CommunicationException, ConfigurationException, ExpressionEvaluationException {
		resourceManager.modifyResourceAvailabilityStatus(resource.asPrismObject(), 
				AvailabilityStatusType.DOWN, parentResult);
	}
	
	protected boolean isOperationRetryEnabled(ResourceType resource) {
		if (resource.getConsistency() == null) {
			return true;
		}
		
		if (resource.getConsistency().isPostpone() == null) {
			Integer operationRetryMaxAttempts = resource.getConsistency().getOperationRetryMaxAttempts();
			if (operationRetryMaxAttempts == null) {
				return true;
			}
			return operationRetryMaxAttempts != 0;
		}
		
		return resource.getConsistency().isPostpone();
	}
	
	protected boolean isCompletePostponedOperations(ProvisioningOperationOptions options) {
		return ProvisioningOperationOptions.isCompletePostponed(options);
	}
		
}
