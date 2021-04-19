/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) 2021 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.acme.cfg;

import fish.payara.jul.cfg.SortedProperties;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.SortedSet;

import org.junit.jupiter.api.Test;

import static fish.payara.jul.cfg.SortedProperties.loadFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * @author David Matejcek
 */
public class SortedPropertiesTest {

    private static final int PROPERTY_COUNT = 5;

    @Test
    void conversions() throws Exception {
        final SortedProperties properties = loadFrom(getClass().getResourceAsStream("/logging.properties"));
        assertAll("properties: " + properties,
            () -> assertNotNull(properties),
            () -> assertThat(properties.getPropertyNames(), hasSize(PROPERTY_COUNT))
        );

        final File file = File.createTempFile("logging", "properties");
        file.deleteOnExit();
        properties.store(file, "This is a test: " + getClass());

        final SortedProperties properties2 = loadFrom(file);
        assertAll("properties2: " + properties2,
            () -> assertNotNull(properties2),
            () -> assertThat(properties2.getPropertyNames(), hasSize(PROPERTY_COUNT))
        );

        final ByteArrayInputStream inputStream = properties2.toInputStream(null);
        final SortedProperties properties3 = loadFrom(inputStream);
        assertEquals(properties.size(), properties3.size(), "size of properties1 and properties3");

        final SortedSet<String> names = properties3.getPropertyNames();
        assertThat(names.first(), lessThan(names.last()));
    }
}
