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
package fish.payara.test.containers.tools.container;

import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.model.ContainerNetwork;

import fish.payara.test.containers.tools.rs.RestClientCache;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.FixedHostPortGenericContainer;

/**
 * Payara server started as docker container.
 *
 * @author David Matějček
 */
public class PayaraServerContainer extends FixedHostPortGenericContainer<PayaraServerContainer> {

    private static final Logger LOG = LoggerFactory.getLogger(PayaraServerContainer.class);
    private final PayaraServerContainerConfiguration configuration;
    private final RestClientCache clientCache;


    /**
     * Creates an instance.
     *
     * @param nameOfBasicImage - name of the docker image to use
     * @param configuration
     */
    public PayaraServerContainer(final String nameOfBasicImage,
        final PayaraServerContainerConfiguration configuration) {
        super(nameOfBasicImage);
        this.configuration = configuration;
        this.clientCache = new RestClientCache();
    }


    // FIXME delete after making paths public
    public PayaraServerContainerConfiguration getConfiguration() {
        return this.configuration;
    }


    /**
     * @return {@link URL}, where tests can access the domain admin port.
     */
    public URL getAdminUrl() {
        return getExternalUrl("https", TestablePayaraPort.DAS_ADMIN_PORT);
    }


    /**
     * @return {@link URL}, where tests can access the appllication HTTP port.
     */
    public URL getHttpUrl() {
        return getExternalUrl("http", TestablePayaraPort.DAS_HTTP_PORT);
    }


    /**
     * @return {@link URL}, where tests can access the application HTTPS port.
     */
    public URL getHttpsUrl() {
        return getExternalUrl("https", TestablePayaraPort.DAS_HTTPS_PORT);
    }


    private URL getExternalUrl(final String protocol, final TestablePayaraPort internalPort) {
        try {
            return new URL(protocol, getContainerIpAddress(), getMappedPort(internalPort.getPort()), "/");
        } catch (final MalformedURLException e) {
            throw new IllegalStateException(
                "Could not create external url for protocol '" + protocol + "' and port " + internalPort, e);
        }
    }


    @Override
    public void close() {
        try {
            if (Stream.of(asAdmin("list-domains").split("\n")).filter(
                line -> line.contains(this.configuration.getPayaraDomainName()) && line.contains("not running"))
                .findFirst().isPresent()) {
                asLocalAdmin("start-domain", this.configuration.getPayaraDomainName());
            }
        } catch (final Exception e) {
            throw new IllegalStateException(
                "Could not ensure the domain is running to stop all instances managed by this domain.", e);
        }
        try {
            stopAllInstances();
        } catch (final Exception e) {
            LOG.error("Could not stop all instances managed by this domain.", e);
        }
        this.clientCache.close();
        try {
            asLocalAdmin("stop-domain", this.configuration.getPayaraDomainName());
        } catch (final Exception e) {
            LOG.error("Could not shutdown the server nicely.", e);
        }
    }


    private void stopAllInstances() throws AsadminCommandException {
        final List<String> instances = Stream.of(asAdmin("list-instances").split("\n")).map(String::trim)
            .filter(line -> !line.contains("Nothing to list.")).map(s -> s.split(" ")[0]).collect(Collectors.toList());
        for (final String instance : instances) {
            try {
                asAdmin("stop-instance", instance);
            } catch (final AsadminCommandException e) {
                LOG.warn("Could not stop instance " + instance, e);
            }
            try {
                asAdmin("delete-instance", instance);
            } catch (final AsadminCommandException e) {
                if (e.getMessage().contains("There is no instance named " + instance + " in this domain")) {
                    LOG.warn("Instance " + instance + " was already deleted");
                } else {
                    LOG.error("Could not delete instance " + instance, e);
                }
            }
        }
    }


    /**
     * @param fileInContainer
     */
    public void reconfigureLogging(final File fileInContainer) {
        // TODO: to be done later.
    }


    /**
     * @return IP address usable in container network
     */
    public String getVirtualNetworkIpAddress() {
        final Collection<ContainerNetwork> networks = getContainerInfo().getNetworkSettings().getNetworks().values();
        LOG.trace("networks: {}", networks);
        return networks.stream().filter(n -> n.getNetworkID().equals(getNetwork().getId())).findAny()
            .map(ContainerNetwork::getIpAddress).orElse("127.0.0.1");
    }


    /**
     * @return instance resolving Payara directory structure <b>inside</b> the docker container.
     */
    public PayaraServerFiles getPayaraFileStructureInDocker() {
        return new PayaraServerFiles(configuration.getPayaraMainDirectoryInDocker(),
            configuration.getPayaraDomainName());
    }


    /**
     * @return instance resolving Payara directory structure <b>outside</b> the docker container.
     */
    public PayaraServerFiles getPayaraFileStructure() {
        return new PayaraServerFiles(configuration.getPayaraMainDirectory(),
            configuration.getPayaraDomainName());
    }


    /**
     * Executes asadmin without need to access to a running domain.
     *
     * @param command - command name
     * @param arguments - arguments. Can be null.
     * @return standard output
     * @throws AsadminCommandException- if the command exit code was not zero; message contains
     *             error output
     */
    public String asLocalAdmin(final String command, final String... arguments) throws AsadminCommandException {
        // FIXME: --echo breaks change-admin-password
        final String[] defaultArgs = new String[] {"--terse"};
        final AsadminCommandExecutor executor = new AsadminCommandExecutor(this, defaultArgs);
        return executor.exec(command, arguments).trim();
    }


    /**
     * Executes asadmin command against running domain instance.
     *
     * @param command - command name
     * @param arguments - arguments. Can be null and should not contain parameters used before
     *            the command.
     * @return standard ouptut
     * @throws AsadminCommandException- if the command exit code was not zero; message contains
     *             error output
     */
    public String asAdmin(final String command, final String... arguments) throws AsadminCommandException {
        final List<String> defaultArgs = new ArrayList<>(Arrays.asList("--terse", "--user", "admin"));
        if (this.configuration.getPasswordFileInDocker() != null) {
            defaultArgs.add("--passwordfile");
            defaultArgs.add(this.configuration.getPasswordFileInDocker().getAbsolutePath());
        }
        final AsadminCommandExecutor executor = new AsadminCommandExecutor(this,
            defaultArgs.toArray(new String[defaultArgs.size()]));
        return executor.exec(command, arguments).trim();
    }


    /**
     * @return absolute path to asadmin command file in the container.
     */
    public File getAsadmin() {
        return getPayaraFileStructureInDocker().getAsadmin();
    }


    /**
     * Calls hosts docker with the HTTP POST. It's port must be exposed into container with the
     * {@link Testcontainers#exposeHostPorts(int...)} before the container is created.
     *
     * @param path
     * @param json
     * @return response including headers
     */
    public String docker(final String path, final String json) {
        return docker("POST", path, json);
    }


    /**
     * Calls hosts docker. It's port must be exposed into container with the
     * {@link Testcontainers#exposeHostPorts(int...)} before the container is created.
     *
     * @param method
     * @param path
     * @param json
     * @return response including headers
     */
    public String docker(final String method, final String path, final String json) {
        LOG.debug("docker(method={}, path={}, json=\n{}\n)", method, path, json);
        try {
            final File outputFileInDocker = new File(this.configuration.getMainApplicationDirectoryInDocker(),
                UUID.randomUUID() + ".json");
            final NetworkTarget docker = this.configuration.getDockerHostAndPort();
            final ExecResult result = execInContainer( //
                "curl", "-sS", //
                "-X", method, //
                "-H", "Accept: application/json", "-H", "Content-Type: application/json", //
                "-i", "http://" + docker.getHost() + ":" + docker.getPort() + "/" + path, //
                "-o", outputFileInDocker.getAbsolutePath(),
                "--data", json);
            LOG.debug("path={}, exitCode={},\nstdout:\n{}\nstderr:\n{}", path, result.getExitCode(), result.getStdout(),
                result.getStderr());
            if (result.getExitCode() != 0) {
                throw new DockerClientException(result.getStderr());
            }
            // the line buffer+terminal are limited and they put "random" new line characters
            // to the output, so this is a workaround
            // bonus: you can read these files after the test (order by date)
            final File outputFile = new File(this.configuration.getMainApplicationDirectory(),
                outputFileInDocker.getName());
            final String output = FileUtils.readFileToString(outputFile, StandardCharsets.UTF_8);
            LOG.trace("HTTP output: \n{}", output);
            return output;
        } catch (final UnsupportedOperationException | IOException | InterruptedException e) {
            throw new IllegalStateException("Could not execute docker command via curl.", e);
        }
    }
}
