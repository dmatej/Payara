/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *  Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.test.containers.tst.security;

import fish.payara.test.containers.tools.container.AsadminCommandExecutor;
import fish.payara.test.containers.tools.container.PayaraServerContainer;
import fish.payara.test.containers.tools.container.PayaraServerFiles;
import fish.payara.test.containers.tools.env.DockerEnvironment;
import fish.payara.test.containers.tools.env.TestConfiguration;
import fish.payara.test.containers.tools.junit.DockerITestExtension;
import fish.payara.test.containers.tools.junit.WaitForExecutable;
import fish.payara.test.containers.tools.rs.RestClientCache;
import fish.payara.test.containers.tst.security.jar.jaspic.CustomSAM;
import fish.payara.test.containers.tst.security.war.jaspic.servlet.PublicServlet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;

import static fish.payara.test.containers.tools.container.TestablePayaraPort.DAS_ADMIN_PORT;
import static fish.payara.test.containers.tools.container.TestablePayaraPort.DAS_HTTPS_PORT;
import static fish.payara.test.containers.tools.container.TestablePayaraPort.DAS_HTTP_PORT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author David Matejcek
 * <pre>
 * ./runme-local.sh -Ptest-containers -pl :test-containers -Ddocker.payara.version=4.1.2.191.13-SNAPSHOT -Ddocker.payara.tag=4.1.2.191.13 -Ppayara4 -Dit.test=JaspicVsMetricsITest
 * </pre>
 */
@ExtendWith(DockerITestExtension.class)
public class JaspicVsMetricsITest {

    private static final String WAR_ROOT_CTX = "/jaspic-lifecycle";
    private static final Logger LOG = LoggerFactory.getLogger(JaspicVsMetricsITest.class);

    private static final String USER_NAME = "test";
    // must be same as in passwordfile-user.txt
    private static final String USER_PASSWORD = "admin123";
    private static final TestConfiguration TEST_CFG = TestConfiguration.getInstance();
    private static final Class<CustomSAM> CLASS_AUTHMODULE = CustomSAM.class;
    private static final Class<PublicServlet> CLASS_WAR = PublicServlet.class;

    private static final RestClientCache RS_CLIENTS = new RestClientCache();

    private static File jarFileOnHost;
    private static File jarFileOnServer;
    private static File warFileOnHost;
    private static File warFileOnServer;

    private static PayaraServerContainer payara;

    @BeforeAll
    public static void createArtifacts() {
        payara = DockerEnvironment.getInstance().getPayaraContainer();

        final JavaArchive jar = ShrinkWrap.create(JavaArchive.class) //
            .addPackages(true, CLASS_AUTHMODULE.getPackage()) //
        ;
        LOG.info(jar.toString(true));
        jarFileOnHost = new File(TEST_CFG.getBuildDirectory(), JaspicVsMetricsITest.class.getSimpleName() + "-sam.jar");
        jar.as(ZipExporter.class).exportTo(jarFileOnHost, true);
        final PayaraServerFiles payaraFilesInDocker = payara.getPayaraFileStructureInDocker();
        jarFileOnServer = new File(payaraFilesInDocker.getDomainLibDirectory(), jarFileOnHost.getName());

        final File webInfDir = TEST_CFG.getClassDirectory().toPath()
            .resolve(Paths.get("security", "war", "jaspic", "servlet", "WEB-INF")).toFile();
        final WebArchive war = ShrinkWrap.create(WebArchive.class) //
            .addPackages(true, CLASS_WAR.getPackage()) //
            .addAsWebInfResource(new File(webInfDir, "web.xml"))
            .addAsWebInfResource(new File(webInfDir, "payara-web.xml"))
        ;
        LOG.info(war.toString(true));
        warFileOnHost = new File(TEST_CFG.getBuildDirectory(), JaspicVsMetricsITest.class.getSimpleName() + ".war");
        war.as(ZipExporter.class).exportTo(warFileOnHost, true);
        warFileOnServer = new File("/", warFileOnHost.getName());
    }


    @AfterEach
    public void resetChanges() {
    }


    @AfterAll
    public static void close() {
        RS_CLIENTS.close();
        // this test is destructive.
        // FIXME: another possibility: restore from backup.
        final DockerEnvironment environment = DockerEnvironment.getInstance();
        if (environment != null) {
            environment.close();
        }
    }


    @AfterAll
    public static void cleanupAfterTest() throws Exception {
        RS_CLIENTS.close();
        payara.asLocalAdmin("stop-domain", TEST_CFG.getPayaraDomainName());
        payara.asLocalAdmin("restore-domain", TEST_CFG.getPayaraDomainName());
        payara.asLocalAdmin("start-domain", TEST_CFG.getPayaraDomainName());
    }


    // FIXME: assertions for all commands!
    @Test
    public void testProtectedMetrics() throws Throwable {
        payara.asAdmin("create-virtual-server", "--property", "authRealm=admin-realm", "--hosts", "localhost",
            "--networklisteners", "http-listener-1", "my-server");
        payara.asAdmin("set", "configs.config.server-config.microprofile-metrics-configuration.enabled=true");

        payara.asLocalAdmin("stop-domain", TEST_CFG.getPayaraDomainName());
        final File destination = new File(payara.getPayaraFileStructureInDocker().getDomainConfigDirectory(),
            "default-web.xml");
        payara.copyFileToContainer(MountableFile.forClasspathResource("realm/" + destination.getName()),
            destination.getAbsolutePath());

        payara.asLocalAdmin("start-domain", TEST_CFG.getPayaraDomainName());

        final ExecResult result1 = payara.execInContainer("curl", "http://localhost:" + DAS_HTTP_PORT + "/");
        assertAll(
            () -> assertThat("result1.exitCode", result1.getExitCode(), equalTo(0)),
            () -> assertThat("result1.stdOut", result1.getStdout(), containsString("The document root folder"
                + " for this server is the docroot subdirectory of this server's domain directory.")) //
        );

        final ExecResult result = payara.execInContainer("curl",
            "http://localhost:" + DAS_HTTP_PORT + "/metrics");
        assertAll(
            () -> assertThat("result.exitCode", result.getExitCode(), equalTo(0)), //
            () -> assertThat("result.stdOut", result.getStdout(), containsString("HTTP Status 401 - Unauthorized")) //
        );

        final WebTarget target = RS_CLIENTS.getAnonymousClient().target(payara.getHttpUrl().toURI()).path("metrics");
        try (Response response = target.request().get()) {
            assertEquals(Status.UNAUTHORIZED, response.getStatusInfo().toEnum(), "response.status");
            assertTrue(response.hasEntity(), "response.hasEntity");
            final String stringEntity = response.readEntity(String.class);
            assertThat("response.text", stringEntity, containsString("HTTP Status 401 - Unauthorized"));
        }
    }


    private String get() throws IOException {
        final Integer httpPortCluster = payara.getMappedPort(28080);
        final URL url = new URL("http", "localhost", httpPortCluster, WAR_ROOT_CTX + "/protected/servlet");
        try {
            final Object response = url.getContent();
            LOG.info("response: {}", response);
            return response.toString();
        } catch (FileNotFoundException e) {
            // HTTP 404
            LOG.debug("OK, FNFE aka HTTP 404 received.", e);
            return null;
        }
    }
}
