/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) 2020-2021 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.logging.jul.formatter;

import fish.payara.logging.jul.record.EnhancedLogRecord;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static fish.payara.logging.jul.cfg.PayaraLoggingConstants.JVM_OPT_LOGGING_KEYVALUE_LOGSOURCE;
import static fish.payara.logging.jul.cfg.PayaraLoggingConstants.JVM_OPT_LOGGING_KEYVALUE_RECORDNUMBER;


/**
 * A special {@link Formatter} able to notify some delegate about the {@link LogRecord} which passed
 * through this instance.
 *
 * @author David Matejcek
 */
public abstract class BroadcastingFormatter extends Formatter {

    private static final boolean LOG_SOURCE_IN_KEY_VALUE = Boolean.getBoolean(JVM_OPT_LOGGING_KEYVALUE_LOGSOURCE);
    private static final boolean RECORD_NUMBER_IN_KEY_VALUE = Boolean.getBoolean(JVM_OPT_LOGGING_KEYVALUE_RECORDNUMBER);

    private String productId;
    private boolean printRecordNumber;
    private boolean printSource;

    public BroadcastingFormatter() {
        this.printRecordNumber = RECORD_NUMBER_IN_KEY_VALUE;
        this.printSource = LOG_SOURCE_IN_KEY_VALUE;
    }

    /**
     * Formats the record.
     *
     * @param record
     * @return formatted record, final record for output
     */
    protected abstract String formatRecord(LogRecord record);

    /**
     * @param printRecordNumber true enables printing the log record sequence number
     */
    public void setPrintRecordNumber(final boolean printRecordNumber) {
        this.printRecordNumber = printRecordNumber;
    }

    /**
     * @return true enables printing the log record sequence number
     */
    public boolean isPrintRecordNumber() {
        return printRecordNumber;
    }

    /**
     * @param printSource if true, the source class and method will be printed to the output (but
     *            only if they are set)
     */
    public void setPrintSource(final boolean printSource) {
        this.printSource = printSource;
    }

    /**
     * @return if true, the source class and method will be printed to the output (but
     *         only if they are set)
     */
    public boolean isPrintSource() {
        return printSource;
    }

    /**
     * @param productId some meaningful identification of the application; default is null.
     */
    public final void setProductId(final String productId) {
        this.productId = productId;
    }

    /**
     * @return Usually some meaningful identification of the application; default is null.
     */
    public final String getProductId() {
        return productId;
    }


    @Override
    public String formatMessage(final LogRecord record) {
        throw new UnsupportedOperationException("String formatMessage(LogRecord record)");
    }


    @Override
    public final String format(final LogRecord record) {
        return formatRecord(record);
    }


    /**
     * @param record
     * @return a record's message plus printed stacktrace if some throwable is present.
     */
    protected String getPrintedMessage(final EnhancedLogRecord record) {
        final String message = record.getMessage();
        final String stackTrace = record.getThrownStackTrace();
        if (message == null || message.isEmpty()) {
            return stackTrace == null ? null : stackTrace;
        }
        if (stackTrace == null) {
            return message;
        }
        return message + System.lineSeparator() + stackTrace;
    }
}
