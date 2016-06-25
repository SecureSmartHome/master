/*
 * MIT License
 *
 * Copyright (c) 2016.
 * Bucher Andreas, Fink Simon Dominik, Fraedrich Christoph, Popp Wolfgang,
 * Sell Leon, Werli Philemon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.unipassau.isl.evs.ssh.master;

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConfiguration;
import de.unipassau.isl.evs.ssh.core.CoreConstants;

import java.util.NoSuchElementException;

/**
 * The MasterConfiguration provides configuration details for the master daemon.
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
        return new ConfigurationDefaults("/etc/securesmarthome.conf", "/var/lib/securesmarthome/keystore", "2345ih43hij");
    }
}
