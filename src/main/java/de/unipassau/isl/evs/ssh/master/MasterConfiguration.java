package de.unipassau.isl.evs.ssh.master;

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.Container;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import java.util.NoSuchElementException;

/**
 * Created by popeye on 6/19/16.
 */
public class MasterConfiguration extends AbstractComponent {
    public static final Key<MasterConfiguration> KEY = new Key<>(MasterConfiguration.class);

    private HierarchicalINIConfiguration config;

    @Override
    public void init(Container container) {
        try {
            config = new HierarchicalINIConfiguration("/etc/securesmarthome.conf");
        } catch (ConfigurationException ignored) {

        }
        super.init(container);
    }

    public int getLocalPort() {
        int port = -1;
        try {
            if (config != null) {
                port = config.getSection("Connection").getInt("LocalPort");
            }
        } catch (NoSuchElementException ignored) {
        }

        if (port < 1) {
            port = CoreConstants.NettyConstants.DEFAULT_LOCAL_PORT;
        }

        return port;
    }

    public int getPublicPort() {
        int port = -1;
        try {
            if (config != null) {
                port = config.getSection("Connection").getInt("PublicPort");
            }
        } catch (NoSuchElementException ignored) {
        }

        if (port < 1) {
            port = CoreConstants.NettyConstants.DEFAULT_PUBLIC_PORT;
        }

        return port;
    }
}
