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

package fish.payara.acme.handler;

import fish.payara.logging.jul.env.LoggingSystemEnvironment;
import fish.payara.logging.jul.formatter.OneLineFormatter;
import fish.payara.logging.jul.handler.PayaraLogHandler;
import fish.payara.logging.jul.handler.PayaraLogHandlerConfiguration;
import fish.payara.logging.jul.record.EnhancedLogRecord;
import fish.payara.logging.jul.tracing.PayaraLoggingTracer;

import java.io.File;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static fish.payara.logging.jul.env.LoggingSystemEnvironment.getOriginalStdErr;
import static fish.payara.logging.jul.env.LoggingSystemEnvironment.getOriginalStdOut;
import static fish.payara.logging.jul.handler.PayaraLogHandler.createPayaraLogHandlerConfiguration;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author David Matejcek
 * @author Patrik Dudits
 */
@TestMethodOrder(OrderAnnotation.class)
public class PayaraLogHandlerTest {

    private static final long MILLIS_FOR_PUMP = 10L;
    private static PayaraLogHandler handler;

    @BeforeAll
    public static void initEnv() throws Exception {
        PayaraLoggingTracer.setTracingEnabled(true);
        LogManager.getLogManager().reset();
        LoggingSystemEnvironment.initialize();
        final PayaraLogHandlerConfiguration cfg = new PayaraLogHandlerConfiguration();
        cfg.setLogFile(File.createTempFile(PayaraLogHandlerTest.class.getCanonicalName(), ".log"));
        cfg.setFormatterConfiguration(new OneLineFormatter());
        handler = new PayaraLogHandler(cfg);
        getRootLogger().addHandler(handler);
    }


    @AfterAll
    public static void resetEverything() {
        getRootLogger().removeHandler(handler);
        if (handler != null) {
            handler.close();
        }
        LogManager.getLogManager().reset();
        PayaraLoggingTracer.setTracingEnabled(false);
    }


    private static Logger getRootLogger() {
        return LogManager.getLogManager().getLogger("");
    }


    @Test
    @Order(10)
    public void enablelogStandardStreams() throws Exception {
        assertTrue(handler.isReady(), "handler.ready");
        final PayaraLogHandlerConfiguration cfg = handler.getConfiguration();
        cfg.setLogStandardStreams(true);
        cfg.setFlushFrequency(2);
        handler.reconfigure(cfg);
        assertAll(
            () -> assertTrue(handler.isReady(), "handler.ready"),
            () -> assertNotSame(System.out, getOriginalStdOut(), "System.out should be redirected"),
            () -> assertNotSame(System.err, getOriginalStdErr(), "System.err should be redirected")
        );

        System.out.println("Tommy, can you hear me?");
        // output stream is pumped in parallel to the error stream, order is not guaranteed between streams
        Thread.sleep(MILLIS_FOR_PUMP);
        System.err.println("Can you feel me near you?");
        System.err.println("Příliš žluťoučký kůň úpěl ďábelské ódy");
        Thread.sleep(MILLIS_FOR_PUMP);
        assertAll(
            () -> assertTrue(handler.isReady(), "handler.ready"),
            () -> assertTrue(cfg.getLogFile().exists(), "file exists"),
            () -> assertThat("file content: \n", Files.readAllLines(cfg.getLogFile().toPath()),
                contains(
                    stringContainsInOrder("INFO", "main", "Tommy, can you hear me?"),
                    stringContainsInOrder("SEVERE", "main", "Can you feel me near you?"),
                    stringContainsInOrder("SEVERE", "main", "Příliš žluťoučký kůň úpěl ďábelské ódy"))));
    }


    @Test
    @Order(30)
    public void roll() throws Exception {
        assertTrue(handler.isReady(), "handler.ready");
        handler.publish(new EnhancedLogRecord(Level.SEVERE, "File one, line one"));
        // pump is now to play
        Thread.sleep(MILLIS_FOR_PUMP);
        assertAll(
            () -> assertTrue(handler.isReady(), "handler.ready"),
            () -> assertTrue(handler.getConfiguration().getLogFile().exists(), "file one exists"),
            () -> assertThat("size of file one", handler.getConfiguration().getLogFile().length(), greaterThan(0L))
        );
        handler.roll();
        assertAll(
            () -> assertTrue(handler.isReady(), "handler.ready"),
            () -> assertTrue(handler.getConfiguration().getLogFile().exists(), "file exists"),
            () -> assertThat("size of file two", handler.getConfiguration().getLogFile().length(), equalTo(0L))
        );
        handler.publish(new EnhancedLogRecord(Level.SEVERE, "File two, line one"));
        Thread.sleep(MILLIS_FOR_PUMP);
        assertAll(
            () -> assertTrue(handler.isReady(), "handler.ready"),
            () -> assertTrue(handler.getConfiguration().getLogFile().exists(), "file exists"),
            () -> assertThat("size of file two", handler.getConfiguration().getLogFile().length(), greaterThan(0L))
        );
    }


    @Test
    @Order(50)
    public void disabledlogStandardStreams() throws Exception {
        assertTrue(handler.isReady(), "handler.ready");
        final PayaraLogHandlerConfiguration cfg = handler.getConfiguration();
        cfg.setLogStandardStreams(false);
        handler.reconfigure(cfg);
        assertAll(
            () -> assertTrue(handler.isReady(), "handler.ready"),
            () -> assertSame(System.out, getOriginalStdOut(), "System.out should not be redirected"),
            () -> assertSame(System.err, getOriginalStdErr(), "System.err should not be redirected")
        );
    }


    @Test
    @Order(60)
    public void createConfiguration() throws Exception {
        final PayaraLogHandlerConfiguration cfg = createPayaraLogHandlerConfiguration(PayaraLogHandler.class);
        assertNotNull(cfg, "cfg");
    }
}
