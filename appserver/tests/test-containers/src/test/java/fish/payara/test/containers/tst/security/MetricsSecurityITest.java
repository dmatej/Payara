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

import fish.payara.test.containers.tools.container.PayaraServerContainer;
import fish.payara.test.containers.tools.env.DockerEnvironment;
import fish.payara.test.containers.tools.env.TestConfiguration;
import fish.payara.test.containers.tools.junit.DockerITestExtension;
import fish.payara.test.containers.tools.rs.RestClientCache;
import java.io.File;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import static fish.payara.test.containers.tools.container.TestablePayaraPort.DAS_HTTP_PORT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author David Matejcek
 * <pre>
 * ./runme-local.sh -Ptest-containers -pl :test-containers -Ddocker.payara.version=4.1.2.191.13-SNAPSHOT -Ddocker.payara.tag=4.1.2.191.13 -Ppayara4 -Dit.test=JaspicSamAuthenticationITest
 * </pre>
 */
// PAYARA-3515
@ExtendWith(DockerITestExtension.class)
public class MetricsSecurityITest {

    private static final Logger LOG = LoggerFactory.getLogger(MetricsSecurityITest.class);

    private static final TestConfiguration TEST_CFG = TestConfiguration.getInstance();
    private static final RestClientCache RS_CLIENTS = new RestClientCache();

    private static PayaraServerContainer payara;

    @BeforeAll
    public static void createArtifacts() throws Exception {
        payara = DockerEnvironment.getInstance().getPayaraContainer();
        // FIXME backup
    }


    @BeforeEach
    public void initDomain() throws Exception {
    }


    @AfterEach
    public void resetChanges() throws Exception {
        RS_CLIENTS.close();
    }


    @AfterAll
    public static void close() throws Exception {
        // FIXME restore and start
        payara.asLocalAdmin("restart-domain", TEST_CFG.getPayaraDomainName());
    }


    @Test
    public void testProtectedMetrics() throws Throwable {
        final File destination = new File(payara.getPayaraFileStructureInDocker().getDomainConfigDirectory(),
            "default-web.xml");
        payara.copyFileToContainer(MountableFile.forClasspathResource("realm/" + destination.getName()),
            destination.getAbsolutePath());
        payara.asLocalAdmin("restart-domain", TEST_CFG.getPayaraDomainName());

        payara.asAdmin("create-virtual-server", "--property", "authRealm=admin-realm", "--hosts", "localhost",
            "--networklisteners", "http-listener-1", "my-server");
        payara.asAdmin("set", "configs.config.server-config.microprofile-metrics-configuration.enabled=true");

        payara.asLocalAdmin("restart-domain", TEST_CFG.getPayaraDomainName());

        final ExecResult result1 = payara.execInContainer("curl", "-s", "-G", "http://localhost:" + DAS_HTTP_PORT + "/");
        assertAll(
            () -> assertThat("result1.exitCode", result1.getExitCode(), equalTo(0)),
            () -> assertThat("result1.stdOut", result1.getStdout(), containsString("The document root folder"
                + " for this server is the docroot subdirectory of this server's domain directory.")) //
        );

        assertAll(this::checkAnonymousRequest, this::checkAuthorizedRequest);
    }


    // FIXME: Must end with HTTP 401, but passes and causes Internal Server Error
    private void checkAnonymousRequest() throws Exception {
        final ExecResult result = payara.execInContainer("curl", "-s", "-G", //
//                "-u", TEST_CFG.getPayaraUsername() + ":" + TEST_CFG.getPayaraPassword(),
                "http://localhost:" + DAS_HTTP_PORT + "/metrics/base");
        assertAll(
            () -> assertThat("result.exitCode", result.getExitCode(), equalTo(0)), //
            () -> assertThat("result.stdOut", result.getStdout(), containsString("HTTP Status 401 - Unauthorized")) //
        );
    }


    // FIXME: Internal Server Error - cannot parse content type header; still unsecured.
    private void checkAuthorizedRequest() throws Exception {
        final WebTarget target = RS_CLIENTS
//            .getAnonymousClient()
            .getNonPreemptiveClient(true, TEST_CFG.getPayaraUsername(), TEST_CFG.getPayaraPassword())
            .target(payara.getHttpUrl().toURI()).path("metrics").path("base");
        try (Response response = target.request().get()) {
            assertEquals(Status.UNAUTHORIZED, response.getStatusInfo().toEnum(), "response.status");
            assertTrue(response.hasEntity(), "response.hasEntity");
            final String stringEntity = response.readEntity(String.class);
            assertThat("response.text", stringEntity, containsString("HTTP Status 200"));
        }
    }
}
