/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020-2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.acme.formatter;

import fish.payara.logging.jul.formatter.JSONLogFormatter;
import fish.payara.logging.jul.formatter.LogFormatDetector;
import fish.payara.logging.jul.formatter.ODLLogFormatter;
import fish.payara.logging.jul.formatter.OneLineFormatter;
import fish.payara.logging.jul.formatter.UniformLogFormatter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to ensure that the LogForatHelper correctly works out the log type
 *
 * @author jonathan coustick
 * @author David Matejcek
 */
public class LogFormatHelperTest {

    private static final String JSON_RECORD = "{\"Timestamp\":\"2020-04-20T21:53:40.248+0100\",\"Level\":\"INFO\","
        + "\"Version\":\"Payara 5.202\",\"LoggerName\":\"javax.enterprise.logging\",\"ThreadID\":\"22\",\"ThreadName\":"
        + "\"RunLevelControllerThread-1587416020198\",\"TimeMillis\":\"1587416020248\",\"LevelValue\":\"800\","
        + "\"MessageID\":\"NCLS-LOGGING-00009\",\"LogMessage\":\"Running Payara Version: Payara Server  5.202"
        + " #badassfish (build ${build.number})\"}";
    private static final String ODL_RECORD
        = "[2020-04-20T22:05:43.203+0100] [Payara 5.202] [INFO] [NCLS-LOGGING-00009] "
        + "[javax.enterprise.logging] [tid: _ThreadID=21 _ThreadName=RunLevelControllerThread-1587416743113] "
        + "[timeMillis: 1587416743203] [levelValue: 800] [[";
    private static final String ULF_RECORD
        = "[#|2020-04-20T22:02:35.314+0100|INFO|Payara 5.202|javax.enterprise.logging|_ThreadID=21;"
        + "_ThreadName=RunLevelControllerThread-1587416555246;_TimeMillis=1587416555314;_LevelValue=800;"
        + "_MessageID=NCLS-LOGGING-00009;|";
    private static final String ONELINE_RECORD = "22:22:15.796    INFO                 main"
        + "                        fish.payara.acme.PayaraLogManagerTest.externalHandlers Tick tock!";
    private static final String RANDOM_RECORD = "liuasudhfuk fhuashfu hiufh fueqrhfuqrehf qufhr uihuih uih jj";

    private final LogFormatDetector helper = new LogFormatDetector();

    @Test
    public void json() {
        assertAll(
            () -> assertTrue(helper.isJSONFormatLogHeader(JSON_RECORD), "is json"),
            () -> assertFalse(helper.isODLFormatLogHeader(JSON_RECORD), "is ODL"),
            () -> assertFalse(helper.isOneLineLFormatLogHeader(JSON_RECORD), "is OneLine"),
            () -> assertFalse(helper.isUniformFormatLogHeader(JSON_RECORD), "is UNL"),
            () -> assertEquals(JSONLogFormatter.class.getName(), helper.detectFormatter(JSON_RECORD))
        );
    }

    @Test
    public void odl() {
        assertAll(
            () -> assertFalse(helper.isJSONFormatLogHeader(ODL_RECORD), "is json"),
            () -> assertTrue(helper.isODLFormatLogHeader(ODL_RECORD), "is ODL"),
            () -> assertFalse(helper.isOneLineLFormatLogHeader(ODL_RECORD), "is OneLine"),
            () -> assertFalse(helper.isUniformFormatLogHeader(ODL_RECORD), "is UNL"),
            () -> assertEquals(ODLLogFormatter.class.getName(), helper.detectFormatter(ODL_RECORD))
        );
    }

    @Test
    public void oneline() {
        assertAll(
            () -> assertFalse(helper.isJSONFormatLogHeader(ONELINE_RECORD), "is json"),
            () -> assertFalse(helper.isODLFormatLogHeader(ONELINE_RECORD), "is ODL"),
            () -> assertTrue(helper.isOneLineLFormatLogHeader(ONELINE_RECORD), "is OneLine"),
            () -> assertFalse(helper.isUniformFormatLogHeader(ONELINE_RECORD), "is UNL"),
            () -> assertEquals(OneLineFormatter.class.getName(), helper.detectFormatter(ONELINE_RECORD))
        );
    }

    @Test
    public void uniform() {
        assertAll(
            () -> assertFalse(helper.isJSONFormatLogHeader(ULF_RECORD), "is json"),
            () -> assertFalse(helper.isODLFormatLogHeader(ULF_RECORD), "is ODL"),
            () -> assertFalse(helper.isOneLineLFormatLogHeader(ULF_RECORD), "is OneLine"),
            () -> assertTrue(helper.isUniformFormatLogHeader(ULF_RECORD), "is UNL"),
            () -> assertEquals(UniformLogFormatter.class.getName(), helper.detectFormatter(ULF_RECORD))
        );
    }

    @Test
    public void unknown() {
        assertAll(
            () -> assertFalse(helper.isJSONFormatLogHeader(RANDOM_RECORD), "is json"),
            () -> assertFalse(helper.isODLFormatLogHeader(RANDOM_RECORD), "is ODL"),
            () -> assertFalse(helper.isOneLineLFormatLogHeader(RANDOM_RECORD), "is OneLine"),
            () -> assertFalse(helper.isUniformFormatLogHeader(RANDOM_RECORD), "is UNL"),
            () -> assertEquals(LogFormatDetector.UNKNOWN_FORMAT, helper.detectFormatter(RANDOM_RECORD))
        );
    }
}
