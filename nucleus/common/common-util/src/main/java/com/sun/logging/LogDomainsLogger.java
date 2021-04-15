/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * Copyright (c) 2016 Payara Foundation. All rights reserved.
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 */
package com.sun.logging;

import fish.payara.jul.PayaraLogger;

import java.util.ResourceBundle;


/**
 * Reason for {@link ResourceBundle} management here - the Logger resource bundle resolution
 * is sensitive to caller's classloader. We also never change it.
 */
class LogDomainsLogger extends PayaraLogger {
    private final ResourceBundle resourceBundle;

    LogDomainsLogger(final String loggerName, final ResourceBundle resourceBundle) {
        super(loggerName);
        if (resourceBundle != null) {
            // The bundle is directly accessed internally in Logger class
            super.setResourceBundle(resourceBundle);
        }
        this.resourceBundle = resourceBundle;
    }


    @Override
    public ResourceBundle getResourceBundle() {
        return resourceBundle;
    }


    @Override
    public void setResourceBundle(ResourceBundle bundle) {
        // noop
    }
}
