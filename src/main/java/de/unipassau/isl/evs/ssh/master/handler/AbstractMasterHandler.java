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

package de.unipassau.isl.evs.ssh.master.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.unipassau.isl.evs.ssh.core.database.dto.UserDevice;
import de.unipassau.isl.evs.ssh.core.handler.AbstractMessageHandler;
import de.unipassau.isl.evs.ssh.core.handler.NoPermissionException;
import de.unipassau.isl.evs.ssh.core.messaging.Message;
import de.unipassau.isl.evs.ssh.core.messaging.RoutingKey;
import de.unipassau.isl.evs.ssh.core.messaging.payload.ErrorPayload;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.core.naming.NamingManager;
import de.unipassau.isl.evs.ssh.core.sec.Permission;
import de.unipassau.isl.evs.ssh.master.database.PermissionController;
import de.unipassau.isl.evs.ssh.master.database.SlaveController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a MasterHandler providing functionality all MasterHandlers need. This will avoid needing to implement the
 * same functionality over and over again.
 *
 * @author Leon Sell
 */
public abstract class AbstractMasterHandler extends AbstractMessageHandler {
    private final Map<Integer, Message.AddressedMessage> proxiedMessages = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Remember that I sent another message (the proxy message) in order to fulfill a received request.
     * The originally received message will be mapped to the sequence number of the proxy message,
     * so that it can be later retrieved using the {@link Message#HEADER_REFERENCES_ID} of the reply for the proxy message.
     *
     * @param receivedMessage the Message with a Request from the App or the Slave that was received.
     * @param proxyMessage    the Message that was sent by me (the Master) in order to get data required for responding
     *                        to the original request.
     * @see #takeProxiedReceivedMessage(int)
     */
    protected void recordReceivedMessageProxy(Message.AddressedMessage receivedMessage, Message.AddressedMessage proxyMessage) {
        final DeviceID masterID = requireComponent(NamingManager.KEY).getMasterID();
        if (!proxyMessage.getFromID().equals(masterID)) {
            logger.warn("Messages from other devices can't act as proxy: " + proxyMessage);
        }
        proxiedMessages.put(proxyMessage.getSequenceNr(), receivedMessage);
    }

    /**
     * Get the originally received message identified by the sequence number of the proxy message that was sent to
     * fulfill the original message.
     *
     * @param proxySequenceNumber the sequence number of the proxy message, usually obtained from the reply to the
     *                            proxy message by getting the {@link Message#HEADER_REFERENCES_ID} header field.
     * @return the message that I originally received.
     */
    protected Message.AddressedMessage takeProxiedReceivedMessage(int proxySequenceNumber) {
        return proxiedMessages.remove(proxySequenceNumber);
    }

    /**
     * Returns whether the device with the given DeviceID is a Slave.
     *
     * @param deviceID DeviceID for the device to check for whether it is a Slave or not.
     * @return Whether or not the device with given DeviceID is a Slave.
     */
    public boolean isSlave(DeviceID deviceID) {
        return requireComponent(SlaveController.KEY).getSlave(deviceID) != null;
    }

    /**
     * Returns whether the device with the given DeviceID is a Master.
     *
     * @param userDeviceID DeviceID for the device to check for whether it is a Master or not.
     * @return Whether or not the device with given DeviceID is a Master.
     */
    public boolean isMaster(DeviceID userDeviceID) {
        return userDeviceID.equals(requireComponent(NamingManager.KEY).getMasterID());
    }

    /**
     * Check if a given Device has a given Permission. Master has all Permission.
     *
     * @param userDeviceID DeviceID of the Device.
     * @param permission   Permission to check for.
     * @param moduleName   Module the Permission applies for.
     * @return true if has permissions otherwise false.
     */
    protected boolean hasPermission(DeviceID userDeviceID, Permission permission, String moduleName) {
        return isMaster(userDeviceID) || requireComponent(PermissionController.KEY)
                .hasPermission(userDeviceID, permission, moduleName);
    }

    /**
     * Has Permission for Permissions that are independent of a Module.
     *
     * @param userDeviceID DeviceID of the Device.
     * @param permission   Permission to check for.
     * @return true if has permissions otherwise false.
     */
    protected boolean hasPermission(DeviceID userDeviceID, Permission permission) {
        return hasPermission(userDeviceID, permission, null);
    }

    /**
     * Send a message to all Devices that have a given Permission.
     *
     * @param messageToSend Message to send to the devices.
     * @param permission    Permission the Device has to have.
     * @param moduleName    Module to Permission applies for.
     * @param routingKey    RoutingKey to send the message with.
     */
    protected void sendMessageToAllDevicesWithPermission(
            Message messageToSend,
            Permission permission,
            String moduleName,
            RoutingKey routingKey
    ) {
        final List<UserDevice> allUserDevicesWithPermission = requireComponent(PermissionController.KEY)
                .getAllUserDevicesWithPermission(permission, moduleName);
        for (UserDevice userDevice : allUserDevicesWithPermission) {
            sendMessage(userDevice.getUserDeviceID(), routingKey, messageToSend);
        }
    }

    /**
     * Send a reply containing a no permission error.
     *
     * @param original   Message to reply to
     * @param permission Permission the author of the original Message doesn't have.
     */
    protected void sendNoPermissionReply(Message.AddressedMessage original, Permission permission) {
        Message message = new Message(new ErrorPayload(new NoPermissionException(permission)));
        sendReply(original, message);
    }
}
