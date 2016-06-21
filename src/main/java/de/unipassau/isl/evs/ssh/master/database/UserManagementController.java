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

package de.unipassau.isl.evs.ssh.master.database;

import com.google.common.base.Strings;
import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.database.AlreadyInUseException;
import de.unipassau.isl.evs.ssh.core.database.DatabaseControllerException;
import de.unipassau.isl.evs.ssh.core.database.IsReferencedException;
import de.unipassau.isl.evs.ssh.core.database.UnknownReferenceException;
import de.unipassau.isl.evs.ssh.core.database.dto.Group;
import de.unipassau.isl.evs.ssh.core.database.dto.UserDevice;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice;
import org.jetbrains.annotations.Nullable;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.exception.DataAccessException;

import java.util.LinkedList;
import java.util.List;

import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup.DEVICEGROUP;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate.PERMISSIONTEMPLATE;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice.USERDEVICE;

/**
 * Offers high level methods to interact with the tables associated with users and groups in the database.
 *
 * @author Leon Sell
 */
public class UserManagementController extends AbstractController {
    public static final Key<UserManagementController> KEY = new Key<>(UserManagementController.class);


    /**
     * Gets the database id of a record that meets the following condition: <code>column = value</code>.
     * This method is basically a wrapper for this query:
     * <code>SELECT _ID FROM table WHERE column = value</code>
     *
     * @param column the column of a table
     * @param value  the value to compare
     * @param <T>
     * @param <V>
     * @return the ID of the record or null if the condition is never met.
     */
    private <T extends Record, V> Integer getID(TableField<T, V> column, V value) {
        Table<T> table = column.getTable();
        Record1<?> idRecord = create.select(table.field(0)).from(table).where(column.equal(value)).fetchOne();
        if (idRecord != null) {
            return (Integer) idRecord.value1();
        }
        return null;
    }

    /**
     * Add a new Group.
     *
     * @param group Group to add.
     */
    public void addGroup(Group group) throws DatabaseControllerException {
        try {
            Integer templateID = getID(PERMISSIONTEMPLATE.NAME, group.getTemplateName());
            create.insertInto(DEVICEGROUP, DEVICEGROUP.NAME, DEVICEGROUP.PERMISSIONTEMPLATEID)
                    .values(group.getName(), templateID)
                    .execute();

        } catch (DataAccessException e) {
            throw new DatabaseControllerException("Either the given Template does not exist in the database"
                    + "or the name is already in use by another Group.", e);
        }
    }

    /**
     * Delete a Group.
     *
     * @param groupName Name of the Group.
     */
    public void removeGroup(String groupName) throws IsReferencedException {
        try {
            create.deleteFrom(DEVICEGROUP)
                    .where(DEVICEGROUP.NAME.equal(groupName))
                    .execute();

        } catch (DataAccessException e) {
            throw new IsReferencedException("This group is used by at least one UserDevice", e);
        }
    }

    /**
     * Get a list of all Groups.
     */
    public List<Group> getGroups() {
        Devicegroup g = DEVICEGROUP.as("g");
        Permissiontemplate t = PERMISSIONTEMPLATE.as("t");

        return create.select(g.NAME, t.NAME)
                .from(g)
                .join(t).on(g.PERMISSIONTEMPLATEID.equal(t._ID))
                .fetchInto(Group.class);
    }

    /**
     * Change the name of a Group.
     *
     * @param oldName Old name of the Group.
     * @param newName New name of the Group.
     */
    public void changeGroupName(String oldName, String newName) throws AlreadyInUseException {
        try {
            create.update(DEVICEGROUP)
                    .set(DEVICEGROUP.NAME, newName)
                    .where(DEVICEGROUP.NAME.equal(oldName))
                    .execute();

        } catch (DataAccessException e) {
            throw new AlreadyInUseException("The given name is already used by another Group.", e);
        }
    }

    /**
     * Get a list of all UserDevices.
     *
     * @return List of UserDevices.
     */
    public List<UserDevice> getUserDevices() {
        Userdevice u = USERDEVICE.as("u");
        Devicegroup g = DEVICEGROUP.as("g");

        Result<Record3<String, String, String>> users = create.select(u.NAME, g.NAME, u.FINGERPRINT)
                .from(u)
                .join(g).on(u.GROUPID.equal(g._ID))
                .fetch();

        List<UserDevice> userDevices = new LinkedList<>();
        for (Record3<String, String, String> user : users) {
            userDevices.add(new UserDevice(user.get(u.NAME), user.get(g.NAME), new DeviceID(user.get(u.FINGERPRINT))));
        }

        return userDevices;
    }

    /**
     * Change the name of a UserDevice.
     *
     * @param deviceID ID of the device.
     * @param newName  New name of the UserDevice.
     */
    public void changeUserDeviceName(DeviceID deviceID, String newName) throws AlreadyInUseException {
        try {
            create.update(USERDEVICE)
                    .set(USERDEVICE.NAME, newName)
                    .where(USERDEVICE.FINGERPRINT.equal(deviceID.getIDString()))
                    .execute();

        } catch (DataAccessException e) {
            throw new AlreadyInUseException("The given name is already used by another UserDevice.", e);
        }
    }

    /**
     * Add a new UserDevice.
     *
     * @param userDevice The new UserDevice.
     */
    public void addUserDevice(UserDevice userDevice) throws DatabaseControllerException {
        try {
            Userdevice u = USERDEVICE.as("u");
            Integer groupID = getID(DEVICEGROUP.NAME, userDevice.getInGroup());

            create.insertInto(u, u.NAME, u.FINGERPRINT, u.GROUPID)
                    .values(userDevice.getName(), userDevice.getUserDeviceID().getIDString(), groupID)
                    .execute();

        } catch (DataAccessException e) {
            throw new DatabaseControllerException(
                    "Either the given Group does not exist in the database"
                            + " or a UserDevice already has the given name or fingerprint.", e);
        }
    }

    /**
     * Delete a UserDevice.
     *
     * @param userDeviceID ID of the UserDevice.
     */
    public void removeUserDevice(DeviceID userDeviceID) {
        create.deleteFrom(USERDEVICE)
                .where(USERDEVICE.FINGERPRINT.equal(userDeviceID.getIDString()))
                .execute();
    }

    /**
     * Change the template of a Group.
     *
     * @param groupName    Name of the Group.
     * @param templateName Name of the new template.
     */
    public void changeTemplateOfGroup(String groupName, String templateName) throws UnknownReferenceException {
        try {
            Integer templateID = getID(PERMISSIONTEMPLATE.NAME, templateName);

            create.update(DEVICEGROUP)
                    .set(DEVICEGROUP.PERMISSIONTEMPLATEID, templateID)
                    .where(DEVICEGROUP.NAME.equal(groupName))
                    .execute();

        } catch (DataAccessException e) {
            throw new UnknownReferenceException("The given Template does not exist in the database", e);
        }
    }

    /**
     * Change Group membership of a User.
     *
     * @param userDeviceID ID of the UserDevice.
     * @param groupName    Name of the new Group.
     */
    public void changeGroupMembership(DeviceID userDeviceID, String groupName) throws UnknownReferenceException {
        try {
            Integer groupID = getID(DEVICEGROUP.NAME, groupName);

            create.update(USERDEVICE)
                    .set(USERDEVICE.GROUPID, groupID)
                    .where(USERDEVICE.FINGERPRINT.equal(userDeviceID.getIDString()))
                    .execute();

        } catch (DataAccessException e) {
            throw new UnknownReferenceException("The given Group does not exist in the database", e);
        }
    }

    /**
     * Get a single Group by name.
     *
     * @param groupName Name of the Group.
     * @return The requested Group.
     */
    public Group getGroup(String groupName) {

        Permissiontemplate t = PERMISSIONTEMPLATE.as("t");
        Devicegroup g = DEVICEGROUP.as("g");

        Record2<String, String> groupRecord = create.select(g.NAME, t.NAME)
                .from(g)
                .join(t).on(g.PERMISSIONTEMPLATEID.equal(t._ID))
                .where(g.NAME.equal(groupName))
                .fetchOne();

        if (groupRecord != null) {
            return new Group(groupRecord.get(g.NAME), groupRecord.get(t.NAME));
        }

        return null;
    }

    /**
     * Get a UserDevice by DeviceID.
     *
     * @param deviceID DeviceID of the UserDevice.
     * @return The requested UserDevice.
     */
    @Nullable
    public UserDevice getUserDevice(DeviceID deviceID) {
        if (deviceID == null || Strings.isNullOrEmpty(deviceID.getIDString())) {
            return null;
        }

        return getUserDeviceByCondition(USERDEVICE.FINGERPRINT.equal(deviceID.getIDString()));
    }

    /**
     * Get a UserDevice by Name
     *
     * @param name Name of the UserDevice.
     * @return The requested UserDevice.
     */
    @Nullable
    public UserDevice getUserDevice(String name) {
        if (Strings.isNullOrEmpty(name)) {
            return null;
        }

        return getUserDeviceByCondition(USERDEVICE.NAME.equal(name));
    }

    @Nullable
    private UserDevice getUserDeviceByCondition(Condition condition) {
        Userdevice u = USERDEVICE.as("u");
        Devicegroup g = DEVICEGROUP.as("g");

        Record3<String, String, String> userRecord = create.select(u.NAME, u.FINGERPRINT, g.NAME)
                .from(u)
                .join(g).on(u.GROUPID.equal(g._ID))
                .where(condition).fetchOne();

        if (userRecord != null) {
            return new UserDevice(userRecord.get(u.NAME), userRecord.get(g.NAME),
                    new DeviceID(userRecord.get(u.FINGERPRINT)));
        }

        return null;
    }

}