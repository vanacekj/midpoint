/*
 * Copyright (c) 2010-2017 Evolveum
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

package com.evolveum.midpoint.schema;

import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.xml.ns._public.common.common_4.ObjectType;

import java.io.Serializable;
import java.util.Collection;

/**
 * Query diagnostics request: contains query to be executed (or at least translated) and some options.
 *
 * EXPERIMENTAL, will probably change
 *
 * @author mederly
 */
public class RepositoryQueryDiagRequest implements Serializable {

	private Class<? extends ObjectType> type;
	private ObjectQuery query;
	private Collection<SelectorOptions<GetOperationOptions>> options;

	private Serializable implementationLevelQuery;				// this is used if specified

	private boolean translateOnly;

	public Class<? extends ObjectType> getType() {
		return type;
	}

	public void setType(Class<? extends ObjectType> type) {
		this.type = type;
	}

	public ObjectQuery getQuery() {
		return query;
	}

	public void setQuery(ObjectQuery query) {
		this.query = query;
	}

	public Collection<SelectorOptions<GetOperationOptions>> getOptions() {
		return options;
	}

	public void setOptions(Collection<SelectorOptions<GetOperationOptions>> options) {
		this.options = options;
	}

	public Serializable getImplementationLevelQuery() {
		return implementationLevelQuery;
	}

	public void setImplementationLevelQuery(Serializable implementationLevelQuery) {
		this.implementationLevelQuery = implementationLevelQuery;
	}

	public boolean isTranslateOnly() {
		return translateOnly;
	}

	public void setTranslateOnly(boolean translateOnly) {
		this.translateOnly = translateOnly;
	}
}
