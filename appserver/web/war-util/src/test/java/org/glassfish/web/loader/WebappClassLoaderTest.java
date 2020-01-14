/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
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
package org.glassfish.web.loader;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.naming.resources.FileDirContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WebappClassLoaderTest {

    private static final int COUNT_OF_FILES = 100;
    private static final int THREAD_POOL_SIZE = 500;
    private static final int ITERATION_COUNT = 500; // count of executions from pool: ITERATION_COUNT * 3 + 1
    private static final int RESULT_TIMEOUT = 60_000;
    private static final int TERMINATION_TIMEOUT = 10_000;

    private static AtomicInteger executedUnfinshedThreads;
    private static ExecutorService executor;
    private static File junitJarFile;
    private ClassLoader classLoader;
    private WebappClassLoader webappClassLoader;

    @BeforeClass
    public static void setup() throws URISyntaxException {
        executedUnfinshedThreads = new AtomicInteger();
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Fetch any JAR to use for classloading
        junitJarFile = new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    @AfterClass
    public static void shutdown() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
            try {
                assertTrue(
                    "Could not terminate all threads - they are in deadlock OR are active but cannot be interrupted.",
                    executor.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS));
            } catch (AssertionError e) {
                // print stack traces of all active executor threads.
                Predicate<Map.Entry<Thread, ?>> filter = entry -> entry.getKey().getName().startsWith("pool-1");
                Consumer<Entry<Thread, StackTraceElement[]>> print = entry -> {
                    System.out.println(entry.getKey().getName() + ":");
                    Arrays.asList(entry.getValue()).forEach(v -> System.out.println("  " + v));
                    System.out.println();
                };
                Thread.getAllStackTraces().entrySet().stream().filter(filter).forEach(print);
                throw e;
            }
        }
    }


    @Before
    public void initClassLoader() {
        classLoader = this.getClass().getClassLoader();
        webappClassLoader = new WebappClassLoader(classLoader, null);
        webappClassLoader.start();
        webappClassLoader.setResources(new FileDirContext());
    }


    @After
    public void closeClassLoader() throws Exception {
        if (webappClassLoader != null) {
            webappClassLoader.close();
        }
        // this will add exception to current TimeoutException with more info.
        assertEquals("Some tasks not finished yet.", 0, executedUnfinshedThreads.get());
    }


    @Test
    public void check_findResourceInternalFromJars_thread_safety() throws Exception {

        // result value is not important, important is to not finish with exception
        CompletableFuture<Void> result = new CompletableFuture<>();
        Runnable lookupTask = waitAndDo(result, () -> lookup(classLoader, webappClassLoader));
        Runnable addTask = waitAndDo(result, () -> add(classLoader, webappClassLoader));
        Runnable closeTask = waitAndDo(result, () -> webappClassLoader.closeJARs(true));

        // Run the methods at the same time
        for (int i = 0; i < ITERATION_COUNT; i++) {
            execute(addTask);
            execute(lookupTask);
            execute(closeTask);
        }

        // watches count of executed tasks, which did not finish yet.
        Runnable watcher = () -> {
            while (executedUnfinshedThreads.get() > 0) {
                Thread.yield();
            }
            result.complete(null);
        };
        executor.execute(watcher);

        // wait max 10 seconds for completition by any way
        // exception in task will be rethrown here
        result.get(RESULT_TIMEOUT, TimeUnit.MILLISECONDS);
    }


    private void execute(Runnable task) {
        executor.execute(task);
        executedUnfinshedThreads.incrementAndGet();
    }

    private void add(ClassLoader realClassLoader, WebappClassLoader webappClassLoader) throws IOException {
        List<JarFile> jarFiles = findJarFiles(realClassLoader);

        for (JarFile j : jarFiles) {
            try {
                webappClassLoader.addJar(junitJarFile.getName(), j, junitJarFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void lookup(ClassLoader realClassLoader, WebappClassLoader webappClassLoader) throws Exception {
        for (JarFile jarFile : findJarFiles(realClassLoader)) {
            for (JarEntry entry : Collections.list(jarFile.entries())) {
                webappClassLoader.findResource(entry.getName());
                // System.out.println("Looked up " + resourceEntry);
                Thread.yield();
            }
        }
    }

    private List<JarFile> findJarFiles(ClassLoader realClassLoader) throws IOException {
        List<JarFile> jarFiles = new LinkedList<>();
        for (int i = 0; i < COUNT_OF_FILES; i++) {
            jarFiles.add(new JarFile(junitJarFile));
        }
        return jarFiles;
    }

    /**
     * Generate a task that will wait on the passed cyclic barrier before running
     * the passed task. Record the result in the passed future
     *
     * @param lock   the lock to wait on before execution
     * @param result where to store any encountered exceptions
     * @param task   the task to run
     * @return a new task
     */
    private static Runnable waitAndDo(final CompletableFuture<Void> result, final ExceptionalRunnable task) {
        return () -> {
            try {
                task.run();
                executedUnfinshedThreads.decrementAndGet();
            } catch (Exception ex) {
                result.completeExceptionally(ex);
            }
        };
    }

    /**
     * A runnable interface that allows exceptions
     */
    @FunctionalInterface
    private interface ExceptionalRunnable {
        void run() throws Exception;
    }
}