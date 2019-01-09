/*
 * Copyright (c) 2010-2019 Evolveum
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

package com.evolveum.midpoint.schema.xjc;

import javax.xml.XMLConstants;
import com.evolveum.midpoint.prism.PrismConstants;

/**
 * @author lazyman
 */
public enum PrefixMapper {

    //W("http://midpoint.evolveum.com/xml/ns/public/communication/workflow-1.xsd", "WORKFLOW"),

    C("http://midpoint.evolveum.com/xml/ns/public/common/common-" + PrismConstants.PRISM_MAJOR_VERSION, "COMMON"),

    T(PrismConstants.NS_TYPES, "TYPES"),

    Q(PrismConstants.NS_QUERY, "QUERY"),

    R_CAP("http://midpoint.evolveum.com/xml/ns/public/resource/capabilities-" + PrismConstants.PRISM_MAJOR_VERSION, "CAPABILITIES"),

    A(PrismConstants.NS_ANNOTATION, "ANNOTATION"),

    S("http://midpoint.evolveum.com/xml/ns/public/model/situation-1.xsd", "SITUATION"),

    ICF_S("http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/resource-schema-" + PrismConstants.PRISM_MAJOR_VERSION, "ICF_SCHEMA"),

    ICF_C("http://midpoint.evolveum.com/xml/ns/public/connector/icf-1/connector-schema-" + PrismConstants.PRISM_MAJOR_VERSION, "ICF_CONFIGURATION"),

    ENC("http://www.w3.org/2001/04/xmlenc#", "XML_ENC"),

    DSIG("http://www.w3.org/2000/09/xmldsig#", "XML_DSIG"),

    XSD(XMLConstants.W3C_XML_SCHEMA_NS_URI, "XSD");

    public static final String DEFAULT_PREFIX = "O_";
    private String namespace;
    private String name;

    private PrefixMapper(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String getNamespaceName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPrefix() {
        return this.name() + "_";
    }

    public static String getPrefix(String namespace) {
        for (PrefixMapper mapper : PrefixMapper.values()) {
            if (mapper.getNamespace().equals(namespace)) {
                return mapper.getPrefix();
            }
        }

        return DEFAULT_PREFIX;
    }

    public static String getNamespace(String prefix) {
        for (PrefixMapper mapper : PrefixMapper.values()) {
            if (mapper.getPrefix().equals(prefix)) {
                return mapper.getNamespace();
            }
        }

        return null;
    }
}
