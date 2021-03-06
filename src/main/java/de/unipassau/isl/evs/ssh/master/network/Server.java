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

package de.unipassau.isl.evs.ssh.master.network;


import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.container.StartupException;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.core.network.NettyInternalLogger;
import de.unipassau.isl.evs.ssh.core.schedule.ExecutionServiceComponent;
import de.unipassau.isl.evs.ssh.master.MasterConfiguration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static de.unipassau.isl.evs.ssh.core.CoreConstants.NettyConstants.ATTR_PEER_ID;

/**
 * The heart of the master server: a netty stack accepting connections from devices and handling communication with them using a netty pipeline.
 * Additionally, it keeps track of timeouts and holds the global connection registry.
 * For details about the pipeline, see {@link #startServer()} and the {@link ServerHandshakeHandler} returned by {@link #getHandshakeHandler()}.
 * As this component is only active on the Master, the terms "Master" and "Server" are used interchangeably.
 *
 * @author Niko Fink
 */
public class Server extends AbstractComponent {
    public static final Key<Server> KEY = new Key<>(Server.class);

    public static final String PREF_SERVER_LOCAL_PORT = Server.class.getName() + ".PREF_SERVER_LOCAL_PORT";
    public static final String PREF_SERVER_PUBLIC_PORT = Server.class.getName() + ".PREF_SERVER_PUBLIC_PORT";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The channel listening for incoming local connections on the port of the server.
     * Use {@link ChannelFuture#sync()} to wait for server startup.
     */
    private ChannelFuture localChannel;
    /**
     * The channel listening for incoming connections from the internet on the port of the server.
     * Use {@link ChannelFuture#sync()} to wait for server startup.
     */
    private ChannelFuture publicChannel;
    /**
     * A ChannelGroup containing really <i>all</i> incoming connections.
     * If it isn't contained here, it is not connected to the Server.
     */
    private ChannelGroup connections;

    /**
     * Init timeouts and the connection registry and start the netty IO server synchronously
     */
    @Override
    public void init(Container container) {
        super.init(container);
        try {
            // Configure netty
            InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory() {
                @Override
                public InternalLogger newInstance(String name) {
                    return new NettyInternalLogger(name);
                }
            });
            ResourceLeakDetector.setLevel(CoreConstants.NettyConstants.RESOURCE_LEAK_DETECTION);
            // Start server
            startServer();
        } catch (InterruptedException e) {
            throw new StartupException("Could not start netty server", e);
        }
    }

    /**
     * Scotty, start me up!
     * Initializes the netty data pipeline and starts the IO server
     *
     * @throws InterruptedException  if interrupted while waiting for the startup
     * @throws IllegalStateException is the Server is already running
     */
    private void startServer() throws InterruptedException {
        if (isChannelOpen()) {
            throw new IllegalStateException("Server already running");
        }

        //Setup the Executor and Connection Pool
        final ExecutionServiceComponent eventLoop = requireComponent(ExecutionServiceComponent.KEY);
        connections = new DefaultChannelGroup(eventLoop.next());

        ServerBootstrap b = new ServerBootstrap()
                .group(eventLoop)
                .channel(NioServerSocketChannel.class)
                .childHandler(getHandshakeHandler())
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        //Bind to ports and wait for the start of the server
        final int localPort = getLocalPort();
        if (localPort < 0 || localPort > 65535) {
            throw new StartupException("Illegal localPort " + localPort);
        }
        localChannel = b.bind(localPort).sync();

        final int publicPort = getPublicPort();
        if (publicPort >= 0 && publicPort <= 65535 && localPort != publicPort) {
            publicChannel = b.bind(publicPort).sync();
        }
        logger.info("Server bound to port " + localChannel.channel() + (publicChannel != null ? " and " + publicChannel.channel() : ""));
    }

    /**
     * HandshakeHandler can be changed or mocked for testing
     *
     * @return the ServerHandshakeHandler to use
     */
    @NotNull
    protected ServerHandshakeHandler getHandshakeHandler() {
        assert getContainer() != null;
        return new ServerHandshakeHandler(this, getContainer());
    }

    /**
     * Stop listening and close all connections.
     */
    public void destroy() {
        if (localChannel != null && localChannel.channel() != null) {
            localChannel.channel().close();
        }
        if (publicChannel != null && publicChannel.channel() != null) {
            publicChannel.channel().close();
        }
        super.destroy();
    }

    /**
     * Finds the Channel representing a connection to a Client with a matching DeviceID.
     *
     * @return the found Channel, or {@code null} if no Channel matches the given ID
     */
    @Nullable
    public Channel findChannel(DeviceID id) {
        for (Channel channel : connections) {
            //noinspection ConstantConditions
            if (channel.isActive() && Objects.equals(channel.attr(ATTR_PEER_ID).get(), id)) {
                return channel;
            }
        }
        return null;
    }

    /**
     * @return the port of the Server for local connections set in the SharedPreferences or {@link CoreConstants.NettyConstants#DEFAULT_LOCAL_PORT}
     * @see #localChannel
     */
    int getLocalPort() {
        return requireComponent(MasterConfiguration.KEY).getLocalPort();
    }

    /**
     * @return the port of the Server for connections from the internet set in the SharedPreferences or {@link CoreConstants.NettyConstants#DEFAULT_LOCAL_PORT}
     * @see #publicChannel
     */
    int getPublicPort() {
        return requireComponent(MasterConfiguration.KEY).getPublicPort();
    }

    /**
     * @return the local Address this server is listening on
     */
    @Nullable
    public InetSocketAddress getAddress() {
        if (localChannel != null && localChannel.channel() != null) {
            return (InetSocketAddress) localChannel.channel().localAddress();
        } else {
            return null;
        }
    }

    /**
     * @return the public Address this server is listening on
     */
    @Nullable
    public InetSocketAddress getPublicAddress() {
        if (publicChannel != null && publicChannel.channel() != null) {
            return (InetSocketAddress) publicChannel.channel().localAddress();
        } else {
            return null;
        }
    }

    /**
     * @return the ChannelGroup containing <b>all</b> currently open connections
     */
    public ChannelGroup getActiveChannels() {
        return connections;
    }

    /**
     * @return an Iterable containing the DeviceIDs of all currently connected Devices
     */
    public Iterable<DeviceID> getActiveDevices() {
        final ChannelGroup input = getActiveChannels();
        final Iterable<DeviceID> transformed = Iterables.transform(input, new Function<Channel, DeviceID>() {
            @Override
            public DeviceID apply(Channel input) {
                return input.attr(ATTR_PEER_ID).get();
            }
        });
        //noinspection UnnecessaryLocalVariable
        final Iterable<DeviceID> filtered = Iterables.filter(transformed, new Predicate<DeviceID>() {
            @Override
            public boolean apply(@Nullable DeviceID input) {
                return input != null;
            }
        });
        return filtered;
    }

    /**
     * @return {@code true}, if the Server TCP channel is currently open
     */
    public boolean isChannelOpen() {
        return localChannel != null && localChannel.channel() != null && localChannel.channel().isOpen();
    }

    /**
     * Blocks until the Server channel has been closed.
     *
     * @throws InterruptedException
     * @see #isChannelOpen()
     * @see Channel#closeFuture()
     */
    public void awaitShutdown() throws InterruptedException {
        if (localChannel != null && localChannel.channel() != null) {
            localChannel.channel().closeFuture().await();
        }
        if (publicChannel != null && publicChannel.channel() != null) {
            publicChannel.channel().closeFuture().await();
        }
    }

    final List<ServerConnectionListener> listeners = new LinkedList<>();

    public boolean addListener(ServerConnectionListener object) {
        return listeners.add(object);
    }

    public boolean removeListener(ServerConnectionListener object) {
        return listeners.remove(object);
    }

    public interface ServerConnectionListener {
        void onClientConnected(Channel channel);

        void onClientDisonnected(Channel channel);
    }
}
