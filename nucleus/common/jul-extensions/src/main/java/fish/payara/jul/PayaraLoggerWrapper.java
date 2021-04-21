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

package fish.payara.jul;

import fish.payara.jul.tracing.PayaraLoggingTracer;

import java.util.ResourceBundle;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Special {@link PayaraLogger} for system loggers which have non-standard behavior.
 *
 * @author David Matejcek
 */
class PayaraLoggerWrapper extends PayaraLogger {

    private final Logger logger;


    PayaraLoggerWrapper(final Logger logger) {
        super(logger.getName());
        this.logger = logger;
        // warn: jul uses setters, but instead of getters it uses fields directly.
        // it makes no sense to call setters here, values are not initialized yet.
    }


    @Override
    public Logger getParent() {
        return this.logger.getParent();
    }


    @Override
    public void setParent(final Logger parent) {
        PayaraLoggingTracer.trace(PayaraLoggerWrapper.class, () -> "setParent(" + parent + "); this: " + this);
        this.logger.setParent(parent);
        // JUL uses this field directly
        super.setParent(logger.getParent());
    }


    @Override
    public Level getLevel() {
        return this.logger.getLevel();
    }


    @Override
    public void setLevel(final Level newLevel) throws SecurityException {
        PayaraLoggingTracer.trace(PayaraLoggerWrapper.class, () -> "setLevel(" + newLevel + "); this: " + this);
        this.logger.setLevel(newLevel);
        // JUL uses this field directly
        super.setLevel(newLevel);
    }


    @Override
    public Filter getFilter() {
        return this.logger.getFilter();
    }


    @Override
    public void setFilter(final Filter newFilter) throws SecurityException {
        this.logger.setFilter(newFilter);
    }


    @Override
    public ResourceBundle getResourceBundle() {
        return this.logger.getResourceBundle();
    }


    @Override
    public void setResourceBundle(final ResourceBundle bundle) {
        this.logger.setResourceBundle(bundle);
    }


    @Override
    public String getResourceBundleName() {
        return this.logger.getResourceBundleName();
    }


    @Override
    public boolean getUseParentHandlers() {
        return this.logger.getUseParentHandlers();
    }


    @Override
    public void setUseParentHandlers(final boolean useParentHandlers) {
        this.logger.setUseParentHandlers(useParentHandlers);
    }


    @Override
    public Handler[] getHandlers() {
        return this.logger.getHandlers();
    }


    @Override
    protected boolean isLoggableLevel(final Level level) {
        return this.logger.isLoggable(level);
    }


    @Override
    public void addHandler(final Handler handler) throws SecurityException {
        PayaraLoggingTracer.trace(PayaraLoggerWrapper.class, () -> "addHandler(" + handler + ")");
        this.logger.addHandler(handler);
    }


    @Override
    public void removeHandler(final Handler handler) throws SecurityException {
        PayaraLoggingTracer.trace(PayaraLoggerWrapper.class, () -> "removeHandler(" + handler + ")");
        this.logger.removeHandler(handler);
    }
}
