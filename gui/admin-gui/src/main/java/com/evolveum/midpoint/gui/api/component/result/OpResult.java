/*
 * Copyright (c) 2010-2013 Evolveum
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

package com.evolveum.midpoint.gui.api.component.result;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.Visitable;
import com.evolveum.midpoint.prism.Visitor;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.web.page.admin.PageAdmin;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectFactory;
import com.evolveum.midpoint.xml.ns._public.common.common_3.OperationResultType;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.Validate;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author lazyman
 */
public class OpResult implements Serializable, Visitable {

    private OperationResultStatus status;
    private String operation;
    private String message;
    private List<Param> params;
    private List<Context> contexts;
    private String exceptionMessage;
    private String exceptionsStackTrace;
    private List<OpResult> subresults;
    private int count;
    private String xml;
    
    private boolean showMore;
    private boolean showError;

    public static OpResult getOpResult(PageBase page, OperationResult result){
        OpResult opResult = new OpResult();
        Validate.notNull(result, "Operation result must not be null.");
        Validate.notNull(result.getStatus(), "Operation result status must not be null.");

        opResult.message = result.getMessage();
        opResult.operation = result.getOperation();
        opResult.status = result.getStatus();
        opResult.count = result.getCount();

        if (result.getCause() != null) {
            Throwable cause = result.getCause();
            opResult.exceptionMessage = cause.getMessage();

            Writer writer = new StringWriter();
            cause.printStackTrace(new PrintWriter(writer));
            opResult.exceptionsStackTrace = writer.toString();
        }

        if (result.getParams() != null) {
            for (Map.Entry<String, Serializable> entry : result.getParams().entrySet()) {
                String paramValue = null;
                Object value = entry.getValue();
                if (value != null) {
                    paramValue = value.toString();
                }

                opResult.getParams().add(new Param(entry.getKey(), paramValue));
            }
        }
        
        if(result.getContext() != null){
        	for (Map.Entry<String, Serializable> entry : result.getContext().entrySet()) {
                String contextValue = null;
                Object value = entry.getValue();
                if (value != null) {
                	contextValue = value.toString();
                }

                opResult.getContexts().add(new Context(entry.getKey(), contextValue));
            }
        }

        if (result.getSubresults() != null) {
            for (OperationResult subresult : result.getSubresults()) {
                opResult.getSubresults().add(OpResult.getOpResult(page, subresult));
            }
        }

        try {
        	OperationResultType resultType = result.createOperationResultType();
        	ObjectFactory of = new ObjectFactory();
			opResult.xml = page.getPrismContext().serializeAtomicValue(of.createOperationResult(resultType), PrismContext.LANG_XML);
		} catch (SchemaException|RuntimeException ex) {
            String m = "Can't create xml: " + ex;
//			error(m);
            opResult.xml = "<?xml version='1.0'?><message>" + StringEscapeUtils.escapeXml(m) + "</message>";
//            throw ex;
        }
        return opResult;
    }

    public boolean isShowMore() {
		return showMore;
	}
    
    public void setShowMore(boolean showMore) {
		this.showMore = showMore;
	}
    
    public boolean isShowError() {
		return showError;
	}
    
    public void setShowError(boolean showError) {
		this.showError = showError;
	}
    
    public List<OpResult> getSubresults() {
        if (subresults == null) {
            subresults = new ArrayList<OpResult>();
        }
        return subresults;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public String getExceptionsStackTrace() {
        return exceptionsStackTrace;
    }

    public String getMessage() {
        return message;
    }

    public String getOperation() {
        return operation;
    }

    public List<Param> getParams() {
        if (params == null) {
            params = new ArrayList<Param>();
        }
        return params;
    }
    
    public List<Context> getContexts() {
        if (contexts == null) {
        	contexts = new ArrayList<Context>();
        }
        return contexts;
    }

    public OperationResultStatus getStatus() {
        return status;
    }

    public int getCount() {
        return count;
    }
    
    public String getXml() {
    	return xml;
    }

	@Override
	public void accept(Visitor visitor) {
		
		visitor.visit(this);
		
		for (OpResult result : this.getSubresults()){
			result.accept(visitor);
		}
		
	}
}