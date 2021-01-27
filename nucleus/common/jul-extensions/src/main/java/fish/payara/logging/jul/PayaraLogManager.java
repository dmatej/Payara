/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) 2019-2020 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.logging.jul;

import fish.payara.logging.jul.internal.PayaraLoggingTracer;
import fish.payara.logging.jul.internal.StartupQueue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fish.payara.logging.jul.LoggingConfigurationHelper.PRINT_TO_STDERR;
import static fish.payara.logging.jul.PayaraLoggingConstants.JVM_OPT_LOGGING_CFG_FILE;
import static java.util.logging.Logger.GLOBAL_LOGGER_NAME;


/**
 * @author David Matejcek
 */
public class PayaraLogManager extends LogManager {

    /** Property key for a list of root handler implementations */
    public static final String KEY_ROOT_HANDLERS = "handlers";
    /** Property key for a level of system root logger. System root loggers children are not configurable. */
    public static final String KEY_SYS_ROOT_LOGGER_LEVEL = "systemRootLoggerLevel";
    /** Property key for a level of user root logger. User root loggers children can have own level.  */
    public static final String KEY_USR_ROOT_LOGGER_LEVEL = ".level";
    /** Empty string - standard root logger name */
    public static final String ROOT_LOGGER_NAME = "";

    private static volatile PayaraLoggingStatus status = PayaraLoggingStatus.UNINITIALIZED;
    private static PayaraLogManager payaraLogManager;
    private static final AtomicBoolean protectBeforeReset = new AtomicBoolean(true);

    // Cannot be static, log manager is initialized very early
    private final PrintStream originalStdOut;
    private final PrintStream originalStdErr;

    private volatile PayaraLogger systemRootLogger;
    private volatile PayaraLogger userRootLogger;
    private volatile PayaraLogger globalLogger;

    private PayaraLogManagerConfiguration cfg;


    static synchronized boolean initialize(final Properties configuration) throws SecurityException, IOException {
        PayaraLoggingTracer.trace(PayaraLogManager.class, "initialize(configuration)");
        if (status.ordinal() > PayaraLoggingStatus.UNINITIALIZED.ordinal()) {
            PayaraLoggingTracer.error(PayaraLogManager.class,
                "Initialization of the logging system failed - it was already executed");
            return false;
        }
        // We must respect that LogManager.getLogManager()
        // - creates final root and global loggers,
        // - calls also addLogger.
        // - calls setLevel if the level was not set in addLogger.
        // OR something already configured another log manager implementation
        // Therefore status is now moved directly to UNCONFIGURED
        status = PayaraLoggingStatus.UNCONFIGURED;
        final PayaraLogManager logManager = getLogManager();
        logManager.doFirstInitialization(configuration == null ? provideProperties() : configuration);
        return true;
    }


    /**
     * @return true if {@link PayaraLogManager} is configured as the JVM log manager.
     */
    public static boolean isPayaraLogManager() {
        if (payaraLogManager == null) {
            return getLogManager() != null;
        }
        return payaraLogManager != null;
    }


    public static PayaraLogManager getLogManager() {
        if (payaraLogManager != null) {
            return payaraLogManager;
        }
        synchronized (PayaraLogManager.class) {
            final LogManager logManager = LogManager.getLogManager();
            if (logManager instanceof PayaraLogManager) {
                payaraLogManager = (PayaraLogManager) logManager;
                return payaraLogManager;
            }
            PayaraLoggingTracer.error(PayaraLogManager.class, "PayaraLogManager not available, using " + logManager);
            PayaraLoggingTracer.error(PayaraLogManager.class, "Classloader used:" //
                + "\n here:  " + PayaraLogManager.class.getClassLoader() //
                + "\n there: " + logManager.getClass().getClassLoader());
            return null;
        }
    }


    /**
     * @deprecated call this constructor directly. Use {@link LogManager#getLogManager()} instead.
     * See {@link LogManager} javadoc for more.
     */
    @Deprecated
    public PayaraLogManager() {
        PayaraLoggingTracer.trace(PayaraLogManager.class, "new PayaraLogManager()");
        this.originalStdOut = System.out;
        this.originalStdErr = System.err;
    }


    private void doFirstInitialization(final Properties configuration) {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "Initializing logManager: " + this);
        try {
            protectBeforeReset.set(false);
            setLoggingStatus(PayaraLoggingStatus.UNCONFIGURED);
            this.cfg = new PayaraLogManagerConfiguration(configuration);
            initializeRootLoggers();
            reconfigure(this.cfg);
        } catch (final Exception e) {
            PayaraLoggingTracer.error(PayaraLogManager.class, "Initialization of " + this + " failed!", e);
            throw e;
        } finally {
            protectBeforeReset.set(true);
        }
    }


    @Override
    public String getProperty(final String name) {
        return this.cfg == null ? null : this.cfg.getProperty(name);
    }

    /**
     * {@inheritDoc}
     * @return false to force caller to refind the new logger, true to inform him that we did not add it.
     */
    @Override
    public boolean addLogger(final Logger logger) {
        Objects.requireNonNull(logger, "logger is null");
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "addLogger(logger.name=" + logger.getName() + ")");

        if (getLoggingStatus().ordinal() < PayaraLoggingStatus.CONFIGURING.ordinal()) {
            try {
                // initialization of system loggers in LogManager.ensureLogManagerInitialized
                // ignores output of addLogger. That's why we use wrappers.
                if (ROOT_LOGGER_NAME.equals(logger.getName())) {
                    PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "System root logger catched: " + logger + ")");
                    this.systemRootLogger = new PayaraLoggerWrapper(logger);
                    // do not add system logger to user context. Create own root instead.
                    // reason: LogManager.ensureLogManagerInitialized ignores result of addLogger,
                    // so there is no way to override it. So leave it alone.
                    this.userRootLogger = new PayaraLogger(ROOT_LOGGER_NAME);
                    return super.addLogger(userRootLogger);
                }
                if (GLOBAL_LOGGER_NAME.equals(logger.getName())) {
                    PayaraLoggingTracer.trace(PayaraLogManager.class,
                        () -> "System global logger catched: " + logger + ")");
                    this.globalLogger = new PayaraLoggerWrapper(Logger.getGlobal());
                    return super.addLogger(globalLogger);
                }
            } finally {
                // if we go directly through constructor without initialize(cfg)
                if (this.systemRootLogger != null && this.globalLogger != null
                    && getLoggingStatus() == PayaraLoggingStatus.UNINITIALIZED) {
                    doFirstInitialization(provideProperties());
                }
            }
        }

        final PayaraLogger replacementLogger = replaceWithPayaraLogger(logger);
        final boolean loggerAdded = super.addLogger(replacementLogger);
        if (loggerAdded && replacementLogger.getParent() == null
            && !ROOT_LOGGER_NAME.equals(replacementLogger.getName())) {
            replacementLogger.setParent(getRootLogger());
        }
        // getLogger must refetch if we wrapped the original instance.
        // note: JUL ignores output for system loggers
        return loggerAdded && replacementLogger == logger;
    }


    @Override
    public PayaraLogger getLogger(final String name) {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "getLogger(name=" + name + ")");
        Objects.requireNonNull(name, "logger name is null");
        // we are hiding the real root and global loggers, because they cannot be overriden
        // directly by PayaraLogger
        if (ROOT_LOGGER_NAME.equals(name)) {
            return getRootLogger();
        }
        if (GLOBAL_LOGGER_NAME.equals(name)) {
            return this.globalLogger;
        }
        final Logger logger = super.getLogger(name);
        if (logger == null) {
            return null;
        }
        if (logger instanceof PayaraLogger) {
            return (PayaraLogger) logger;
        }
        // First request to Logger.getLogger calls LogManager.demandLogger which calls
        // addLogger, which caches the logger and can be overriden, but returns unwrapped
        // logger.
        // Second request is from the cache OR is a special logger like the global logger.
        return ensurePayaraLoggerOrWrap(super.getLogger(name));
    }


    /**
     * Don't call this method. It is used by {@link LogManager} and removes all handlers, so
     * Payara logging will not work after that.
     */
    @Override
    public synchronized void reset() {
        // reset causes closing of current handlers
        // reset is invoked automatically also in the begining of super.readConfiguration(is).
        // btw LogManager.createLogHandlers exists in JDK11, but not in JDK8
        if (protectBeforeReset.get()) {
            PayaraLoggingTracer.trace(PayaraLogManager.class, "reset() ignored.");
            return;
        }
        super.reset();
        PayaraLoggingTracer.trace(PayaraLogManager.class, "reset() done.");
    }


    /**
     * Ignored. Does nothing!
     */
    @Override
    public synchronized void readConfiguration() throws SecurityException, IOException {
        PayaraLoggingTracer.trace(PayaraLogManager.class, "readConfiguration() ignored.");
    }


    @Override
    public synchronized void readConfiguration(final InputStream input) throws SecurityException, IOException {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "readConfiguration(ins=" + input + ")");
        this.cfg = new PayaraLogManagerConfigurationParser().parse(input);
        PayaraLoggingTracer.trace(PayaraLogManager.class, "readConfiguration(input) done.");
    }


    public synchronized void resetAndReadConfiguration(final InputStream input) throws SecurityException, IOException {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "resetAndReadConfiguration(ins=" + input + ")");
        try {
            protectBeforeReset.set(false);
            reset();
            readConfiguration(input);
        } finally {
            protectBeforeReset.set(true);
        }
    }


    public synchronized void resetAndReadConfiguration(final File loggingProperties) throws SecurityException, IOException {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "resetAndReadConfiguration(file=" + loggingProperties + ")");
        try (InputStream is = new FileInputStream(loggingProperties)) {
            resetAndReadConfiguration(is);
        }
    }

    public PayaraLoggingStatus getLoggingStatus() {
        return status;
    }


    private void setLoggingStatus(final PayaraLoggingStatus status) {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "setLoggingStatus(status=" + status + ")");
        PayaraLogManager.status = status;
    }


    public PayaraLogHandler getPayaraLogHandler() {
        return getRootLogger().getHandler(PayaraLogHandler.class);
    }


    public List<PayaraLogger> getAllLoggers() {
        return Collections.list(getLoggerNames()).stream().map(this::getLogger).filter(Objects::nonNull)
            .collect(Collectors.toList());
    }


    public List<Handler> getAllHandlers() {
        final Function<PayaraLogger, Stream<Handler>> toHandler = logger -> Arrays.stream(logger.getHandlers());
        return Collections.list(getLoggerNames()).stream().map(this::getLogger).filter(Objects::nonNull)
            .flatMap(toHandler).collect(Collectors.toList());
    }


    /**
     * @return can be null only when {@link LogManager} does initialization.
     */
    public PayaraLogger getRootLogger() {
        return this.userRootLogger;
    }


    public void resetStandardOutputs() {
        System.setOut(originalStdOut);
        System.setErr(originalStdErr);
    }


    public PrintStream getOriginalStdOut() {
        return originalStdOut;
    }


    public PrintStream getOriginalStdErr() {
        return originalStdErr;
    }


    public void reconfigure(final PayaraLogManagerConfiguration cfg) {
        reconfigure(cfg, null, null);
    }


    public synchronized void reconfigure(final PayaraLogManagerConfiguration cfg, final Action reconfigureAction,
        final Action flushAction) {
        PayaraLoggingTracer.trace(PayaraLogManager.class,
            () -> "reconfigure(cfg, action, action); Configuration:\n" + cfg);
        if (cfg.isTracingEnabled()) {
            // if enabled, start immediately
            PayaraLoggingTracer.setTracing(cfg.isTracingEnabled());
        }
        setLoggingStatus(PayaraLoggingStatus.CONFIGURING);
        this.cfg = cfg;
        // it is used to configure new objects in LogManager class
        final Thread currentThread = Thread.currentThread();
        final ClassLoader originalCL = currentThread.getContextClassLoader();
        try {
            PayaraLoggingTracer.trace(PayaraLogManager.class, "Reconfiguring logger levels...");
            final Enumeration<String> existingLoggerNames = getLoggerNames();
            while (existingLoggerNames.hasMoreElements()) {
                final String existingLoggerName = existingLoggerNames.nextElement();
                if (ROOT_LOGGER_NAME.equals(existingLoggerName)) {
                    this.systemRootLogger.setLevel(getLevel(KEY_SYS_ROOT_LOGGER_LEVEL, Level.INFO));
                    this.userRootLogger.setLevel(getLevel(KEY_USR_ROOT_LOGGER_LEVEL, Level.INFO));
                    continue;
                }
                final PayaraLogger logger = getLogger(existingLoggerName);
                if (logger != null) {
                    final Level level = getLevel(existingLoggerName + ".level", null);
                    PayaraLoggingTracer.trace(PayaraLogManager.class,
                        "Configuring logger level for '" + existingLoggerName + "' to '" + level + "'");
                    // null means inherit from parent
                    logger.setLevel(level);
                }
            }
            PayaraLoggingTracer.trace(PayaraLogManager.class, "Updated logger levels successfully.");

            if (reconfigureAction != null) {
                try {
                    currentThread.setContextClassLoader(reconfigureAction.getClassLoader());
                    reconfigureAction.run();
                } finally {
                    currentThread.setContextClassLoader(originalCL);
                }
            }

            final Predicate<Handler> isReadyPredicate = h -> !PayaraLogHandler.class.isInstance(h)
                || PayaraLogHandler.class.cast(h).isReady();
            final List<Handler> handlers = getAllHandlers();
            if (handlers.isEmpty() || handlers.stream().allMatch(isReadyPredicate)) {
                setLoggingStatus(PayaraLoggingStatus.FLUSHING_BUFFERS);
                if (flushAction != null) {
                    try {
                        currentThread.setContextClassLoader(flushAction.getClassLoader());
                        flushAction.run();
                    } finally {
                        currentThread.setContextClassLoader(originalCL);
                    }
                }
                final StartupQueue queue = StartupQueue.getInstance();
                queue.toStream().forEach(o -> o.getLogger().checkAndLog(o.getRecord()));
                queue.reset();
                setLoggingStatus(PayaraLoggingStatus.FULL_SERVICE);
            }
        } finally {
            // regardless of the result, set tracing.
            PayaraLoggingTracer.setTracing(cfg.isTracingEnabled());
        }
    }


    public void closeAllExternallyManagedLogHandlers() {
        PayaraLoggingTracer.trace(PayaraLogManager.class, "closeAllExternallyManagedLogHandlers()");
        final List<PayaraLogger> loggers = getAllLoggers();
        // single handler instance can be used by more loggers
        final Set<Handler> handlersToClose = new HashSet<>();
        final Consumer<PayaraLogger> remover = logger -> {
            final List<Handler> handlers = logger.getHandlers(ExternallyManagedLogHandler.class);
            handlersToClose.addAll(handlers);
            handlers.forEach(logger::removeHandler);
        };
        loggers.forEach(remover);
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "Handlers to be closed: " + handlersToClose);
        handlersToClose.forEach(Handler::close);
    }


    private static PayaraLogger replaceWithPayaraLogger(final Logger logger) {
        PayaraLoggingTracer.trace(PayaraLogManager.class, "replaceWithPayaraLogger(" + logger.getName() + ")");
        if (logger instanceof PayaraLogger) {
            return (PayaraLogger) logger;
        }
        return new PayaraLogger(logger);
    }


    /**
     * This is a fialsafe method to wrapp any logger which would miss standard mechanisms.
     * Invocation of this method would mean that something changed in JDK implementation
     * and this module must be updated.
     * <p>
     * Prints error to STDERR if
     *
     * @param logger
     * @return {@link PayaraLogger} or {@link PayaraLoggerWrapper}
     */
    private PayaraLogger ensurePayaraLoggerOrWrap(final Logger logger) {
        if (logger instanceof PayaraLogger) {
            return (PayaraLogger) logger;
        }
        PayaraLoggingTracer.error(getClass(), "Emergency wrapping logger!", new RuntimeException());
        return new PayaraLoggerWrapper(logger);
    }


    private void initializeRootLoggers() {
        PayaraLoggingTracer.trace(PayaraLogManager.class, "initializeRootLoggers()");
        this.globalLogger.setParent(getRootLogger());
        final PayaraLogger referenceLogger = getRootLogger();
        final LoggingConfigurationHelper parser = new LoggingConfigurationHelper(PRINT_TO_STDERR);
        final List<String> requestedHandlers = parser.getList(KEY_ROOT_HANDLERS, null);
        final List<Handler> currentHandlers = Arrays.asList(referenceLogger.getHandlers());

        // this is to have a single handler for both loggers without common parent logger.
        final List<Handler> handlersToAdd = new ArrayList<>();
        for (final String handlerClass : requestedHandlers) {
            if (currentHandlers.stream().noneMatch(h -> h.getClass().getName().equals(handlerClass))) {
                final Handler newHandler = create(handlerClass);
                if (newHandler != null) {
                    handlersToAdd.add(newHandler);
                }
            }
        }

        final Level systemRootLevel = getLevel(KEY_SYS_ROOT_LOGGER_LEVEL, Level.INFO);
        configureRootLogger(systemRootLogger, systemRootLevel, requestedHandlers, handlersToAdd);

        final Level rootLoggerLevel = getLevel(KEY_USR_ROOT_LOGGER_LEVEL, Level.INFO);
        configureRootLogger(userRootLogger, rootLoggerLevel, requestedHandlers, handlersToAdd);
        setUserRootLoggerMissingParents(userRootLogger);
        // TODO: probably check for system root loggers as parents and move them to user?
    }

    private void setUserRootLoggerMissingParents(final PayaraLogger rootParentLogger) {
        final Enumeration<String> names = getLoggerNames();
        while (names.hasMoreElements()) {
            final String name = names.nextElement();
            final PayaraLogger logger = getLogger(name);
            if (logger != null && logger.getParent() == null && !ROOT_LOGGER_NAME.equals(logger.getName())) {
                PayaraLoggingTracer.error(getClass(), "Setting parent to logger: " + logger.getName() + "/" + logger);
                logger.setParent(rootParentLogger);
            }
        }
    }


    private void configureRootLogger(final PayaraLogger rootLogger, final Level level, final List<String> requestedHandlers, final List<Handler> handlersToAdd) {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "configureRootLogger(rootLogger=" + rootLogger + ", level=" + level
            + ", requestedHandlers=" + requestedHandlers + ")");
        rootLogger.setLevel(level);
        final List<Handler> currentHandlers = Arrays.asList(rootLogger.getHandlers());
        if (requestedHandlers == null || requestedHandlers.isEmpty()) {
            PayaraLoggingTracer.error(PayaraLogManager.class, "No handlers set for the root logger!");
            return;
        }
        for (final Handler handler : currentHandlers) {
            if (requestedHandlers.stream().noneMatch(name -> name.equals(handler.getClass().getName()))) {
                // FIXME: does not respect handlerServices, will remove them, but they are in separate property and configured by different Action
                rootLogger.removeHandler(handler);
                handler.close();
            }
        }
        for (final Handler handler : handlersToAdd) {
            rootLogger.addHandler(handler);
        }
        // handlers which are already registered will not be removed and added again.
    }


    private Level getLevel(final String property, final Level defaultLevel) {
        final String levelProperty = getProperty(property);
        if (levelProperty == null || levelProperty.isEmpty()) {
            return defaultLevel;
        }
        try {
            return Level.parse(levelProperty);
        } catch (final IllegalArgumentException e) {
            PayaraLoggingTracer.error(PayaraLogManager.class,
                "Could not parse level " + levelProperty + ", returning " + defaultLevel + ".", e);
            return defaultLevel;
        }
    }


    @SuppressWarnings("unchecked") // always safe
    private static <T> T create(final String clazz) {
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "create(clazz=" + clazz + ")");
        try {
            return (T) Class.forName(clazz).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            PayaraLoggingTracer.error(PayaraLogManager.class, "Could not create " + clazz, e);
            return null;
        }
    }


    private static Properties provideProperties() {
        try {
            final Properties propertiesFromJvmOption = toProperties(System.getProperty(JVM_OPT_LOGGING_CFG_FILE));
            if (propertiesFromJvmOption != null) {
                return propertiesFromJvmOption;
            }
            final Properties propertiesFromClasspath = loadFromClasspath();
            if (propertiesFromClasspath != null) {
                return propertiesFromClasspath;
            }
            throw new IllegalStateException(
                "Could not find any logging.properties configuration file neither from JVM option ("
                    + JVM_OPT_LOGGING_CFG_FILE + ") nor from classpath.");
        } catch (final IOException e) {
            throw new IllegalStateException("Could not load logging configuration file.", e);
        }
    }


    private static Properties toProperties(final String absolutePath) throws IOException {
        if (absolutePath == null) {
            return null;
        }
        final File file = new File(absolutePath);
        return toProperties(file);
    }


    private static Properties toProperties(final File file) throws IOException {
        if (!file.canRead()) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) {
            return toProperties(input);
        }
    }


    private static Properties toProperties(final InputStream input) throws IOException {
        final Properties properties = new Properties();
        properties.load(input);
        return properties;
    }


    private static Properties loadFromClasspath() throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        PayaraLoggingTracer.trace(PayaraLogManager.class, () -> "loadFromClasspath(); classloader: " + classLoader);
        try (InputStream input = classLoader.getResourceAsStream("logging.properties")) {
            if (input == null) {
                return null;
            }
            return toProperties(input);
        }
    }


    /**
     * Action to be performed when client calls
     * {@link PayaraLogManager#reconfigure(PayaraLogManagerConfiguration, Action, Action)}
     */
    @FunctionalInterface
    public interface Action {

        /**
         * Custom action to be performed when executing the reconfiguration.
         */
        void run();


        /**
         * @return thread context classloader; can be overriden.
         */
        default ClassLoader getClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }
    }
}
