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

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.container.Component;
import de.unipassau.isl.evs.ssh.core.database.AlreadyInUseException;
import de.unipassau.isl.evs.ssh.core.database.DatabaseControllerException;
import de.unipassau.isl.evs.ssh.core.database.UnknownReferenceException;
import de.unipassau.isl.evs.ssh.core.database.dto.PermissionDTO;
import de.unipassau.isl.evs.ssh.core.database.dto.UserDevice;
import de.unipassau.isl.evs.ssh.core.messaging.Message;
import de.unipassau.isl.evs.ssh.core.messaging.RoutingKey;
import de.unipassau.isl.evs.ssh.core.messaging.payload.DeleteDevicePayload;
import de.unipassau.isl.evs.ssh.core.messaging.payload.ErrorPayload;
import de.unipassau.isl.evs.ssh.core.messaging.payload.GenerateNewRegisterTokenPayload;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.core.sec.DeviceConnectInformation;
import de.unipassau.isl.evs.ssh.core.sec.KeyStoreController;
import de.unipassau.isl.evs.ssh.master.database.DatabaseContract;
import de.unipassau.isl.evs.ssh.master.database.PermissionController;
import de.unipassau.isl.evs.ssh.master.database.UserManagementController;
import de.unipassau.isl.evs.ssh.master.network.Server;
import de.unipassau.isl.evs.ssh.master.network.broadcast.UserConfigurationBroadcaster;
import io.netty.channel.Channel;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys.MASTER_USER_DELETE;
import static de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys.MASTER_USER_REGISTER;
import static de.unipassau.isl.evs.ssh.core.sec.Permission.ADD_USER;
import static de.unipassau.isl.evs.ssh.core.sec.Permission.DELETE_USER;

/**
 * Handles messages indicating that a device wants to register itself at the system and also generates
 * messages for each target that needs to know of this event and passes them to the OutgoingRouter.
 *
 * @author Leon Sell
 */
public class MasterRegisterDeviceHandler extends AbstractMasterHandler implements Component {
    public static final Key<MasterRegisterDeviceHandler> KEY = new Key<>(MasterRegisterDeviceHandler.class);

    public static final String FIRST_USER = "Admin";
    public static final String FIRST_GROUP = DatabaseContract.Group.DefaultValues.PARENTS.toString();
    private final Map<String, UserDevice> userDeviceForToken = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public RoutingKey[] getRoutingKeys() {
        return new RoutingKey[]{MASTER_USER_REGISTER, MASTER_USER_DELETE};
    }


    @Override
    public void handle(Message.AddressedMessage message) {
        if (MASTER_USER_REGISTER.matches(message)) {
            handleInitRequest(message);
        } else if (MASTER_USER_DELETE.matches(message)) {
            deleteUser(MASTER_USER_DELETE.getPayload(message), message);
        } else {
            invalidMessage(message);
        }
    }

    private void deleteUser(DeleteDevicePayload payload, Message.AddressedMessage original) {
        DeviceID fromID = original.getFromID();
        final DeviceID userToDelete = payload.getUser();

        if (!hasPermission(fromID, DELETE_USER)) {
            sendNoPermissionReply(original, DELETE_USER);
            return;
        }
        requireComponent(UserManagementController.KEY).removeUserDevice(userToDelete);

        final Channel channel = requireComponent(Server.KEY).findChannel(userToDelete);
        if (channel != null) {
            channel.close();
        }

        sendReply(original, new Message());
        requireComponent(UserConfigurationBroadcaster.KEY).updateAllClients();
    }

    /**
     * Generates a new token which devices can use to register at the system.
     *
     * @param device information which will be associated with the device using this token to register.
     * @return the generated token.
     */
    public byte[] generateNewRegisterToken(UserDevice device) throws AlreadyInUseException {
        if (requireComponent(UserManagementController.KEY).getUserDevice(device.getName()) != null) {
            throw new AlreadyInUseException("A device with the given name is already registered at the system.");
        }
        final byte[] token = DeviceConnectInformation.getRandomToken();
        userDeviceForToken.put(Base64.encodeBase64String(token), device);
        return token;
    }

    /**
     * Uses the information saved for the given token to register the new device.
     *
     * @return {@code true} if the registration was successful
     */
    public boolean registerDevice(X509Certificate certificate, byte[] token) {
        final String base64Token = Base64.encodeBase64String(token);
        final DeviceID deviceID = DeviceID.fromCertificate(certificate);
        if (userDeviceForToken.containsKey(base64Token)) {
            final UserDevice newDevice = userDeviceForToken.get(base64Token);
            newDevice.setUserDeviceID(deviceID);
            //Save certificate to KeyStore
            try {
                requireComponent(KeyStoreController.KEY).saveCertificate(certificate, deviceID.getIDString());
            } catch (GeneralSecurityException gse) {
                throw new RuntimeException("An error occurred while adding the certificate of the new device to"
                        + " the KeyStore.", gse);
            }
            addUserDeviceToDatabase(deviceID, newDevice);
            userDeviceForToken.remove(base64Token);
            requireComponent(UserConfigurationBroadcaster.KEY).updateAllClients();
            return true;
        } else {
            logger.warn("Some tried using an unknown token to register. Token: " + base64Token
                    + ". Certificate: " + certificate);
            return false;
        }
    }

    private void addUserDeviceToDatabase(DeviceID deviceID, UserDevice newDevice) {
        try {
            requireComponent(UserManagementController.KEY).addUserDevice(newDevice);
        } catch (DatabaseControllerException dce) {
            throw new RuntimeException("An error occurred while adding the new device to the database", dce);
        }
        //Add permissions. First device gets all permissions. Others get the permissions of the group they belong to.
        if (requireComponent(UserManagementController.KEY).getUserDevices().size() == 1) {
            final List<PermissionDTO> permissions = requireComponent(PermissionController.KEY).getPermissions();
            for (PermissionDTO permission : permissions) {
                try {
                    requireComponent(PermissionController.KEY).addUserPermission(
                            deviceID,
                            permission.getPermission(),
                            permission.getModuleName()
                    );
                } catch (UnknownReferenceException ure) {
                    throw new RuntimeException("There was a problem adding all permissions to the newly added user. " +
                            "Maybe a permission was deleted while adding permissions to the new user.", ure);
                }
            }
        } else {
            final String templateName = requireComponent(UserManagementController.KEY)
                    .getGroup(newDevice.getInGroup()).getTemplateName();
            final List<PermissionDTO> permissions = requireComponent(PermissionController.KEY)
                    .getPermissionsOfTemplate(templateName);
            for (PermissionDTO permission : permissions) {
                try {
                    requireComponent(PermissionController.KEY).addUserPermission(
                            deviceID,
                            permission.getPermission(),
                            permission.getModuleName()
                    );
                } catch (UnknownReferenceException ure) {
                    throw new RuntimeException("There was a problem adding all permissions to the newly added user. " +
                            "Maybe a permission was deleted while adding permissions to the new user.", ure);
                }
            }
        }
    }

    private void handleInitRequest(Message.AddressedMessage message) {
        if (hasPermission(message.getFromID(), ADD_USER)) {
            final GenerateNewRegisterTokenPayload generateNewRegisterTokenPayload =
                    MASTER_USER_REGISTER.getPayload(message);
            final byte[] newToken;
            try {
                newToken = generateNewRegisterToken(generateNewRegisterTokenPayload.getUserDevice());
            } catch (AlreadyInUseException e) {
                sendReply(message, new Message(new ErrorPayload(e)));
                return;
            }
            final String base64Token = Base64.encodeBase64String(newToken);
            userDeviceForToken.put(base64Token, generateNewRegisterTokenPayload.getUserDevice());
            final Message reply = new Message(new GenerateNewRegisterTokenPayload(
                    newToken,
                    generateNewRegisterTokenPayload.getUserDevice()
            ));
            sendReply(message, reply);
        } else {
            sendNoPermissionReply(message, ADD_USER);
        }
    }
}