/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
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

// Portions Copyright [2017-2021] [Payara Foundation and/or affiliates]

package fish.payara.jul.formatter;

import fish.payara.jul.cfg.LogProperty;
import fish.payara.jul.env.LoggingSystemEnvironment;
import fish.payara.jul.formatter.ExcludeFieldsSupport.SupplementalAttribute;
import fish.payara.jul.record.EnhancedLogRecord;
import fish.payara.jul.record.MessageResolver;
import fish.payara.jul.tracing.PayaraLoggingTracer;

import java.time.OffsetDateTime;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static fish.payara.jul.formatter.ODLLogFormatter.ODLFormatterProperty.EXCLUDED_FIELDS;
import static fish.payara.jul.formatter.ODLLogFormatter.ODLFormatterProperty.FIELD_SEPARATOR;
import static fish.payara.jul.formatter.ODLLogFormatter.ODLFormatterProperty.MULTILINE;
import static java.lang.System.lineSeparator;

/**
 * ODLLogFormatter conforms to the logging format defined by the
 * Log Working Group in Oracle.
 * The specified format is
 * <pre>[[timestamp] [organization ID] [Message Type/Level] [Message ID] [Logger Name] [Thread ID] [User ID] [ECID] [Extra Attributes] [Message]]\n</pre>
 *
 * @author Naman Mehta
 * @author David Matejcek - refactoring
 */
public class ODLLogFormatter extends AnsiColorFormatter {

    private static final int REC_BUFFER_CAPACITY = 512;

    private static final String FIELD_BEGIN_MARKER = "[";
    private static final String FIELD_END_MARKER = "]";
    private static final String DEFAULT_FIELD_SEPARATOR = " ";
    private static final String MULTILINE_INDENTATION = "  ";

    private static final String LABEL_CLASSNAME = "CLASSNAME";
    private static final String LABEL_METHODNAME = "METHODNAME";
    private static final String LABEL_RECORDNUMBER = "RECORDNUMBER";

    private static final MessageResolver MSG_RESOLVER = new MessageResolver();

    private final ExcludeFieldsSupport excludeFieldsSupport = new ExcludeFieldsSupport();
    private String recordFieldSeparator = DEFAULT_FIELD_SEPARATOR;
    private boolean multiline = true;

    public ODLLogFormatter(final HandlerId handlerId) {
        super(handlerId);
        configure(this, FormatterConfigurationHelper.forFormatterClass(getClass()));
        configure(this, FormatterConfigurationHelper.forHandlerId(handlerId));
    }


    /**
     * Creates an instance and initializes defaults from log manager's configuration
     */
    public ODLLogFormatter() {
        configure(this, FormatterConfigurationHelper.forFormatterClass(getClass()));
    }


    private static void configure(final ODLLogFormatter formatter, final FormatterConfigurationHelper helper) {
        formatter.setExcludeFields(helper.getString(EXCLUDED_FIELDS, formatter.excludeFieldsSupport.toString()));
        formatter.multiline = helper.getBoolean(MULTILINE, formatter.multiline);
        formatter.recordFieldSeparator = helper.getString(FIELD_SEPARATOR, formatter.recordFieldSeparator);
    }


    @Override
    public String formatRecord(final LogRecord record) {
        return formatEnhancedLogRecord(MSG_RESOLVER.resolve(record));
    }


    /**
     * @param excludeFields comma separated field names which should not be in the ouptut
     */
    public void setExcludeFields(final String excludeFields) {
        this.excludeFieldsSupport.setExcludedFields(excludeFields);
    }


    /**
     * @param multiline true if the log message is on the next line. Default: true.
     */
    public void setMultiline(final boolean multiline) {
        this.multiline = multiline;
    }


    private String formatEnhancedLogRecord(final EnhancedLogRecord record) {
        try {
            final String message = getPrintedMessage(record);
            if (message == null) {
                return "";
            }
            final boolean forceMultiline = multiline || message.contains(lineSeparator());
            final Level logLevel = record.getLevel();
            final String msgId = record.getMessageKey();
            final String loggerName = record.getLoggerName();
            final String threadName = record.getThreadName();
            final StringBuilder output = new StringBuilder(REC_BUFFER_CAPACITY);
            appendTimestamp(output, record.getTime());
            appendProductId(output);
            appendLogLevel(output, logLevel);
            appendMessageKey(output, msgId);
            appendLoggerName(output, loggerName);
            appendThread(output, record.getThreadID(), threadName);
            appendMillis(output, record.getMillis());
            appendLogLevelAsInt(output, logLevel);
            appendSequenceNumber(output, record.getSequenceNumber());
            appendSource(output, record.getSourceClassName(), record.getSourceMethodName());

            if (forceMultiline) {
                output.append(FIELD_BEGIN_MARKER).append(FIELD_BEGIN_MARKER);
                output.append(lineSeparator());
                output.append(MULTILINE_INDENTATION);
            }
            output.append(message);
            if (forceMultiline) {
                output.append(FIELD_END_MARKER).append(FIELD_END_MARKER);
            }
            output.append(lineSeparator()).append(lineSeparator());
            return output.toString();
        } catch (final Exception e) {
            PayaraLoggingTracer.error(getClass(), "Error in formatting Logrecord", e);
            return record.getMessage();
        }
    }

    private void appendTimestamp(final StringBuilder output, final OffsetDateTime timestamp) {
        output.append(FIELD_BEGIN_MARKER);
        output.append(getTimestampFormatter().format(timestamp));
        output.append(FIELD_END_MARKER).append(recordFieldSeparator);
    }

    private void appendProductId(final StringBuilder output) {
        output.append(FIELD_BEGIN_MARKER);
        final String productId = LoggingSystemEnvironment.getProductId();
        if (productId != null) {
            output.append(productId);
        }
        output.append(FIELD_END_MARKER).append(recordFieldSeparator);
    }

    private void appendLogLevel(final StringBuilder output, final Level logLevel) {
        output.append(FIELD_BEGIN_MARKER);
        final AnsiColor levelColor = getLevelColor(logLevel);
        if (levelColor != null) {
            output.append(levelColor);
        }
        output.append(logLevel.getName());
        if (levelColor != null) {
            output.append(AnsiColor.RESET);
        }
        output.append(FIELD_END_MARKER).append(recordFieldSeparator);
    }

    private void appendMessageKey(final StringBuilder output, final String msgId) {
        output.append(FIELD_BEGIN_MARKER);
        if (msgId != null) {
            output.append(msgId);
        }
        output.append(FIELD_END_MARKER).append(recordFieldSeparator);
    }

    private void appendLoggerName(final StringBuilder output, final String loggerName) {
        output.append(FIELD_BEGIN_MARKER);
        if (loggerName != null) {
            if (isAnsiColorEnabled()) {
                output.append(getLoggerColor());
            }
            output.append(loggerName);
            if (isAnsiColorEnabled()) {
                output.append(AnsiColor.RESET);
            }
        }
        output.append(FIELD_END_MARKER).append(recordFieldSeparator);
    }

    private void appendThread(final StringBuilder output, final int threadId, final String threadName) {
        if (!excludeFieldsSupport.isSet(SupplementalAttribute.TID)) {
            output.append(FIELD_BEGIN_MARKER);
            output.append("tid: ").append("_ThreadID=").append(threadId).append(" _ThreadName=").append(threadName);
            output.append(FIELD_END_MARKER).append(recordFieldSeparator);
        }
    }

    private void appendMillis(final StringBuilder output, final long millis) {
        if (!excludeFieldsSupport.isSet(SupplementalAttribute.TIME_MILLIS)) {
            output.append(FIELD_BEGIN_MARKER);
            output.append("timeMillis: ").append(millis);
            output.append(FIELD_END_MARKER).append(recordFieldSeparator);
        }
    }

    private void appendLogLevelAsInt(final StringBuilder output, final Level logLevel) {
        if (!excludeFieldsSupport.isSet(SupplementalAttribute.LEVEL_VALUE)) {
            output.append(FIELD_BEGIN_MARKER);
            output.append("levelValue: ").append(logLevel.intValue());
            output.append(FIELD_END_MARKER).append(recordFieldSeparator);
        }
    }

    private void appendSequenceNumber(final StringBuilder output, final long sequenceNumber) {
        if (isPrintSequenceNumber()) {
            output.append(FIELD_BEGIN_MARKER);
            output.append(LABEL_RECORDNUMBER).append(": ").append(sequenceNumber);
            output.append(FIELD_END_MARKER).append(recordFieldSeparator);
        }
    }

    private void appendSource(final StringBuilder output, final String className, final String methodName) {
        if (!isPrintSource()) {
            return;
        }
        if (className != null) {
            output.append(FIELD_BEGIN_MARKER);
            output.append(LABEL_CLASSNAME).append(": ").append(className);
            output.append(FIELD_END_MARKER).append(recordFieldSeparator);
        }
        if (methodName != null) {
            output.append(FIELD_BEGIN_MARKER);
            output.append(LABEL_METHODNAME).append(": ").append(methodName);
            output.append(FIELD_END_MARKER).append(recordFieldSeparator);
        }
    }

    /**
     * Configuration property set of this formatter
     */
    public enum ODLFormatterProperty implements LogProperty {

        /** Excluded fields from the output */
        EXCLUDED_FIELDS("excludedFields"),
        /** If false, each log record is on a single line (except messages containing new lines) */
        MULTILINE("multiline"),
        /** Character between record fields */
        FIELD_SEPARATOR("fieldSeparator"),
        ;

        private final String propertyName;

        ODLFormatterProperty(final String propertyName) {
            this.propertyName = propertyName;
        }


        @Override
        public String getPropertyName() {
            return propertyName;
        }
    }
}
