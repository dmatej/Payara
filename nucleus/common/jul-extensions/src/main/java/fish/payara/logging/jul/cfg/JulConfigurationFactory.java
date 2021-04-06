/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.logging.jul.cfg;

import fish.payara.logging.jul.formatter.JSONLogFormatter;
import fish.payara.logging.jul.formatter.ODLLogFormatter;
import fish.payara.logging.jul.formatter.UniformLogFormatter;
import fish.payara.logging.jul.handler.PayaraLogHandler;
import fish.payara.logging.jul.tracing.PayaraLoggingTracer;

import java.nio.charset.StandardCharsets;
import java.util.logging.Formatter;
import java.util.logging.Level;

import static fish.payara.logging.jul.cfg.PayaraLogHandlerConfiguration.DEFAULT_BUFFER_CAPACITY;
import static fish.payara.logging.jul.cfg.PayaraLogHandlerConfiguration.DEFAULT_BUFFER_TIMEOUT;
import static fish.payara.logging.jul.cfg.PayaraLogHandlerConfiguration.DEFAULT_ROTATION_LIMIT_BYTES;

/**
 * @author David Matejcek
 */
public class JulConfigurationFactory {

    public static final int MINIMUM_ROTATION_LIMIT_VALUE = 500_000;

    public PayaraLogHandlerConfiguration createPayaraLogHandlerConfiguration(
        final Class<? extends PayaraLogHandler> handlerClass, final String defaultLogFileName) {
        final LoggingConfigurationHelper helper = new LoggingConfigurationHelper(handlerClass);
        final PayaraLogHandlerConfiguration configuration = new PayaraLogHandlerConfiguration();
        configuration.setLevel(helper.getLevel("level", Level.ALL));
        configuration.setEncoding(helper.getCharset("encoding", StandardCharsets.UTF_8));
        configuration.setLogToFile(helper.getBoolean("logtoFile", true));
        configuration.setLogFile(helper.getFile("file", null));
        configuration.setLogStandardStreams(helper.getBoolean("logStandardStreams", Boolean.FALSE));

        configuration.setFlushFrequency(helper.getNonNegativeInteger("flushFrequency", 1));
        configuration.setBufferCapacity(helper.getInteger("bufferCapacity", DEFAULT_BUFFER_CAPACITY));
        configuration.setBufferTimeout(helper.getInteger("bufferTimeout", DEFAULT_BUFFER_TIMEOUT));

        final Integer rotationLimit = helper.getInteger("rotationLimitInBytes", DEFAULT_ROTATION_LIMIT_BYTES);
        configuration.setLimitForFileRotation(
            rotationLimit >= MINIMUM_ROTATION_LIMIT_VALUE ? rotationLimit : DEFAULT_ROTATION_LIMIT_BYTES);
        configuration.setCompressionOnRotation(helper.getBoolean("compressOnRotation", Boolean.FALSE));
        configuration.setRotationOnDateChange(helper.getBoolean("rotationOnDateChange", Boolean.FALSE));
        configuration.setRotationTimeLimitValue(helper.getNonNegativeInteger("rotationTimelimitInMinutes", 0));
        configuration.setMaxHistoryFiles(helper.getNonNegativeInteger("maxHistoryFiles", 10));

        final Formatter formatter = helper.getFormatter("formatter", UniformLogFormatter.class.getName());
        configureFormatter(formatter, helper);
        configuration.setFormatterConfiguration(formatter);
        return configuration;
    }


    /**
     * Configures the formatter. Formatter can be already configured by his direct defaults. This
     * method respects formatter's configuration property names used in the context of the handler.
     * <p>
     * Null values in handler's properties don't change the formatter's settings.
     *
     * @param formatter formatter to be configured.
     * @param helper helper created for the handler using the formatter.
     * @return true if formatter matched list of known formatters and was configured.
     */
    public boolean configureFormatter(final Formatter formatter, final LoggingConfigurationHelper helper) {
        PayaraLoggingTracer.trace(JulConfigurationFactory.class,
            () -> "configureFormatter(formatter=" + formatter + ", helper)");
        if (formatter instanceof UniformLogFormatter) {
            configureUniformLogFormatter((UniformLogFormatter) formatter, helper);
            return true;
        }
        if (formatter instanceof ODLLogFormatter) {
            configureODLFormatter((ODLLogFormatter) formatter, helper);
            return true;
        }
        if (formatter instanceof JSONLogFormatter) {
            configureJSONFormatter((JSONLogFormatter) formatter, helper);
            return true;
        }
        return false;
    }


    private void configureUniformLogFormatter(final UniformLogFormatter formatter,
        final LoggingConfigurationHelper helper) {
        formatter.setAnsiColor(helper.getBoolean("ansiColor", Boolean.FALSE));
        formatter.setDateTimeFormatter(helper.getDateTimeFormatter("logFormatDateFormat", null));
        formatter.setExcludeFields(helper.getString("excludeFields", null));
        formatter.setMultiLineMode(helper.getBoolean("multiLineMode", Boolean.TRUE));
        formatter.setRecordFieldSeparator(helper.getCharacter("logFormatFieldSeparator", null));
        formatter.setRecordBeginMarker(helper.getString("logFormatBeginMarker", null));
        formatter.setRecordEndMarker(helper.getString("logFormatEndMarker", null));
    }


    private void configureODLFormatter(final ODLLogFormatter formatter,
        final LoggingConfigurationHelper helper) {
        formatter.setAnsiColor(helper.getBoolean("ansiColor", Boolean.FALSE));
        formatter.setDateTimeFormatter(helper.getDateTimeFormatter("logFormatDateFormat", null));
        formatter.setExcludeFields(helper.getString("excludeFields", null));
        formatter.setMultiLineMode(helper.getBoolean("multiLineMode", Boolean.TRUE));
    }


    private void configureJSONFormatter(final JSONLogFormatter formatter,
        final LoggingConfigurationHelper helper) {
        formatter.setExcludeFields(helper.getString("excludeFields", null));
    }
}
