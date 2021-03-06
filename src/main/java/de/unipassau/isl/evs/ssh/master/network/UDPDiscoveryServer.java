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

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.core.naming.NamingManager;
import de.unipassau.isl.evs.ssh.core.network.Client;
import de.unipassau.isl.evs.ssh.core.schedule.ExecutionServiceComponent;
import de.unipassau.isl.evs.ssh.core.sec.KeyStoreController;
import de.unipassau.isl.evs.ssh.master.database.SlaveController;
import de.unipassau.isl.evs.ssh.master.database.UserManagementController;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ReferenceCountUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.Signature;

import static de.unipassau.isl.evs.ssh.core.CoreConstants.NettyConstants.DISCOVERY_PAYLOAD_REQUEST;
import static de.unipassau.isl.evs.ssh.core.CoreConstants.NettyConstants.DISCOVERY_PAYLOAD_RESPONSE;

/**
 * This component is responsible for responding to UDP discovery packets and signalling the address and port back to
 * {@link Client}s searching for this Master.
 *
 * @author Niko Fink
 */
public class UDPDiscoveryServer extends AbstractComponent {
    public static final Key<UDPDiscoveryServer> KEY = new Key<>(UDPDiscoveryServer.class);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * The channel listening for incoming UDP connections on the port of the client.
     * Use {@link ChannelFuture#sync()} to wait for client startup.
     */
    private ChannelFuture channel;

    @Override
    public void init(Container container) {
        super.init(container);

        // Setup UDP Channel
        Bootstrap b = new Bootstrap()
                .channel(NioDatagramChannel.class)
                .group(requireComponent(ExecutionServiceComponent.KEY))
                .handler(new RequestHandler())
                .option(ChannelOption.SO_BROADCAST, true);
        channel = b.bind(CoreConstants.NettyConstants.DISCOVERY_SERVER_PORT);
    }

    @Override
    public void destroy() {
        if (channel.channel().isActive()) {
            channel.channel().close();
        }
        super.destroy();
    }

    /**
     * Send a response with the ConnectInformation of this Master to the requesting Client.
     *
     * @param request the request sent from a {@link Client}
     */
    private void sendDiscoveryResponse(DatagramPacket request) {
        final ByteBuf buffer = channel.channel().alloc().heapBuffer();

        // gather information
        final byte[] header = DISCOVERY_PAYLOAD_RESPONSE.getBytes();
        final String addressString = request.recipient().getAddress().getHostAddress();
        final byte[] address = addressString.getBytes();
        final InetSocketAddress serverAddress = requireComponent(Server.KEY).getAddress();
        if (serverAddress == null) {
            logger.warn("Could not respond to UDP discovery request as Server is not started yet");
            return;
        }
        final int port = serverAddress.getPort();
        logger.info("sendDiscoveryResponse with connection data " + addressString + ":" + port + " to " + request.sender());

        // write it to the buffer
        buffer.writeInt(header.length);
        buffer.writeBytes(header);
        buffer.writeInt(address.length);
        buffer.writeBytes(address);
        buffer.writeInt(port);

        // and sign the data
        try {
            Signature signature = Signature.getInstance("ECDSA");
            signature.initSign(requireComponent(KeyStoreController.KEY).getOwnPrivateKey());
            signature.update(buffer.nioBuffer());
            final byte[] sign = signature.sign();
            buffer.writeInt(sign.length);
            buffer.writeBytes(sign);
        } catch (GeneralSecurityException e) {
            logger.warn("Could not send UDP discovery response", e);
        }

        final DatagramPacket response = new DatagramPacket(buffer, request.sender());
        channel.channel().writeAndFlush(response);
    }

    /**
     * The ChannelHandler that receives and parses incoming UDP requests and calls {@link #sendDiscoveryResponse(DatagramPacket)}
     * in order to respond to them.
     */
    private class RequestHandler extends ChannelHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                final DatagramPacket request = (DatagramPacket) msg;
                final ByteBuf buffer = request.content();
                buffer.markReaderIndex();
                final String messageType = readString(buffer);
                if (DISCOVERY_PAYLOAD_RESPONSE.equals(messageType)) {
                    logger.debug("UDP discovery Server can't handle UDP response from " + request.sender());
                    return;
                } else if (!DISCOVERY_PAYLOAD_REQUEST.equals(messageType)) {
                    logger.debug("Discarding UDP packet with illegal message type from " + request.sender() + ": " + messageType);
                    return;
                }
                final DeviceID ownID = requireComponent(NamingManager.KEY).getOwnID();
                final DeviceID clientID = readDeviceID(buffer);
                if (clientID == null) {
                    logger.debug("Discarding UDP inquiry without client ID from " + request.sender());
                    return;
                }

                final boolean isMasterKnown = buffer.readBoolean();
                if (isMasterKnown) {
                    // if the master is known to the device, the ID that the device is searching must match with mine
                    final DeviceID masterID = readDeviceID(buffer);
                    if (!ownID.equals(masterID)) {
                        logger.debug("Discarding UDP inquiry from " + clientID + "(" + request.sender() + ") " +
                                "that is not looking for me (" + ownID + ") but " + masterID);
                        return;
                    }
                } else {
                    // if the device doesn't know his master, the master must know the device
                    if (!isDeviceRegistered(clientID)) {
                        logger.debug("Discarding UDP inquiry from " + clientID + "(" + request.sender() + ") " +
                                "that is looking for any master and is not registered");
                        return;
                    }
                }
                logger.debug("UDP inquiry received from " + clientID + "(" + request.sender() + ") that is looking for "
                        + (isMasterKnown ? "me" : "any master and is registered here"));
                sendDiscoveryResponse(request);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        private boolean isDeviceRegistered(DeviceID clientID) {
            return requireComponent(SlaveController.KEY).getSlave(clientID) != null
                    || requireComponent(UserManagementController.KEY).getUserDevice(clientID) != null;
        }

        /**
         * Read a string.
         */
        @Nullable
        private String readString(ByteBuf buffer) {
            final int length = buffer.readInt();
            if (length < 0 || length > 0xFFFF) {
                return null;
            }
            byte[] value = new byte[length];
            buffer.readBytes(value);
            return new String(value);
        }

        /**
         * Read a DeviceID.
         */
        @Nullable
        private DeviceID readDeviceID(ByteBuf buffer) {
            final int length = buffer.readInt();
            if (length != DeviceID.ID_LENGTH) {
                return null;
            }
            byte[] value = new byte[DeviceID.ID_LENGTH];
            buffer.readBytes(value);
            return new DeviceID(value);
        }
    }
}
