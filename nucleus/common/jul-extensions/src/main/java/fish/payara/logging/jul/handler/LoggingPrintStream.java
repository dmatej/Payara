/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
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
// Portions Copyright [2016-2021] [Payara Foundation and/or affiliates]

package fish.payara.logging.jul.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * LoggingPrintStream creates a PrintStream with a LoggingByteArrayOutputStream as its
 * OutputStream. Once it is set as the System.out or System.err, all outputs to these
 * PrintStreams will end up in {@link LoggingOutputStream} which will log these on a flush.
 * <p>
 * This simple behavious has a negative side effect that stack traces are logged with
 * each line being a new log record. The reason for above is that printStackTrace converts each
 * line into a separate println, causing a flush at the end of each.
 * <p>
 * One option that was thought of to smooth this over was to see if the caller of println is
 * Throwable.[some set of methods].
 * Unfortunately, there are others who interpose on System.out and err
 * (like jasper) which makes that check untenable.
 * Hence the logic currently used is to see if there is a println(Throwable)
 * and do a printStackTrace and log the complete StackTrace ourselves.
 * If this is followed by a series of printlns, then we keep ignoring
 * those printlns as long as they were the same as that recorded by
 * the stackTrace. This needs to be captured on a per thread basis
 * to take care of potentially parallel operations.
 * <p>
 * Care is taken to optimise the frequently used path where exceptions
 * are not being printed.
 */
final class LoggingPrintStream extends PrintStream {
    private final LogManager logManager = LogManager.getLogManager();
    private final ThreadLocal<StackTraceObjects> perThreadStObjects = new ThreadLocal<>();
    // FIXME: this lock and all it's checking should probably go to LoggingOutputStream
    private final Logger logger;

    public static LoggingPrintStream create(final Logger logger, final Level level, final int bufferCapacity) {
        try {
            return new LoggingPrintStream(logger, level, bufferCapacity);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Impossible happened, UTF-8 is not supported!", e);
        }
    }


    private LoggingPrintStream(final Logger logger, final Level level, final int bufferCapacity)
        throws UnsupportedEncodingException {
        super(new LoggingOutputStream(logger, level, bufferCapacity), false, StandardCharsets.UTF_8.name());
        this.logger = logger;
    }


    @Override
    public void println(Object x) {
        if (!checkLocks()) {
            return;
        }

        if (x instanceof Throwable) {
            StackTraceObjects stackTraceObjects = new StackTraceObjects((Throwable) x);
            perThreadStObjects.set(stackTraceObjects);
            super.println(stackTraceObjects.toString());
        } else {
            // No special processing if it is not an exception.
            println(String.valueOf(x));
        }

    }

    @Override
    public PrintStream printf(String str, Object... args) {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb, Locale.getDefault())) {
            formatter.format(str, args);
        }
        print(sb.toString());
        return null;
    }

    @Override
    public PrintStream printf(Locale locale, String str, Object... args) {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb, locale)) {
            formatter.format(str,args);
        }
        print(sb.toString());
        return null;
    }

    @Override
    public PrintStream format(String format, Object... args) {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb, Locale.getDefault())) {
            formatter.format(format, args);
        }
        print(sb.toString());
        return null;
    }

    @Override
    public PrintStream format(Locale locale, String format, Object... args) {
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb, locale)) {
            formatter.format(format, args);
        }
        print(sb.toString());
        return null;
    }

    @Override
    public void println(String str) {
        if (!checkLocks()) {
            return;
        }

        final StackTraceObjects sTO = perThreadStObjects.get();
        if (sTO == null) {
            // lets get done with the common case fast
            super.println(str);
            flush();
            return;
        }

        if (!sTO.ignorePrintln(str)) {
            perThreadStObjects.set(null);
            super.println(str);
            flush();
            return;
        }

        if (sTO.checkCompletion()) {
            perThreadStObjects.set(null);
            return;
        }
    }

    @Override
    public void print(String x) {
        if (checkLocks()) {
            super.print(x);
        }
    }


    @Override
    public void print(Object x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void print(boolean x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(boolean x) {
        if (checkLocks()) {
            super.println(x);
        }
    }

    @Override
    public void print(char x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(char x) {
        if (checkLocks()) {
            super.println(x);
        }
    }

    @Override
    public void print(int x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(int x) {
        if (checkLocks()) {
            super.println(x);
        }
    }

    @Override
    public void print(long x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(long x) {
        if (checkLocks()) {
            super.println(x);
        }
    }

    @Override
    public void print(float x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(float x) {
        if (checkLocks()) {
            super.println(x);
        }
    }

    @Override
    public void print(double x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(double x) {
        if (checkLocks()) {
            super.println(x);
        }
    }

    @Override
    public void print(char[] x) {
        if (checkLocks()) {
            super.print(x);
        }
    }

    @Override
    public void println(char[] x) {
        if (checkLocks()) {
            super.println(x);
        }
    }


    @Override
    public void println() {
        if (checkLocks()) {
            super.println();
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        try {
            synchronized (this) {
                if (out == null) {
                    throw new IOException("Stream closed");
                }
                out.write(buf, off, len);
                out.flush();
            }
        } catch (InterruptedIOException x) {
            Thread.currentThread().interrupt();
        } catch (IOException x) {
            setError();
        }
    }

    @Override
    public void write(int b) {
        if (checkLocks()) {
            super.write(b);
        }
    }


    /**
     * LoggingPrintStream class is to support the java System.err and System.out
     * redirection to Appserver log file--server.log.
     * <p>
     * When Java IO is redirected and System.out.println(...) is invoked by a thread with
     * LogManager or Logger(SYSTEMERR_LOGGER,SYSTEOUT_LOGGER) locked, all kind of dead
     * locks among threads will happen.
     * <p>
     * These dead locks are easily reproduced when jvm system properties
     * "-Djava.security.manager" and "-Djava.security.debug=access,failure" are defined.
     * These dead locks are basically because each thread has its own sequence of
     * acquiring lock objects(LogManager,Logger,FileandSysLogHandler, the buffer inside
     * LoggingPrintStream).
     * <p>
     * There is no obvious way to define the lock hierarchy and control the lock sequence;
     * Trylock is not a strightforward solution either.Beside they both create heavy
     * dependence on the detail implementation of JDK and Appserver.
     * <p>
     * This method(checkLocks) is to find which locks current thread has and
     * LoggingPrintStream object will decide whether to continue to do printing or
     * give ip up to avoid the dead lock.
     */
    private boolean checkLocks() {
        return !Thread.holdsLock(logger) && !Thread.holdsLock(logManager);
    }


    /**
     * StackTraceObjects keeps track of StackTrace printed
     * by a thread as a result of println(Throwable) and
     * it keeps track of subsequent println(String) to
     * avoid duplicate logging of stacktrace
     */
    // FIXME: smells alot - is all that magic worth?
    private static final class StackTraceObjects {

        private final ByteArrayOutputStream stackTraceBuf;
        private final PrintStream stStream;
        private final String stString;
        private final ByteArrayOutputStream comparisonBuf;
        private final PrintStream cbStream;
        private final int stackTraceBufBytes;
        private int charsIgnored = 0;

        private StackTraceObjects(Throwable throwable) {
            // alloc buffer for getting stack trace.
            stackTraceBuf = new ByteArrayOutputStream();
            stStream = new PrintStream(stackTraceBuf, true);
            comparisonBuf = new ByteArrayOutputStream();
            cbStream = new PrintStream(comparisonBuf, true);
            throwable.printStackTrace(stStream);
            stString = stackTraceBuf.toString();
            stackTraceBufBytes = stackTraceBuf.size();
            // helps keep our stack trace skipping logic simpler.
            cbStream.println(throwable);
        }

        @Override
        public String toString() {
            return stString;
        }

        boolean ignorePrintln(String str) {
            String cbString;
            int cbLen;
            cbStream.println(str);
            cbString = comparisonBuf.toString();
            cbLen = cbString.length();
            if (stString.regionMatches(charsIgnored, cbString, 0, cbLen)) {
                charsIgnored += cbLen;
                comparisonBuf.reset();
                return true;
            }

            return false;

        }

        boolean checkCompletion() {
            return charsIgnored >= stackTraceBufBytes;
        }
    }
}
