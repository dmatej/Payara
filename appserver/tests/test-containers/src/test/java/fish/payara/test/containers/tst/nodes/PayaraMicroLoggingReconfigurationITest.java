/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License"). You
 * may not use this file except in compliance with the License. You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license." If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above. However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package fish.payara.test.containers.tst.nodes;

import com.github.dockerjava.api.model.ContainerNetwork;

import fish.payara.test.containers.tools.container.DasCfg;
import fish.payara.test.containers.tools.container.PayaraServerContainer;
import fish.payara.test.containers.tools.container.TestablePayaraPort;
import fish.payara.test.containers.tools.junit.WaitForExecutable;
import fish.payara.test.containers.tools.log4j.EventCollectorAppender;
import fish.payara.test.containers.tools.properties.Properties;
import fish.payara.test.containers.tools.rs.RestClientCache;
import fish.payara.test.containers.tst.log.war.LoggingRestEndpoint;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.spi.LoggingEvent;
import org.hamcrest.core.StringContains;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author David Matejcek
 */
@Testcontainers
public class PayaraMicroLoggingReconfigurationITest {

    private static final Logger LOG = LoggerFactory.getLogger(PayaraMicroLoggingReconfigurationITest.class);
    private static final Logger LOG_DAS = LoggerFactory.getLogger("DAS");
    private static final Logger LOG_MICRO = LoggerFactory.getLogger("MIC");

    private static final String WEBAPP_NAME = PayaraMicroLoggingReconfigurationITest.class.getSimpleName() + "WebApp";
    private static final Properties TEST_CFG = new Properties("test.properties");
    private static final DasCfg CFG_DAS = new DasCfg("hostname-das", TEST_CFG.getString("docker.payara.tag"));
    private static final DasCfg CFG_MIC = new DasCfg("hostname-micro", TEST_CFG.getString("docker.payara.tag"));
    private static final Class<LoggingRestEndpoint> APP_CLASS = LoggingRestEndpoint.class;

    // FIXME: filtering messages
    private static final Set<String> MESSAGES_FOR_FILTER = Arrays
        .stream(new String[] {"STDERR:   akjaskjf" //
        }).collect(Collectors.toSet());

    private static final Network NET = Network.newNetwork();
    private static final RestClientCache RS_CLIENTS = new RestClientCache();

    private static File warFileOnHost;
    private static File warFileInMicro;

    @Container
    private final PayaraServerContainer das = //
        new PayaraServerContainer("payara/server-full:" + CFG_DAS.getVersion(), CFG_DAS) //
            .withNetwork(NET).withNetworkMode("bridge").withNetworkAliases(CFG_DAS.getHost()) //
            .withExposedPorts(TestablePayaraPort.getFullServerPortValues()) //
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostName(CFG_DAS.getHost());
            })
            .withLogConsumer(new Slf4jLogConsumer(LOG_DAS))
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30L)));

    @Container
    private final PayaraServerContainer micro = new PayaraServerContainer("payara/micro:" + CFG_DAS.getVersion(), CFG_MIC) //
        .withNetwork(NET).withNetworkMode("bridge").withNetworkAliases(CFG_MIC.getHost()) //
        .withExposedPorts(TestablePayaraPort.getMicroPortValues()).withLogConsumer(new Slf4jLogConsumer(LOG_MICRO))
        .withFileSystemBind(warFileOnHost.getAbsolutePath(), warFileInMicro.getAbsolutePath())
        .withEnv("PAYARA_ARGS", "--clustermode domain:" + CFG_DAS.getHost() + ":4900"
            + " --hzPublicAddress " + CFG_MIC.getHost()
            + " --name MicroTest --deploy " + warFileInMicro.getAbsolutePath() + " --contextRoot /logging")
        .waitingFor(Wait.forLogMessage(".*Payara Micro.+ ready.+\\n", 1).withStartupTimeout(Duration.ofSeconds(30L)));

    private EventCollectorAppender microLog;

    @BeforeAll
    public static void createApplication() {
        final WebArchive war = ShrinkWrap.create(WebArchive.class) //
            .addPackages(true, APP_CLASS.getPackage()) //
            .addAsWebResource(EmptyAsset.INSTANCE, "WEB-INF/beans.xml")
        ;
        LOG.info(war.toString(true));
        warFileOnHost = new File(TEST_CFG.getFile("build.directory"), WEBAPP_NAME + ".war");
        war.as(ZipExporter.class).exportTo(warFileOnHost, true);
        warFileInMicro = new File("/opt/payara/", warFileOnHost.getName());
    }


    @BeforeEach
    public void addLogCollector() {
        final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger("MIC");
        assertNotNull(logger, "MIC logger was not found");
        final Predicate<LoggingEvent> filter = event -> {
            final Predicate<? super String> predicate = msgPart -> {
                final String message = event.getMessage().toString();
                return message.contains(msgPart);
            };
            return MESSAGES_FOR_FILTER.stream().anyMatch(predicate);
        };
        microLog = new EventCollectorAppender().withCapacity(20).withEventFilter(filter);
        logger.addAppender(microLog);
    }


    @AfterEach
    public void resetLogCollector() {
        assertThat("log collector size", microLog.getSize(), equalTo(0));
        microLog.clearCache();
    }


    @AfterAll
    public static void close() {
        RS_CLIENTS.close();
    }


    @Test
    public void testSetLogLevels() throws Throwable {
        WaitForExecutable.waitFor(
            () -> assertThat(das.asAdmin("list-hazelcast-cluster-members"), StringContains.containsString(WEBAPP_NAME)),
            15000L);
        final Client client = RS_CLIENTS.getNonPreemptiveClient(true, null, null);
        final WebTarget target = client.target(micro.getHttpUrl().toURI()).path("logging").path("sample");
        final Response response = target.request().post(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        // TODO: check response and log

        final Collection<ContainerNetwork> microIpAddresses = micro.getContainerInfo().getNetworkSettings()
            .getNetworks().values();
        LOG.info("Micro network addresses: {}",
            microIpAddresses.stream().map(ContainerNetwork::getIpAddress).collect(Collectors.toList()));
        final String microIpAddress = microIpAddresses.iterator().next().getIpAddress();
        das.asAdmin("send-asadmin-command", "--explicitTarget=" + microIpAddress + ":6900:MicroTest", //
            "--logOutput=true",
            "--command=set-log-levels", LoggingRestEndpoint.class.getName() + "=FINE");
        // TODO assert

        // TODO replace with waiting for log
        Thread.sleep(300L);

        final Response response2 = target.request().post(Entity.entity("", MediaType.TEXT_PLAIN_TYPE));
        // TODO: check response2 and log

        Thread.sleep(100L);

        String response3 = das.asAdmin("send-asadmin-command", "--explicitTarget=" + microIpAddress + ":6900:MicroTest",
            "--command=set-log-attributes", "handlers=java.util.logging.ConsoleHandler"
                + ":java.util.logging.ConsoleHandler.formatter=com.sun.enterprise.server.logging.ODLLogFormatter"
                + ":com.sun.enterprise.server.logging.ODLLogFormatter.ansiColor=true");
        Thread.sleep(100L);
    }
}
