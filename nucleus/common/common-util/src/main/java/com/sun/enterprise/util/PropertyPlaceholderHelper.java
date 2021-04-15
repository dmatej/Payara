/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) [2018-2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */
package com.sun.enterprise.util;

import fish.payara.jul.cfg.SortedProperties;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Zdeněk Soukup
 */
public class PropertyPlaceholderHelper {

    private static final Logger LOG = Logger.getLogger(PropertyPlaceholderHelper.class.getName());

    public final static String ENV_REGEX = "([^\\$]*)\\$\\{ENV=([^\\}]*)\\}([^\\$]*)";
    private static final int MAX_SUBSTITUTION_DEPTH = 100;

    private final Pattern pattern;
    private final Map<String, String> properties;


    public PropertyPlaceholderHelper(Map<String, String> properties, String regex) {
        this.properties = properties;
        this.pattern = Pattern.compile(regex);
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getPropertyValue(String key) {
        LOG.finest(() -> String.format("getPropertyValue(key=%s)", key));
        return properties.get(key);
    }

    public SortedProperties replacePropertiesPlaceholder(Properties properties) {
        final SortedProperties p = new SortedProperties();
        Set<String> keys = properties.stringPropertyNames();

        for (String key : keys) {
            p.setProperty(key, replacePlaceholder(properties.getProperty(key)));
        }
        return p;
    }

    public String replacePlaceholder(String value) {
        if (value != null && value.indexOf('$') != -1) {
            int i = 0;
            // Perform Environment variable substitution
            Matcher m = getPattern().matcher(value);

            while (m.find() && i < MAX_SUBSTITUTION_DEPTH) {
                String matchValue = m.group(2).trim();
                String newValue = getPropertyValue(matchValue);
                if (newValue != null) {
                    value = m.replaceFirst(Matcher.quoteReplacement(m.group(1) + newValue + m.group(3)));
                    m.reset(value);
                }
                i++;
            }

            if (i >= MAX_SUBSTITUTION_DEPTH) {
                LOG.log(Level.SEVERE, "System property substitution exceeded maximum of {0}", MAX_SUBSTITUTION_DEPTH);
            }
        }
        return value;
    }
}
