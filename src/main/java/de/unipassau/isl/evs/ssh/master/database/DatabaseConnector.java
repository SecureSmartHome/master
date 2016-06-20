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
import de.unipassau.isl.evs.ssh.core.container.StartupException;
import de.unipassau.isl.evs.ssh.core.sec.Permission;
import de.unipassau.isl.evs.ssh.master.database.generated.DefaultSchema;
import de.unipassau.isl.evs.ssh.master.database.generated.Tables;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.records.ComposedOfPermissionRecord;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.records.PermissionRecord;
import org.jooq.DSLContext;
import org.jooq.DropTableStep;
import org.jooq.InsertValuesStep2;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.util.sqlite.SQLiteDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import static de.unipassau.isl.evs.ssh.master.database.DatabaseContract.Group;
import static de.unipassau.isl.evs.ssh.master.database.DatabaseContract.PermissionTemplate;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.ComposedOfPermission.COMPOSED_OF_PERMISSION;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup.DEVICEGROUP;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Permission.PERMISSION;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate.PERMISSIONTEMPLATE;

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
    private static final String SQL_CREATE_FILENAME = "CreateDB.sql";
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

    private int getVersion() {
        String result = (String) create.resultQuery("PRAGMA user_version;").fetchOne(0);
        return Integer.parseInt(result);
    }

    private void setVersion(int version) {
        create.query("PRAGMA user_version = " + version).execute();
    }

    private void upgrade() {
        dropAllTables();
        create();
    }

    private void create() {
        StringBuilder sb = new StringBuilder();
        try {
            for (String s : Files.readAllLines(Paths.get(SQL_CREATE_FILENAME), Charset.defaultCharset())) {
                sb.append(s);
            }
        } catch (IOException e) {
            throw new StartupException(e);
        }
        create.query(sb.toString());
        fill();
    }

    private void fill() {
        fillPermissions();
        fillGroupsAndTemplates();
        fillTemplates();
    }

    private void fillPermissions() {
        List<InsertValuesStep2<PermissionRecord, Integer, String>> queries = new LinkedList<>();
        for (Permission permission : Permission.binaryPermissions) {
            queries.add(create.insertInto(PERMISSION, PERMISSION._ID, PERMISSION.NAME)
                    .values(permission.ordinal(), permission.toString()));
        }
        create.batch(queries).execute();
    }

    private void fillGroupsAndTemplates() {
        final PermissionTemplate.DefaultValues[] templates = PermissionTemplate.DefaultValues.values();
        final Group.DefaultValues[] groups = Group.DefaultValues.values();

        for (int i = 0; i < templates.length; i++) {
            create.insertInto(PERMISSIONTEMPLATE, PERMISSIONTEMPLATE._ID, PERMISSIONTEMPLATE.NAME)
                    .values(i, templates[i].toString()).execute();

            if (i < groups.length) {
                create.insertInto(DEVICEGROUP, DEVICEGROUP._ID, DEVICEGROUP.NAME, DEVICEGROUP.PERMISSIONTEMPLATEID)
                        .values(i, groups[i].toString(), i).execute();
            }
        }
    }

    private void fillTemplates() {
        final int parentsTemplateID = 1;
        List<InsertValuesStep2<ComposedOfPermissionRecord, Integer, Integer>> fillParentTemplate = new LinkedList<>();
        for (Permission permission : Permission.binaryPermissions) {
            fillParentTemplate.add(create.insertInto(COMPOSED_OF_PERMISSION,
                    COMPOSED_OF_PERMISSION.PERMISSIONID, COMPOSED_OF_PERMISSION.PERMISSIONTEMPLATEID)
                    .values(permission.ordinal(), parentsTemplateID));
        }
        create.batch(fillParentTemplate).execute();

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
        LinkedList<InsertValuesStep2<ComposedOfPermissionRecord, Integer, Integer>> fillChildTemplate = new LinkedList<>();
        for (int permission : childrenPermissionsIDs) {
            fillChildTemplate.add(create.insertInto(COMPOSED_OF_PERMISSION,
                    COMPOSED_OF_PERMISSION.PERMISSIONID, COMPOSED_OF_PERMISSION.PERMISSIONTEMPLATEID)
                    .values(permission, childrenTemplateID));
        }
        create.batch(fillChildTemplate).execute();

        final int guestsTemplateID = 3;
        LinkedList<InsertValuesStep2<ComposedOfPermissionRecord, Integer, Integer>> fillGuestTemplate = new LinkedList<>();
        for (int permission : guestPermissionIDs) {
            fillGuestTemplate.add(create.insertInto(COMPOSED_OF_PERMISSION,
                    COMPOSED_OF_PERMISSION.PERMISSIONID, COMPOSED_OF_PERMISSION.PERMISSIONTEMPLATEID)
                    .values(permission, guestsTemplateID));
        }
        create.batch(fillGuestTemplate).execute();
    }

    private void dropAllTables(){
        LinkedList<DropTableStep> dropTables = new LinkedList<>();
        for (Table<?> table : DefaultSchema.DEFAULT_SCHEMA.getTables()) {
             dropTables.add(create.dropTable(table));
        }
        create.batch(dropTables).execute();
    }
}