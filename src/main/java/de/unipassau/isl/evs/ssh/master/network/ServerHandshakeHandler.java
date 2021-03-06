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

import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.database.dto.Slave;
import de.unipassau.isl.evs.ssh.core.database.dto.UserDevice;
import de.unipassau.isl.evs.ssh.core.handler.MessageHandler;
import de.unipassau.isl.evs.ssh.core.messaging.IncomingDispatcher;
import de.unipassau.isl.evs.ssh.core.messaging.Message;
import de.unipassau.isl.evs.ssh.core.messaging.OutgoingRouter;
import de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys;
import de.unipassau.isl.evs.ssh.core.messaging.payload.DeviceConnectedPayload;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.core.naming.NamingManager;
import de.unipassau.isl.evs.ssh.core.network.handler.Decrypter;
import de.unipassau.isl.evs.ssh.core.network.handler.Encrypter;
import de.unipassau.isl.evs.ssh.core.network.handler.PipelinePlug;
import de.unipassau.isl.evs.ssh.core.network.handler.SignatureChecker;
import de.unipassau.isl.evs.ssh.core.network.handler.SignatureGenerator;
import de.unipassau.isl.evs.ssh.core.network.handler.TimeoutHandler;
import de.unipassau.isl.evs.ssh.core.network.handshake.HandshakeException;
import de.unipassau.isl.evs.ssh.core.network.handshake.HandshakePacket;
import de.unipassau.isl.evs.ssh.core.network.handshake.HandshakePacket.ServerAuthenticationResponse;
import de.unipassau.isl.evs.ssh.core.sec.KeyStoreController;
import de.unipassau.isl.evs.ssh.master.database.SlaveController;
import de.unipassau.isl.evs.ssh.master.database.UserManagementController;
import de.unipassau.isl.evs.ssh.master.handler.MasterRegisterDeviceHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import static de.unipassau.isl.evs.ssh.core.CoreConstants.NettyConstants.*;

/**
 * A ChannelHandlerAdapter that will execute the Handshake with the Client and add the IncomingDispatcher on success.
 * See {@link HandshakePacket} for the handshake sequence, also {@link #setState(ChannelHandlerContext, State, State)}
 * transitions show the order the functions must be executed.
 *
 * @author Niko Fink: Handshake Sequence
 * @author Christoph Fraedrich: Registration
 */
@ChannelHandler.Sharable
public class ServerHandshakeHandler extends ChannelHandlerAdapter {
    private static final AttributeKey<byte[]> CHAP_CHALLENGE = AttributeKey.valueOf(ServerHandshakeHandler.class, "CHAP_CHALLENGE");
    private static final AttributeKey<State> STATE = AttributeKey.valueOf(ServerHandshakeHandler.class, "STATE");
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Server server;
    private final Container container;

    public ServerHandshakeHandler(Server server, Container container) {
        this.server = server;
        this.container = container;
    }

    /**
     * Configures the per-connection pipeline that is responsible for handling incoming and outgoing data.
     * After an incoming packet is decrypted, decoded and verified,
     * it will be sent to its target {@link MessageHandler}
     * by the {@link IncomingDispatcher}.
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        logger.debug("channelRegistered " + ctx);
        if (container == null) {
            //Do not accept new connections after the Server has been shut down
            logger.debug("channelRegistered:closed");
            ctx.close();
            return;
        }

        // Add (de-)serialization Handlers before this Handler
        ctx.pipeline().addBefore(ctx.name(), ObjectEncoder.class.getSimpleName(), new ObjectEncoder());
        ctx.pipeline().addBefore(ctx.name(), ObjectDecoder.class.getSimpleName(), new ObjectDecoder(
                ClassResolvers.weakCachingConcurrentResolver(getClass().getClassLoader())));
        ctx.pipeline().addBefore(ctx.name(), LoggingHandler.class.getSimpleName(), new LoggingHandler(LogLevel.TRACE));

        // Timeout Handler
        ctx.pipeline().addBefore(ctx.name(), IdleStateHandler.class.getSimpleName(),
                new IdleStateHandler(READER_IDLE_TIME, WRITER_IDLE_TIME, ALL_IDLE_TIME));
        ctx.pipeline().addBefore(ctx.name(), TimeoutHandler.class.getSimpleName(), new TimeoutHandler());

        // Add exception handler
        ctx.pipeline().addLast(PipelinePlug.class.getSimpleName(), new PipelinePlug());

        super.channelRegistered(ctx);
        logger.debug("Pipeline after register: " + ctx.pipeline());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("channelActive " + ctx);
        super.channelActive(ctx);
        assert container.require(NamingManager.KEY).isMaster();
        setState(ctx, null, State.EXPECT_HELLO);
        final boolean isLocal = ((InetSocketAddress) ctx.channel().localAddress()).getPort() == server.getLocalPort();
        ctx.attr(ATTR_LOCAL_CONNECTION).set(isLocal);
        logger.debug("Channel to " + (isLocal ? "local" : "internet") + " device open, waiting for Client Hello");
        setChapChallenge(ctx, new byte[HandshakePacket.CHAP.CHALLENGE_LENGTH]);
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof HandshakePacket.Hello) {
                handleHello(ctx, ((HandshakePacket.Hello) msg));
            } else if (msg instanceof HandshakePacket.CHAP) {
                if (getState(ctx) == State.EXPECT_INITIAL_CHAP) {
                    handleInitialChapRequest(ctx, ((HandshakePacket.CHAP) msg));
                } else {
                    handleFinalChapResponse(ctx, ((HandshakePacket.CHAP) msg));
                }
            } else if (msg instanceof HandshakePacket.ActiveRegistrationRequest) {
                handleActiveRegistrationRequest(ctx, ((HandshakePacket.ActiveRegistrationRequest) msg));
            } else {
                throw new HandshakeException("Illegal Handshake packet received");
            }
        } catch (Exception e) {
            ctx.close();
            throw e;
        }
    }

    private void handleHello(ChannelHandlerContext ctx, HandshakePacket.Hello msg) throws GeneralSecurityException {
        setState(ctx, State.EXPECT_HELLO, State.EXPECT_INITIAL_CHAP);
        logger.debug("Got Client Hello, sending Server Hello and awaiting 1. CHAP as response");

        assert !msg.isMaster;
        final X509Certificate deviceCertificate = msg.certificate;
        ctx.attr(CoreConstants.NettyConstants.ATTR_PEER_CERT).set(deviceCertificate);
        final DeviceID deviceID = DeviceID.fromCertificate(deviceCertificate);
        ctx.attr(CoreConstants.NettyConstants.ATTR_PEER_ID).set(deviceID);
        logger.debug("Client " + deviceID + " connected, checking authentication");

        final X509Certificate masterCert = container.require(NamingManager.KEY).getMasterCertificate();
        final boolean isMaster = container.require(NamingManager.KEY).isMaster();
        ctx.writeAndFlush(new HandshakePacket.Hello(masterCert, isMaster)).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

        // add Security handlers
        final PublicKey remotePublicKey = deviceCertificate.getPublicKey();
        final PrivateKey localPrivateKey = container.require(KeyStoreController.KEY).getOwnPrivateKey();
        ctx.pipeline().addBefore(ObjectEncoder.class.getSimpleName(), Encrypter.class.getSimpleName(), new Encrypter(remotePublicKey));
        ctx.pipeline().addBefore(ObjectEncoder.class.getSimpleName(), Decrypter.class.getSimpleName(), new Decrypter(localPrivateKey));
        ctx.pipeline().addBefore(ObjectEncoder.class.getSimpleName(), SignatureChecker.class.getSimpleName(), new SignatureChecker(remotePublicKey));
        ctx.pipeline().addBefore(ObjectEncoder.class.getSimpleName(), SignatureGenerator.class.getSimpleName(), new SignatureGenerator(localPrivateKey));
    }

    private void handleInitialChapRequest(ChannelHandlerContext ctx, HandshakePacket.CHAP msg) throws HandshakeException {
        setState(ctx, State.EXPECT_INITIAL_CHAP, State.EXPECT_FINAL_CHAP);
        logger.debug("Got 1. CHAP, sending 2. CHAP and awaiting 3. CHAP as response");

        if (msg.challenge == null || msg.response != null) {
            throw new HandshakeException("Illegal CHAP Response");
        }
        final byte[] chapChallenge = getChapChallenge(ctx);
        new SecureRandom().nextBytes(chapChallenge);
        ctx.writeAndFlush(new HandshakePacket.CHAP(chapChallenge, msg.challenge)).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    private void handleFinalChapResponse(ChannelHandlerContext ctx, HandshakePacket.CHAP msg) throws HandshakeException {
        setState(ctx, State.EXPECT_FINAL_CHAP, State.CHECK_AUTH);
        logger.debug("Got 3. CHAP, sending Status");

        if (msg.challenge != null || msg.response == null) {
            throw new HandshakeException("Illegal CHAP Response");
        }
        if (!Arrays.equals(getChapChallenge(ctx), msg.response)) {
            throw new HandshakeException("CHAP Packet with invalid response");
        }

        checkAuthentication(ctx);
    }

    private void checkAuthentication(ChannelHandlerContext ctx) throws HandshakeException {
        setState(ctx, State.CHECK_AUTH, State.CHECK_AUTH);

        final DeviceID deviceID = ctx.attr(CoreConstants.NettyConstants.ATTR_PEER_ID).get();
        final Slave slave = container.require(SlaveController.KEY).getSlave(deviceID);
        final UserDevice userDevice = container.require(UserManagementController.KEY).getUserDevice(deviceID);
        if (slave != null || userDevice != null) {
            setState(ctx, State.CHECK_AUTH, State.FINISHED);
            logger.info("Device " + deviceID + " is registered as " + (slave != null ? "Slave " + slave : "UserDevice " + userDevice));

            final byte[] passiveRegistrationToken = slave == null ? null : slave.getPassiveRegistrationToken();
            final boolean isConnectionLocal = ctx.attr(ATTR_LOCAL_CONNECTION).get() == Boolean.TRUE;
            ctx.writeAndFlush(ServerAuthenticationResponse.authenticated(
                    null, passiveRegistrationToken, isConnectionLocal
            )).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

            handshakeSuccessful(ctx);
        } else {
            setState(ctx, State.CHECK_AUTH, State.EXPECT_REGISTER);
            logger.info("Device " + deviceID + " is not registered, requesting registration");

            ctx.writeAndFlush(ServerAuthenticationResponse.unauthenticated(
                    "Unknown Client, please register."
            )).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    private void handleActiveRegistrationRequest(ChannelHandlerContext ctx, HandshakePacket.ActiveRegistrationRequest msg) throws HandshakeException {
        setState(ctx, State.EXPECT_REGISTER, State.CHECK_AUTH);

        // send client register info to handler
        boolean success = container.require(MasterRegisterDeviceHandler.KEY).registerDevice(
                ctx.attr(CoreConstants.NettyConstants.ATTR_PEER_CERT).get(),
                msg.activeRegistrationToken
        );

        if (success) {
            logger.debug("Accepted registration request from " + ctx.attr(CoreConstants.NettyConstants.ATTR_PEER_ID).get());
            checkAuthentication(ctx);
        } else {
            setState(ctx, State.CHECK_AUTH, State.EXPECT_REGISTER);
            logger.debug("Rejected registration request from " + ctx.attr(CoreConstants.NettyConstants.ATTR_PEER_ID).get());

            ctx.writeAndFlush(ServerAuthenticationResponse.unauthenticated(
                    "Client registration rejected, closing connection."
            )).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    protected void handshakeSuccessful(ChannelHandlerContext ctx) {
        final State state = getState(ctx);
        if (state != State.FINISHED) {
            throw new IllegalStateException("Handshake not finished: " + state);
        }
        final DeviceID deviceID = ctx.channel().attr(ATTR_PEER_ID).get();

        // allow pings
        TimeoutHandler.setPingEnabled(ctx.channel(), true);
        // add Dispatcher
        ctx.pipeline().addBefore(ctx.name(), IncomingDispatcher.class.getSimpleName(), container.require(IncomingDispatcher.KEY));
        // Logging is handled by IncomingDispatcher and OutgoingRouter
        ctx.pipeline().remove(LoggingHandler.class.getSimpleName());
        // remove HandshakeHandler
        ctx.pipeline().remove(this);

        // Register connection
        server.getActiveChannels().add(ctx.channel());
        logger.info("Handshake with " + deviceID + " successful, current Pipeline: " + ctx.pipeline());

        Message message = new Message(new DeviceConnectedPayload(deviceID, ctx.channel(), ctx.attr(ATTR_LOCAL_CONNECTION).get()));
        container.require(OutgoingRouter.KEY).sendMessageLocal(RoutingKeys.MASTER_DEVICE_CONNECTED, message);

        ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                for (Server.ServerConnectionListener listener : server.listeners) {
                    listener.onClientConnected(future.channel());
                }
            }
        });

        for (Server.ServerConnectionListener listener : server.listeners) {
            listener.onClientConnected(ctx.channel());
        }
    }

    private void setState(ChannelHandlerContext ctx, @Nullable State expectedState, @Nullable State newState) throws HandshakeException {
        if (!ctx.channel().attr(STATE).compareAndSet(expectedState, newState)) {
            throw new HandshakeException("Expected state " + expectedState + " but was " + getState(ctx) + ", " +
                    "new state would have been " + newState);
        }
        logger.debug("State transition " + expectedState + " -> " + newState);
    }

    private State getState(ChannelHandlerContext ctx) {
        return ctx.channel().attr(STATE).get();
    }

    private void setChapChallenge(ChannelHandlerContext ctx, byte[] value) {
        ctx.channel().attr(CHAP_CHALLENGE).set(value);
    }

    private byte[] getChapChallenge(ChannelHandlerContext ctx) {
        return ctx.channel().attr(CHAP_CHALLENGE).get();
    }

    private enum State {
        EXPECT_HELLO, EXPECT_INITIAL_CHAP, EXPECT_FINAL_CHAP, EXPECT_REGISTER, CHECK_AUTH, FINISHED
    }
}
