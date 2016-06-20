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
import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.AccessLogger;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.database.AlreadyInUseException;
import de.unipassau.isl.evs.ssh.core.database.DatabaseControllerException;
import de.unipassau.isl.evs.ssh.core.database.IsReferencedException;
import de.unipassau.isl.evs.ssh.core.database.UnknownReferenceException;
import de.unipassau.isl.evs.ssh.core.database.dto.PermissionDTO;
import de.unipassau.isl.evs.ssh.core.database.dto.UserDevice;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.ComposedOfPermission;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.HasPermission;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Permission;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;

import java.util.List;

import static de.unipassau.isl.evs.ssh.master.database.generated.tables.ComposedOfPermission.COMPOSED_OF_PERMISSION;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup.DEVICEGROUP;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.HasPermission.HAS_PERMISSION;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Permission.PERMISSION;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate.PERMISSIONTEMPLATE;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice.USERDEVICE;

/**
 * Offers high level methods to interact with the tables associated with permissions in the database.
 * and simply take ({@link de.unipassau.isl.evs.ssh.core.sec.Permission} permission, {@link String} moduleName) instead (Niko, 2015-12-20)
 *
 * @author Leon Sell
 */
public class PermissionController extends AbstractComponent {
    public static final Key<PermissionController> KEY = new Key<>(PermissionController.class);
    private DSLContext create;

    @Override
    public void init(Container container) {
        super.init(container);
        create = requireComponent(DatabaseConnector.KEY).create;
    }

    @Override
    public void destroy() {
        create = null;
        super.destroy();
    }

    /**
     * Lists all Permissions of a given template.
     *
     * @param templateName Name of the template.
     * @return List of the Permissions in the template.
     */
    public List<PermissionDTO> getPermissionsOfTemplate(String templateName) {
        Permission p = PERMISSION.as("p");
        Permissiontemplate pt = PERMISSIONTEMPLATE.as("pt");
        Electronicmodule m = ELECTRONICMODULE.as("m");
        ComposedOfPermission comp = COMPOSED_OF_PERMISSION.as("comp");

        return create.select(p.NAME, m.NAME)
                .from(comp)
                .join(pt).on(comp.PERMISSIONTEMPLATEID.equal(pt._ID))
                .join(p).on(comp.PERMISSIONID.equal(p._ID))
                .leftJoin(m).on(p.ELECTRONICMODULEID.equal(m._ID))
                .where(pt.NAME.eq(templateName)).fetchInto(PermissionDTO.class);
    }

    /**
     * Returns whether a given user has a given Permission.
     *
     * @param userDeviceID DeviceID associated with the user.
     * @param permission   Permission to check.
     * @param moduleName   Module the permission applies for or null if the given permission is a binary permission.
     * @return true if has permissions otherwise false.
     */
    public boolean hasPermission(DeviceID userDeviceID, de.unipassau.isl.evs.ssh.core.sec.Permission permission, String moduleName) {
        final AccessLogger logger = getComponent(AccessLogger.KEY);
        if (logger != null) {
            logger.logAccess(permission);
        }

        Permission p = PERMISSION.as("p");
        HasPermission hp = HAS_PERMISSION.as("hp");
        Userdevice ud = USERDEVICE.as("ud");
        Electronicmodule m = ELECTRONICMODULE.as("m");

        return create.select()
                .from(hp)
                .join(p).on(hp.PERMISSIONID.equal(p._ID))
                .join(ud).on(hp.USERID.equal(ud._ID))
                .leftJoin(m).on(p.ELECTRONICMODULEID.equal(m._ID))
                .where(p.NAME.equal(permission.toString()))
                .and(m.NAME.isNull().or(m.NAME.equal(moduleName)))
                .and(ud.FINGERPRINT.eq(userDeviceID.getIDString()))
                .fetch().isNotEmpty();
    }

    /**
     * Removes a template from the database.
     *
     * @param templateName Name of the template.
     */
    public void removeTemplate(String templateName) throws IsReferencedException {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        try {
            databaseConnector.executeSql("delete from "
                    + DatabaseContract.PermissionTemplate.TABLE_NAME
                    + " where " + DatabaseContract.PermissionTemplate.COLUMN_NAME
                    + " = ?", new String[]{templateName});
        } catch (SQLiteConstraintException sqlce) {
            throw new IsReferencedException("This template is used by at least one Group", sqlce);
        }
        */
    }

    /**
     * Add a Permission to a Template.
     *
     * @param templateName Name of the Template.
     * @param permission   Permission to add.
     * @param moduleName   Module the permission applies for.
     */
    public void addPermissionToTemplate(String templateName, de.unipassau.isl.evs.ssh.core.sec.Permission permission, String moduleName) throws UnknownReferenceException {

        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        try {
            if (Strings.isNullOrEmpty(moduleName)) {
                databaseConnector.executeSql("insert into "
                                + DatabaseContract.ComposedOfPermission.TABLE_NAME
                                + " (" + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID
                                + ", " + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID
                                + ") values ((" + DatabaseContract.SqlQueries.PERMISSION_ID_FROM_NAME_WITHOUT_MODULE_SQL_QUERY
                                + "), (" + DatabaseContract.SqlQueries.TEMPLATE_ID_FROM_NAME_SQL_QUERY + "))",
                        new String[]{permission.toString(), templateName}
                );
            } else {
                databaseConnector.executeSql("insert into "
                                + DatabaseContract.ComposedOfPermission.TABLE_NAME
                                + " (" + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID
                                + ", " + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID
                                + ") values ((" + DatabaseContract.SqlQueries.PERMISSION_ID_FROM_NAME_AND_MODULE_SQL_QUERY
                                + "), (" + DatabaseContract.SqlQueries.TEMPLATE_ID_FROM_NAME_SQL_QUERY + "))",
                        new String[]{permission.toString(), moduleName, templateName}
                );
            }
        } catch (SQLiteConstraintException sqlce) {
            throw new UnknownReferenceException("The given Template or PermissionDTO does not exist in the database",
                    sqlce);
        }
        */
    }

    /**
     * Remove a Permission from a Template.
     *
     * @param templateName Name of the Template.
     * @param permission   Permission to remove.
     * @param moduleName   Module the permission applies for.
     */
    public void removePermissionFromTemplate(String templateName, de.unipassau.isl.evs.ssh.core.sec.Permission permission, String moduleName) {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        if (Strings.isNullOrEmpty(moduleName)) {
            databaseConnector.executeSql("delete from "
                            + DatabaseContract.ComposedOfPermission.TABLE_NAME
                            + " where " + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID
                            + " = (" + DatabaseContract.SqlQueries.PERMISSION_ID_FROM_NAME_WITHOUT_MODULE_SQL_QUERY + ") and "
                            + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID
                            + " = (" + DatabaseContract.SqlQueries.TEMPLATE_ID_FROM_NAME_SQL_QUERY + ")",
                    new String[]{permission.toString(), templateName}
            );
        } else {
            databaseConnector.executeSql("delete from "
                            + DatabaseContract.ComposedOfPermission.TABLE_NAME
                            + " where " + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID
                            + " = (" + DatabaseContract.SqlQueries.PERMISSION_ID_FROM_NAME_AND_MODULE_SQL_QUERY + ") and "
                            + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID
                            + " = (" + DatabaseContract.SqlQueries.TEMPLATE_ID_FROM_NAME_SQL_QUERY + ")",
                    new String[]{permission.toString(), moduleName, templateName}
            );
        }
        */
    }

    /**
     * Add a Permission for a UserDevice.
     *
     * @param userDeviceID DeviceID of the UserDevice.
     * @param permission   Permission to add.
     * @param moduleName   Module the permission applies for.
     */
    public void addUserPermission(DeviceID userDeviceID, de.unipassau.isl.evs.ssh.core.sec.Permission permission,
                                  String moduleName) throws UnknownReferenceException {
        try {
            Integer userID = getUserID(userDeviceID);
            Integer permissionID = getPermissionID(permission.toString(), moduleName);

            if (userID == null) {
                throw new UnknownReferenceException("The given DeviceID does not exist!");
            }

            if (permissionID == null) {
                throw new UnknownReferenceException("The given permission does not correspond to the given module!");
            }

            create.insertInto(HAS_PERMISSION, HAS_PERMISSION.PERMISSIONID, HAS_PERMISSION.USERID)
                    .values(permissionID, userID).execute();

        } catch (DataAccessException e) {
            throw new UnknownReferenceException(
                    "The given UserDevice or Permission does not exist in the database", e);
        }
    }

    private Integer getUserID(DeviceID id) {
        Record1<Integer> result = create.select(USERDEVICE._ID)
                .from(USERDEVICE)
                .where(USERDEVICE.FINGERPRINT.equal(id.getIDString())).fetchOne();

        if (result != null) {
            return result.value1();
        }

        return null;

    }

    private Integer getPermissionID(String permission, String moduleName) {
        Electronicmodule m = ELECTRONICMODULE.as("m");
        Permission p = PERMISSION.as("p");

        Record1<Integer> result = create.select(p._ID)
                .from(p)
                .leftJoin(m).on(p.ELECTRONICMODULEID.equal(m._ID))
                .where(p.NAME.equal(permission)
                        .and(m.NAME.isNull().or(m.NAME.equal(moduleName))))
                .fetchOne();

        if (result != null) {
            return result.value1();
        }

        return null;
    }

    /**
     * Remove a Permission for a UserDevice.
     *
     * @param userDeviceID DeviceID of the UserDevice.
     * @param permission   Permission to remove.
     * @param moduleName   Module the permission applies for.
     */
    public void removeUserPermission(DeviceID userDeviceID, de.unipassau.isl.evs.ssh.core.sec.Permission permission,
                                     String moduleName) {

        Integer userID = getUserID(userDeviceID);
        Integer permissionID = getPermissionID(permission.toString(), moduleName);

        create.deleteFrom(HAS_PERMISSION)
                .where(HAS_PERMISSION.PERMISSIONID.equal(permissionID)
                        .and(HAS_PERMISSION.USERID.equal(userID)))
                .execute();
    }


    /**
     * Adds a new template to the database.
     *
     * @param templateName Name of the template.
     */
    public void addTemplate(String templateName) throws AlreadyInUseException {

        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        try {
            databaseConnector.executeSql("insert into "
                    + DatabaseContract.PermissionTemplate.TABLE_NAME
                    + " (" + DatabaseContract.PermissionTemplate.COLUMN_NAME + ")"
                    + "values (?)", new String[]{templateName});
        } catch (SQLiteConstraintException sqlce) {
            throw new AlreadyInUseException("The name is already used by another Template.", sqlce);
        }
        */
    }

    /**
     * Adds a new Permission to the database.
     *
     * @param permission Permission to add.
     * @param moduleName Module the permission applies for.
     */
    public void addPermission(de.unipassau.isl.evs.ssh.core.sec.Permission permission,
                              String moduleName) throws DatabaseControllerException {
        try {
            Integer moduleID = null;

            if (Strings.isNullOrEmpty(moduleName)) {
                moduleID = create.select(ELECTRONICMODULE._ID)
                        .from(ELECTRONICMODULE)
                        .where(ELECTRONICMODULE.NAME.equal(moduleName))
                        .fetchOne().value1();
            }

            create.insertInto(PERMISSION, PERMISSION.NAME, PERMISSION.ELECTRONICMODULEID)
                    .values(permission.toString(), moduleID);

        } catch (DataAccessException e) {
            throw new DatabaseControllerException("Either the name-module combination is already exists in the database"
                    + " or the given module doesn't exist.", e);
        }
    }

    /**
     * Removes a Permission from the database.
     *
     * @param permission Permission to remove.
     * @param moduleName Module the permission applies for.
     */
    public void removePermission(de.unipassau.isl.evs.ssh.core.sec.Permission permission, String moduleName) {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        if (Strings.isNullOrEmpty(moduleName)) {
            databaseConnector.executeSql("delete from "
                            + DatabaseContract.Permission.TABLE_NAME
                            + " where " + DatabaseContract.Permission.COLUMN_NAME
                            + " = ? and " + DatabaseContract.Permission.COLUMN_ELECTRONIC_MODULE_ID + " is NULL",
                    new String[]{permission.toString()}
            );
        } else {
            databaseConnector.executeSql("delete from "
                            + DatabaseContract.Permission.TABLE_NAME
                            + " where " + DatabaseContract.Permission.COLUMN_NAME
                            + " = ? and " + DatabaseContract.Permission.COLUMN_ELECTRONIC_MODULE_ID
                            + " = (" + DatabaseContract.SqlQueries.MODULE_ID_FROM_NAME_SQL_QUERY + ")",
                    new String[]{permission.toString(), moduleName}
            );
        }
        */
    }

    /**
     * Get the names of all Permissions.
     *
     * @return All names as a list.
     */
    public List<PermissionDTO> getPermissions() {

        Permission p = PERMISSION.as("p");
        Electronicmodule m = ELECTRONICMODULE.as("m");

        return create.select(p.NAME, m.NAME)
                .from(p)
                .leftJoin(m).on(p.ELECTRONICMODULEID.equal(m._ID))
                .fetchInto(PermissionDTO.class);
    }

    /**
     * Get the name of all Templates.
     *
     * @return All names as a list.
     */
    public List<String> getTemplates() {
        return create.select()
                .from(PERMISSIONTEMPLATE)
                .fetch(PERMISSIONTEMPLATE.NAME);
    }

    /**
     * Change the name of a Template.
     *
     * @param oldName Old name of the Template.
     * @param newName New name of the Template.
     * @throws AlreadyInUseException
     */
    public void changeTemplateName(String oldName, String newName) throws AlreadyInUseException {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        try {
            databaseConnector.executeSql("update " + DatabaseContract.PermissionTemplate.TABLE_NAME
                            + " set " + DatabaseContract.PermissionTemplate.COLUMN_NAME
                            + " = ? where " + DatabaseContract.PermissionTemplate.COLUMN_NAME + " = ?",
                    new String[]{newName, oldName});
        } catch (SQLiteConstraintException sqlce) {
            throw new AlreadyInUseException("The given name is already used by another Template.", sqlce);
        }
        */
    }

    /**
     * Returns all UserDevices that have a given permission.
     *
     * @param permission Permission to check for.
     * @param moduleName Module the permission applies for.
     * @return List of the UserDevices.
     */
    public List<UserDevice> getAllUserDevicesWithPermission(de.unipassau.isl.evs.ssh.core.sec.Permission permission, String moduleName) {
        Permission p = PERMISSION.as("p");
        Userdevice u = USERDEVICE.as("u");
        Devicegroup g = DEVICEGROUP.as("g");
        Electronicmodule m = ELECTRONICMODULE.as("m");
        HasPermission hp = HAS_PERMISSION.as("hp");

        return create.select(u.NAME, u.FINGERPRINT, g.NAME)
                .from(hp)
                .join(p).on(hp.PERMISSIONID.equal(p._ID))
                .join(u).on(hp.USERID.equal(u._ID))
                .join(g).on(u.GROUPID.equal(g._ID))
                .leftJoin(m).on(p.ELECTRONICMODULEID.equal(m._ID))
                .where(p.NAME.equal(permission.toString())
                        .and(m.NAME.isNull().or(m.NAME.equal(moduleName))))
                .fetchInto(UserDevice.class);
    }

    /**
     * Get all permissions that a given user device has.
     *
     * @param userDeviceID The UserDevice which has the Permissions which will be returned.
     * @return List of all Permissions that the given UserDevice has.
     */
    public List<PermissionDTO> getPermissionsOfUserDevice(DeviceID userDeviceID) {
        Permission p = PERMISSION.as("p");
        Userdevice u = USERDEVICE.as("u");
        Electronicmodule m = ELECTRONICMODULE.as("m");
        HasPermission hp = HAS_PERMISSION.as("hp");

        return create.select(p.NAME, m.NAME)
                .from(hp)
                .join(p).on(hp.PERMISSIONID.equal(p._ID))
                .join(u).on(hp.USERID.equal(u._ID))
                .leftJoin(m).on(p.ELECTRONICMODULEID.equal(m._ID))
                .where(u.FINGERPRINT.eq(userDeviceID.getIDString()))
                .fetchInto(PermissionDTO.class);
    }
}