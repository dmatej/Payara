/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *    Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
 *
 *     The contents of this file are subject to the terms of either the GNU
 *     General Public License Version 2 only ("GPL") or the Common Development
 *     and Distribution License("CDDL") (collectively, the "License").  You
 *     may not use this file except in compliance with the License.  You can
 *     obtain a copy of the License at
 *     https://github.com/payara/Payara/blob/master/LICENSE.txt
 *     See the License for the specific
 *     language governing permissions and limitations under the License.
 *
 *     When distributing the software, include this License Header Notice in each
 *     file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *     GPL Classpath Exception:
 *     The Payara Foundation designates this particular file as subject to the "Classpath"
 *     exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *     file that accompanied this code.
 *
 *     Modifications:
 *     If applicable, add the following below the License Header, with the fields
 *     enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyright [year] [name of copyright owner]"
 *
 *     Contributor(s):
 *     If you wish your version of this file to be governed by only the CDDL or
 *     only the GPL Version 2, indicate your decision by adding "[Contributor]
 *     elects to include this software in this distribution under the [CDDL or GPL
 *     Version 2] license."  If you don't indicate a single choice of license, a
 *     recipient has the option to distribute your version of this file under
 *     either the CDDL, the GPL Version 2 or to extend the choice of license to
 *     its licensees as provided above.  However, if you add GPL Version 2 code
 *     and therefore, elected the GPL Version 2 license, then the option applies
 *     only if the new code is made subject to such option by the copyright
 *     holder.
 */
package fish.payara.test.containers.tools.env;

import fish.payara.test.containers.tools.container.MySQLContainerConfiguration;
import fish.payara.test.containers.tools.container.NetworkTarget;
import fish.payara.test.containers.tools.container.PayaraServerContainerConfiguration;
import fish.payara.test.containers.tools.properties.Properties;

import java.util.Objects;

/**
 * Parses {@link DockerEnvironmentConfiguration} from standardized properties.
 *
 * @author David Matějček
 */
public final class DockerEnvironmentConfigurationParser {

    private DockerEnvironmentConfigurationParser() {
        // hidden
    }


    /**
     * Parses the properties.
     *
     * @param properties
     * @return {@link DockerEnvironmentConfiguration}, never null
     */
    public static DockerEnvironmentConfiguration parse(final Properties properties) {
        Objects.requireNonNull(properties, "properties");
        final DockerEnvironmentConfiguration cfg = new DockerEnvironmentConfiguration();
        cfg.setPayaraServerConfiguration(parseServerConfiguration(properties));
        cfg.setForceNewPayaraServer(properties.getBoolean("docker.payara.image.forceNew", false));
        cfg.setForceNewMySQLServer(properties.getBoolean("docker.mysql.image.forceNew", false));
        cfg.setUseMySqlContainer(properties.getBoolean("docker.mysql.useDocker", false));
        cfg.setMySQLServerConfiguration(parseMySqlContainerConfiguration(properties));
        return cfg;
    }


    private static PayaraServerContainerConfiguration parseServerConfiguration(final Properties properties) {
        final PayaraServerContainerConfiguration cfg = new PayaraServerContainerConfiguration();
        cfg.setDownloadedDockerImageName(properties.getString("docker.payara.image.base"));
        cfg.setJdkPackageId(properties.getString("docker.payara.jdkPackageId"));
        cfg.setPreparationTimeout(properties.getLong("docker.images.timeoutInSeconds", 60));

        cfg.setMainApplicationDirectory(properties.getFile("docker.payara.sharedDirectory"));
        cfg.setTestOutputDirectory(properties.getFile("build.testOutputDirectory"));
        cfg.setPomFile(properties.getFile("build.pomFile"));

        cfg.setHost(properties.getString("docker.payara.host"));
        cfg.setPayaraDirectoryName(properties.getString("docker.payara.payaraDirectoryName", "payara"));
        cfg.setPayaraDomainName(properties.getString("docker.payara.domainName", "domain1"));
        cfg.setNewLoggingImplementation(properties.getBoolean("docker.payara.cfg.logging.newImplementation", true));

        cfg.setSystemMemory(properties.getInt("docker.payara.memory.totalInGB", 1));
        cfg.setXms(properties.getString("docker.payara.jvm.xms"));
        cfg.setXmx(properties.getString("docker.payara.jvm.xmx"));
        cfg.setXss(properties.getString("docker.payara.jvm.xss"));

        cfg.setJaCoCoReportDirectory(properties.getFile("jacoco.reportDirectory"));
        cfg.setJaCoCoVersion(properties.getString("jacoco.version"));

        final String dockerHost = properties.getString("docker.host", "localhost");
        final int dockerPort = properties.getInt("docker.port", 2375);
        cfg.setDockerHostAndPort(new NetworkTarget(dockerHost, dockerPort));

        return cfg;
    }


    /**
     * Parses the configuration from the the docker.mysql.* properties.
     *
     * @param properties
     * @return new {@link MySQLContainerConfiguration}
     */
    private static MySQLContainerConfiguration parseMySqlContainerConfiguration(final Properties properties) {
        final MySQLContainerConfiguration cfg = new MySQLContainerConfiguration();
        cfg.setDbPassword(properties.getString("docker.mysql.password"));
        cfg.setDbUser(properties.getString("docker.mysql.user"));
        cfg.setDownloadedDockerImageName(properties.getString("docker.mysql.image.base"));
        cfg.setHostName(properties.getString("docker.mysql.host"));
        cfg.setPort(properties.getInt("docker.mysql.port", 0));
        cfg.setSystemMemory(properties.getInt("docker.mysql.memory.totalInGB", 0));
        cfg.setWorkingDirectory(properties.getFile("docker.mysql.workingDirectory"));
        return cfg;
    }
}
