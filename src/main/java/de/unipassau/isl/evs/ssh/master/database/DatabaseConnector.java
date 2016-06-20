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

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.container.ContainerService;
import de.unipassau.isl.evs.ssh.core.container.StartupException;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.GPIOAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.MockAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.USBAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.WLANAccessPoint;
import de.unipassau.isl.evs.ssh.core.sec.Permission;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.types.Interval;
import org.jooq.util.sqlite.SQLiteDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static de.unipassau.isl.evs.ssh.master.database.DatabaseContract.Group;
import static de.unipassau.isl.evs.ssh.master.database.DatabaseContract.PermissionTemplate;

/**
 * The DatabaseConnector allows to establish connections to the used database and execute operations on it.
 *
 * @author Wolfgang Popp
 */
public class DatabaseConnector extends AbstractComponent {
    public static final Key<DatabaseConnector> KEY = new Key<>(DatabaseConnector.class);
    private static final String DATABASE_NAME = "SecureSmartHome.db";
    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 3;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Connection connection;

    final DSLContext create;

    public DatabaseConnector() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_NAME);
        } catch (SQLException e) {
            throw new StartupException(e);
        }
        create = DSL.using(connection, SQLDialect.SQLITE);
        create.query("PRAGMA foreign_keys = ON;").execute();
    }

    @Override
    public void init(Container container) {
        super.init(container);
        if (connection == null || create == null) {
            throw new StartupException();
        }

        int dbVersion = getVersion();

        if (dbVersion == 0) {
            create();
            setVersion(DATABASE_VERSION);
        } else if (dbVersion < DATABASE_VERSION) {
            upgrade();
            setVersion(DATABASE_VERSION);
        }
    }

    @Override
    public void destroy() {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.error("Could not close database");
        }
        super.destroy();
    }

    private int getVersion(){
        String result = (String) create.resultQuery("PRAGMA user_version;").fetchOne(0);
        return Integer.parseInt(result);
    }

    private void setVersion(int version){
        create.query("PRAGMA user_version = " + version);
    }

    private void upgrade(){

    }

    private void create(){

    }

    private class DBOpenHelper extends SQLiteOpenHelper {
        private static final String SQL_CREATE_DB = "CREATE TABLE " + DatabaseContract.UserDevice.TABLE_NAME + " ("
                + DatabaseContract.UserDevice.COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY,"
                + DatabaseContract.UserDevice.COLUMN_NAME + " VARCHAR NOT NULL UNIQUE,"
                + DatabaseContract.UserDevice.COLUMN_FINGERPRINT + " VARCHAR NOT NULL UNIQUE,"
                + DatabaseContract.UserDevice.COLUMN_GROUP_ID + " INTEGER NOT NULL,"
                + "FOREIGN KEY(" + DatabaseContract.UserDevice.COLUMN_GROUP_ID + ") REFERENCES " + Group.TABLE_NAME + "(" + Group.COLUMN_ID + ")"
                + ");"

                + "CREATE TABLE " + DatabaseContract.Permission.TABLE_NAME + " ("
                + DatabaseContract.Permission.COLUMN_ID + " INTEGER NOT NULL PRIMARY KEY,"
                + DatabaseContract.Permission.COLUMN_NAME + " VARCHAR NOT NULL,"
                + DatabaseContract.Permission.COLUMN_ELECTRONIC_MODULE_ID + " INTEGER,"
                + "UNIQUE(" + DatabaseContract.Permission.COLUMN_NAME + ", " + DatabaseContract.Permission.COLUMN_ELECTRONIC_MODULE_ID + "),"
                + "FOREIGN KEY(" + DatabaseContract.Permission.COLUMN_ELECTRONIC_MODULE_ID + ") REFERENCES " + DatabaseContract.ElectronicModule.TABLE_NAME + "(" + DatabaseContract.ElectronicModule.COLUMN_ID + ") ON DELETE CASCADE"
                + ");"

                + "CREATE TABLE " + DatabaseContract.HasPermission.TABLE_NAME + " ("
                + DatabaseContract.HasPermission.COLUMN_PERMISSION_ID + " INTEGER NOT NULL,"
                + DatabaseContract.HasPermission.COLUMN_USER_ID + " INTEGER NOT NULL,"
                + "PRIMARY KEY (" + DatabaseContract.HasPermission.COLUMN_PERMISSION_ID + ", " + DatabaseContract.HasPermission.COLUMN_USER_ID + "),"
                + "FOREIGN KEY(" + DatabaseContract.HasPermission.COLUMN_USER_ID + ") REFERENCES " + DatabaseContract.UserDevice.TABLE_NAME + "(" + DatabaseContract.UserDevice.COLUMN_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY(" + DatabaseContract.HasPermission.COLUMN_PERMISSION_ID + ") REFERENCES " + DatabaseContract.Permission.TABLE_NAME + "(" + DatabaseContract.Permission.COLUMN_ID + ") ON DELETE CASCADE"
                + ");"

                + "CREATE TABLE " + Group.TABLE_NAME + " ("
                + Group.COLUMN_ID + "  INTEGER NOT NULL PRIMARY KEY,"
                + Group.COLUMN_NAME + " VARCHAR NOT NULL UNIQUE,"
                + Group.COLUMN_PERMISSION_TEMPLATE_ID + " INTEGER NOT NULL,"
                + "FOREIGN KEY(" + Group.COLUMN_PERMISSION_TEMPLATE_ID + ") REFERENCES " + PermissionTemplate.TABLE_NAME + "(" + PermissionTemplate.COLUMN_ID + ")"
                + ");"

                + "CREATE TABLE " + PermissionTemplate.TABLE_NAME + " ("
                + PermissionTemplate.COLUMN_ID + "  INTEGER NOT NULL PRIMARY KEY,"
                + PermissionTemplate.COLUMN_NAME + " VARCHAR NOT NULL UNIQUE"
                + ");"

                + "CREATE TABLE " + DatabaseContract.ComposedOfPermission.TABLE_NAME + " ("
                + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID + " INTEGER NOT NULL,"
                + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID + " INTEGER NOT NULL,"
                + "PRIMARY KEY (" + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID + "," + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID + "),"
                + "FOREIGN KEY(" + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID + ") REFERENCES " + PermissionTemplate.TABLE_NAME + "(" + PermissionTemplate.COLUMN_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY(" + DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID + ") REFERENCES " + DatabaseContract.Permission.TABLE_NAME + "(" + DatabaseContract.Permission.COLUMN_ID + ") ON DELETE CASCADE"
                + ");"

                + "CREATE TABLE " + DatabaseContract.ElectronicModule.TABLE_NAME + " ("
                + DatabaseContract.ElectronicModule.COLUMN_ID + "  INTEGER NOT NULL PRIMARY KEY,"
                + DatabaseContract.ElectronicModule.COLUMN_SLAVE_ID + " INTEGER NOT NULL,"
                + DatabaseContract.ElectronicModule.COLUMN_NAME + " VARCHAR NOT NULL UNIQUE,"
                + DatabaseContract.ElectronicModule.COLUMN_GPIO_PIN + " INTEGER,"
                + DatabaseContract.ElectronicModule.COLUMN_USB_PORT + " INTEGER,"
                + DatabaseContract.ElectronicModule.COLUMN_WLAN_PORT + " INTEGER,"
                + DatabaseContract.ElectronicModule.COLUMN_WLAN_USERNAME + " VARCHAR,"
                + DatabaseContract.ElectronicModule.COLUMN_WLAN_PASSWORD + " VARCHAR,"
                + DatabaseContract.ElectronicModule.COLUMN_WLAN_IP + " VARCHAR,"
                + DatabaseContract.ElectronicModule.COLUMN_MODULE_TYPE + " VARCHAR NOT NULL,"
                + DatabaseContract.ElectronicModule.COLUMN_CONNECTOR_TYPE + " VARCHAR CHECK("
                + DatabaseContract.ElectronicModule.COLUMN_CONNECTOR_TYPE + " = '" + MockAccessPoint.TYPE + "' or "
                + DatabaseContract.ElectronicModule.COLUMN_CONNECTOR_TYPE + " = '" + GPIOAccessPoint.TYPE + "' or "
                + DatabaseContract.ElectronicModule.COLUMN_CONNECTOR_TYPE + " = '" + USBAccessPoint.TYPE + "' or "
                + DatabaseContract.ElectronicModule.COLUMN_CONNECTOR_TYPE + " = '" + WLANAccessPoint.TYPE + "'),"
                + "FOREIGN KEY(" + DatabaseContract.ElectronicModule.COLUMN_SLAVE_ID + ") REFERENCES " + DatabaseContract.Slave.TABLE_NAME + "(" + DatabaseContract.Slave.COLUMN_ID + ")"
                + ");"

                + "CREATE TABLE " + DatabaseContract.Slave.TABLE_NAME + " ("
                + DatabaseContract.Slave.COLUMN_ID + "  INTEGER NOT NULL PRIMARY KEY,"
                + DatabaseContract.Slave.COLUMN_NAME + " VARCHAR NOT NULL UNIQUE,"
                + DatabaseContract.Slave.COLUMN_FINGERPRINT + " VARCHAR NOT NULL UNIQUE"
                + ");"

                + "CREATE TABLE " + DatabaseContract.HolidayLog.TABLE_NAME + " ("
                + DatabaseContract.HolidayLog.COLUMN_ID + "  INTEGER NOT NULL PRIMARY KEY,"
                + DatabaseContract.HolidayLog.COLUMN_ELECTRONIC_MODULE_ID + " INTEGER,"
                + DatabaseContract.HolidayLog.COLUMN_ACTION + " VARCHAR NOT NULL,"
                + DatabaseContract.HolidayLog.COLUMN_TIMESTAMP + " INTEGER NOT NULL,"
                + "FOREIGN KEY(" + DatabaseContract.HolidayLog.COLUMN_ELECTRONIC_MODULE_ID + ") REFERENCES " + DatabaseContract.ElectronicModule.TABLE_NAME + "(" + DatabaseContract.ElectronicModule.COLUMN_ID + ") ON DELETE CASCADE"
                + ");";

        private static final String SQL_DROP_TABLES = "DROP TABLE " + DatabaseContract.HasPermission.TABLE_NAME + ";"
                + "DROP TABLE " + DatabaseContract.ComposedOfPermission.TABLE_NAME + ";"
                + "DROP TABLE " + DatabaseContract.UserDevice.TABLE_NAME + ";"
                + "DROP TABLE " + DatabaseContract.Permission.TABLE_NAME + ";"
                + "DROP TABLE " + DatabaseContract.ElectronicModule.TABLE_NAME + ";"
                + "DROP TABLE " + Group.TABLE_NAME + ";"
                + "DROP TABLE " + DatabaseContract.Slave.TABLE_NAME + ";"
                + "DROP TABLE " + PermissionTemplate.TABLE_NAME + ";"
                + "DROP TABLE " + DatabaseContract.HolidayLog.TABLE_NAME + ";";

        private DBOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        private void insertPermissions(SQLiteDatabase db) {
            for (Permission permission : Permission.binaryPermissions) {
                ContentValues values = new ContentValues(2);
                values.put(DatabaseContract.Permission.COLUMN_NAME, permission.toString());
                values.put(DatabaseContract.Permission.COLUMN_ID, permission.ordinal());
                db.insert(DatabaseContract.Permission.TABLE_NAME, null, values);
            }
        }

        private void insertGroupsAndTemplates(SQLiteDatabase db) {
            final PermissionTemplate.DefaultValues[] templates = PermissionTemplate.DefaultValues.values();
            final Group.DefaultValues[] groups = Group.DefaultValues.values();

            for (int i = 0; i < templates.length; i++) {
                ContentValues template = new ContentValues(1);
                template.put(PermissionTemplate.COLUMN_NAME, templates[i].toString());
                long id = db.insert(PermissionTemplate.TABLE_NAME, null, template);

                if (i < groups.length) {
                    ContentValues group = new ContentValues(2);
                    group.put(Group.COLUMN_NAME, groups[i].toString());
                    group.put(Group.COLUMN_PERMISSION_TEMPLATE_ID, id);
                    db.insert(Group.TABLE_NAME, null, group);
                }
            }
        }

        private void fillTemplates(SQLiteDatabase db) {
            final int parentsTemplateID = 1;
            for (Permission permission : Permission.binaryPermissions) {
                ContentValues values = new ContentValues(2);
                values.put(DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID, permission.ordinal());
                values.put(DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID, parentsTemplateID);
                db.insert(DatabaseContract.ComposedOfPermission.TABLE_NAME, null, values);
                create.insertInto(DatabaseContract.ComposedOfPermission.TABLE_NAME)
            }

            int[] childrenPermissionsIDs = new int[]{
                    Permission.REQUEST_LIGHT_STATUS.ordinal(),
                    Permission.REQUEST_WINDOW_STATUS.ordinal(),
                    Permission.REQUEST_DOOR_STATUS.ordinal(),
                    Permission.UNLATCH_DOOR.ordinal(),
                    Permission.REQUEST_CAMERA_STATUS.ordinal(),
                    Permission.TAKE_CAMERA_PICTURE.ordinal(),
                    Permission.REQUEST_WEATHER_STATUS.ordinal(),
                    Permission.HUMIDITY_WARNING.ordinal(),
                    Permission.BRIGHTNESS_WARNING.ordinal(),
                    Permission.BELL_RANG.ordinal(),
                    Permission.WEATHER_WARNING.ordinal(),
                    Permission.DOOR_UNLATCHED.ordinal(),
                    Permission.DOOR_LOCKED.ordinal(),
                    Permission.DOOR_UNLOCKED.ordinal(),
                    Permission.SWITCH_LIGHT_EXTERN.ordinal()
            };

            int[] guestPermissionIDs = new int[]{
                    Permission.REQUEST_LIGHT_STATUS.ordinal(),
                    Permission.BRIGHTNESS_WARNING.ordinal()
            };

            final int childrenTemplateID = 2;
            for (int permission : childrenPermissionsIDs) {
                ContentValues values = new ContentValues(2);
                values.put(DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID, permission);
                values.put(DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID, childrenTemplateID);
                db.insert(DatabaseContract.ComposedOfPermission.TABLE_NAME, null, values);

            }

            final int guestsTemplateID = 3;
            for (int permission : guestPermissionIDs) {
                ContentValues values = new ContentValues(2);
                values.put(DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_ID, permission);
                values.put(DatabaseContract.ComposedOfPermission.COLUMN_PERMISSION_TEMPLATE_ID, guestsTemplateID);
                db.insert(DatabaseContract.ComposedOfPermission.TABLE_NAME, null, values);

            }
        }

        private void insertDefaults(SQLiteDatabase db) {
            insertPermissions(db);
            insertGroupsAndTemplates(db);
            fillTemplates(db);
        }

        private void execSQLScript(String script, SQLiteDatabase db) {
            String[] statements = script.split(";");
            for (String statement : statements) {
                //Log.v(TAG, "executing SQL statement: " + statement + ";");
                db.execSQL(statement + ";");
            }
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.v(TAG, "creating Database");
            execSQLScript(SQL_CREATE_DB, db);
            insertDefaults(db);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            db.setForeignKeyConstraintsEnabled(true);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Drops all tables and creates them again.
            Log.v(TAG, "updating Database");
            execSQLScript(SQL_DROP_TABLES, db);
            onCreate(db);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }
}