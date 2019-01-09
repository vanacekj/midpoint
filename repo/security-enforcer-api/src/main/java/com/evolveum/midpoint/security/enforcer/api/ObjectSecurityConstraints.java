/**
 * Copyright (c) 2014-2018 Evolveum
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
package com.evolveum.midpoint.security.enforcer.api;

import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.xml.ns._public.common.common_4.AuthorizationDecisionType;
import com.evolveum.midpoint.xml.ns._public.common.common_4.AuthorizationPhaseType;

public interface ObjectSecurityConstraints extends DebugDumpable {

	/**
	 * Almost the same as  findAllItemsDecision(String, ...), but in this case there are several equivalent action URLs.
	 * E.g. "read" and "get" actions. If any of them is denied, operation is denied. If any of them is allowed, operation is allowed.
	 */
	AuthorizationDecisionType findAllItemsDecision(String[] actionUrls, AuthorizationPhaseType phase);
	
	/**
	 * Returns decision for the whole action. This is fact returns a decision that applies to all items - if there is any.
	 * If there is no universally-applicable decision then null is returned. In that case there may still be fine-grained
	 * decisions for individual items. Use findItemDecision() to get them.
	 */
	AuthorizationDecisionType findAllItemsDecision(String actionUrl, AuthorizationPhaseType phase);

	AuthorizationDecisionType findItemDecision(ItemPath nameOnlyItemPath, String[] actionUrls, AuthorizationPhaseType phase);
	
	AuthorizationDecisionType findItemDecision(ItemPath nameOnlyItemPath, String actionUrl, AuthorizationPhaseType phase);

}
