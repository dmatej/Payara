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

package fish.payara.logging.jul.handler;

import fish.payara.logging.jul.record.EnhancedLogRecord;
import fish.payara.logging.jul.tracing.PayaraLoggingTracer;

import java.util.logging.Handler;

import static fish.payara.logging.jul.tracing.PayaraLoggingTracer.trace;


/**
 * The logging pump is a special thread with high priority, processing {@link EnhancedLogRecord}
 * instances in the {@link LogRecordBuffer} of the {@link Handler}
 *
 * @author David Matejcek
 */
abstract class LoggingPumpThread extends Thread {

    private final LogRecordBuffer buffer;


    protected LoggingPumpThread(final String threadName, final LogRecordBuffer buffer) {
        super(threadName);
        setDaemon(true);
        setPriority(Thread.MAX_PRIORITY);
        this.buffer = buffer;
    }


    /**
     * @return true if the pump should stop
     */
    protected abstract boolean isShutdownRequested();


    /**
     * @return count of records processed until the {@link #flushOutput()} is called.
     */
    protected abstract int getFlushFrequency();

    /**
     * @param record null or record to log
     * @return false if the record was null, so the buffer is empty or waiting was interrupted
     */
    protected abstract boolean logRecord(final EnhancedLogRecord record);

    /**
     * Unconditionally flushes the output
     */
    protected abstract void flushOutput();


    @Override
    public final void run() {
        trace(PayaraLogHandler.class, () -> "Logging pump for " + buffer + " started.");
        while (!isShutdownRequested()) {
            try {
                publishBatchFromBuffer();
            } catch (final Exception e) {
                PayaraLoggingTracer.error(getClass(), "Log record not published.", e);
                // Continue the loop without exiting
            }
        }
    }


    /**
     * Retrieves the LogRecord from our Queue and store them in the file
     */
    private void publishBatchFromBuffer() {
        if (!logRecord(buffer.pollOrWait())) {
            return;
        }
        if (getFlushFrequency() > 1) {
            // starting from 1, one record was already published
            for (int i = 1; i < getFlushFrequency(); i++) {
                if (!logRecord(buffer.poll())) {
                    break;
                }
            }
        }
        flushOutput();
    }
}