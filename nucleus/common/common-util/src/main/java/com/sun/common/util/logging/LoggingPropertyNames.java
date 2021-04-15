/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

// Portions Copyright [2018-2021] [Payara Foundation and/or its affiliates]

package com.sun.common.util.logging;

import fish.payara.jul.handler.PayaraLogHandler;
import fish.payara.jul.handler.SyslogHandler;

import static fish.payara.jul.handler.PayaraLogHandlerConfiguration.PayaraLogHandlerProperty.ROTATION_LIMIT_SIZE;
import static fish.payara.jul.handler.PayaraLogHandlerConfiguration.PayaraLogHandlerProperty.ROTATION_LIMIT_TIME;

public class LoggingPropertyNames {

    private static final String PAYARA_LOG_HANDLER = PayaraLogHandler.class.getName() + ".";

    private static final String PAYARA_NOTIFICATION_HANDLER = "fish.payara.enterprise.server.logging.PayaraNotificationFileHandler.";

    public static final String SyslogHandler = SyslogHandler.class.getName() + ".";

    public static final String logRotationLimitInBytes = ROTATION_LIMIT_SIZE.getPropertyFullName(PayaraLogHandler.class);

    public static final String payaraNotificationLogRotationLimitInBytes  = PAYARA_NOTIFICATION_HANDLER + "rotationLimitInBytes";

    public static final String logRotationTimelimitInMinutes = ROTATION_LIMIT_TIME.getPropertyFullName(PayaraLogHandler.class);

    public static final String payaraNotificationLogRotationTimelimitInMinutes  = PAYARA_NOTIFICATION_HANDLER + "rotationTimelimitInMinutes";

    public static final String file = PAYARA_LOG_HANDLER + "file";

    public static final String payaraNotificationFile  = PAYARA_NOTIFICATION_HANDLER + "file";

    public static final String logFormatter = PAYARA_LOG_HANDLER + "formatter";

    public static final String payaraNotificationLogFormatter = PAYARA_NOTIFICATION_HANDLER + "formatter";

    public static final String logHandler = "handlers";

    public static final String useSystemLogging = SyslogHandler + "useSystemLogging";

    public static final String retainErrorStatisticsForHours = PAYARA_LOG_HANDLER + "retainErrorsStasticsForHours";

    public static final String logToFile = PAYARA_LOG_HANDLER + "logtoFile";

    public static final String payaraNotificationLogToFile  = PAYARA_NOTIFICATION_HANDLER + "logtoFile";

    public static final String logToConsole = PAYARA_LOG_HANDLER + "logtoConsole";

    public static final String alarms = PAYARA_LOG_HANDLER + "alarms";

    public static final String logStandardStreams = PAYARA_LOG_HANDLER + "logStandardStreams";

    public static final String MAX_QUEUE_SIZE = PAYARA_LOG_HANDLER + "maxQueueSize";

    public static final String QUEUE_FLUSH_FREQUENCY = PAYARA_LOG_HANDLER + "queueFlushFrequency";

}

