/*

 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

 Copyright (c) 1014 C2B2 Consulting Limited. All rights reserved.

 The contents of this file are subject to the terms of the Common Development
 and Distribution License("CDDL") (collectively, the "License").  You
 may not use this file except in compliance with the License.  You can
 obtain a copy of the License at
 https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 or packager/legal/LICENSE.txt.  See the License for the specific
 language governing permissions and limitations under the License.

 When distributing the software, include this License Header Notice in each
 file and include the License file at packager/legal/LICENSE.txt.
 */
package fish.payara.nucleus.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.ConfigLoader;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.glassfish.api.StartupRunLevel;
import org.glassfish.api.admin.ServerEnvironment;
import org.glassfish.api.event.EventListener;
import org.glassfish.api.event.EventTypes;
import org.glassfish.api.event.Events;
import org.glassfish.hk2.runlevel.RunLevel;
import org.glassfish.internal.api.ServerContext;
import org.jvnet.hk2.annotations.Service;

/**
 *
 * @author steve
 */
@Service(name = "hazelcast-core")
@RunLevel(StartupRunLevel.VAL)
public class HazelcastCore implements EventListener {
    
    public final static String INSTANCE_ATTRIBUTE="GLASSFISH-INSTANCE";

    private HazelcastInstance theInstance;

    @Inject
    Events events;

    @Inject
    ServerContext context;

    @Inject
    @Named(ServerEnvironment.DEFAULT_INSTANCE_NAME)
    HazelcastRuntimeConfiguration configuration;

    private boolean enabled;

    @PostConstruct
    public void postConstruct() {
        enabled = configuration.getEnabled();
        events.register(this);
        if ((configuration.getEnabled())) {
            bootstrapHazelcast();
        }
    }

    public HazelcastInstance getInstance() {
        return theInstance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void event(Event event) {
        if (event.is(EventTypes.SERVER_SHUTDOWN)) {
            shutdownHazelcast();
        } else if (event.is(EventTypes.SERVER_READY)) {
            if (enabled) {
                bindToJNDI();
            }

        }
    }

    private Config buildConfiguration() {
        Config config = new Config();

        String hazelcastFilePath = "";
        URL serverConfigURL;
        try {
            serverConfigURL = new URL(context.getServerConfigURL());
            File serverConfigFile = new File(serverConfigURL.getPath());
            hazelcastFilePath = serverConfigFile.getParentFile().getAbsolutePath() + File.separator + configuration.getHazelcastConfigurationFile();
            File file = new File(hazelcastFilePath);
            if (file.exists()) {
                config = ConfigLoader.load(hazelcastFilePath);
                if (config == null) {
                    Logger.getLogger(HazelcastCore.class.getName()).log(Level.WARNING, "Hazelcast Core could not find configuration file " + hazelcastFilePath + " using default configuration");
                    config = new Config();
                }
            } else {
                MulticastConfig mcConfig = config.getNetworkConfig().getJoin().getMulticastConfig();
                mcConfig.setEnabled(true);
                mcConfig.setMulticastGroup(configuration.getMulticastGroup());
                mcConfig.setMulticastPort(Integer.parseInt(configuration.getMulticastPort()));
                config.getNetworkConfig().setPortAutoIncrement(true);
                config.getNetworkConfig().setPort(Integer.parseInt(configuration.getStartPort()));
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.WARNING, "Unable to parse server config URL", ex);
        } catch (IOException ex) {
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.WARNING, "Hazelcast Core could not load configuration file " + hazelcastFilePath + " using default configuration", ex);
        }

        String instanceName = context.getDefaultDomainName() + "." + context.getInstanceName();
        config.setInstanceName(instanceName);
        return config;
    }

    public void setEnabled(Boolean enabled) {
        if (this.enabled && enabled || !this.enabled && !enabled) {
            // do nothing
        } else if (this.enabled && !enabled) {
            this.enabled = false;
            shutdownHazelcast();
        } else if (!this.enabled && enabled) {
            this.enabled = true;
            bootstrapHazelcast();
            bindToJNDI();
        }
    }

    private void shutdownHazelcast() {
        if (theInstance != null) {
            unbindFromJNDI();
            theInstance.shutdown();
            theInstance = null;
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.INFO, "Shutdown Hazelcast");
        }
    }

    private void bootstrapHazelcast() {
        Config config = buildConfiguration();
        // hack to prevent Hazelcast barfing on multiple classloaders for portable hooks during boot
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Hazelcast.class.getClassLoader());
        theInstance = Hazelcast.newHazelcastInstance(config);
        theInstance.getCluster().getLocalMember().setStringAttribute(INSTANCE_ATTRIBUTE, context.getInstanceName());
        Thread.currentThread().setContextClassLoader(tccl);
    }

    private void bindToJNDI() {
        try {
            InitialContext ctx;
            ctx = new InitialContext();
            ctx.bind(configuration.getJNDIName(), theInstance);
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.INFO, "Hazelcast Instance Bound to JNDI at " + configuration.getJNDIName());
        } catch (NamingException ex) {
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void unbindFromJNDI() {
        try {
            InitialContext ctx;
            ctx = new InitialContext();
            ctx.unbind(configuration.getJNDIName());
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.INFO, "Hazelcast Instance Unbound from JNDI at " + configuration.getJNDIName());
        } catch (NamingException ex) {
            Logger.getLogger(HazelcastCore.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }

}
