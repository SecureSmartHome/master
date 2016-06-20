package de.unipassau.isl.evs.ssh.master;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

/**
 * Created by popeye on 6/19/16.
 */
public class Main implements Daemon {

    private MasterContainer masterContainer;

    @Override
    public void init(DaemonContext context) throws DaemonInitException, Exception {
        masterContainer = new MasterContainer();

    }

    @Override
    public void start() throws Exception {
        masterContainer.onCreate();
    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public void destroy() {
        masterContainer.onDestroy();
    }
}
