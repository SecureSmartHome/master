package de.unipassau.isl.evs.ssh.master;

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConfiguration;
import de.unipassau.isl.evs.ssh.core.CoreConstants;

import java.util.NoSuchElementException;

/**
 * Created by popeye on 6/19/16.
 */
public class MasterConfiguration extends CoreConfiguration {
    public static final Key<MasterConfiguration> KEY = new Key<>(MasterConfiguration.class);


    public int getLocalPort() {
        int port = -1;
        try {
            port = config.getSection("Connection").getInt("LocalPort");
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
            port = config.getSection("Connection").getInt("PublicPort");
        } catch (NoSuchElementException ignored) {
        }

        if (port < 1) {
            port = CoreConstants.NettyConstants.DEFAULT_PUBLIC_PORT;
        }

        return port;
    }

    @Override
    protected ConfigurationDefaults loadDefaults() {
        return new ConfigurationDefaults("/etc/securesmarthome.conf", "/var/lib/securesmarthome/keystore");
    }
}
