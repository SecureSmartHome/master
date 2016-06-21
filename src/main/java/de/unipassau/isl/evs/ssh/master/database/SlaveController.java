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
import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.database.AlreadyInUseException;
import de.unipassau.isl.evs.ssh.core.database.DatabaseControllerException;
import de.unipassau.isl.evs.ssh.core.database.IsReferencedException;
import de.unipassau.isl.evs.ssh.core.database.dto.Module;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.GPIOAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.MockAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.ModuleAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.USBAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.ModuleAccessPoint.WLANAccessPoint;
import de.unipassau.isl.evs.ssh.core.database.dto.Slave;
import de.unipassau.isl.evs.ssh.core.naming.DeviceID;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule;
import org.jetbrains.annotations.NotNull;
import org.jooq.Record1;
import org.jooq.Record10;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.exception.DataAccessException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave.SLAVE;

/**
 * Offers high level methods to interact with the tables associated with slaves and modules in the database.
 *
 * @author Leon Sell
 */
public class SlaveController extends AbstractController {
    public static final Key<SlaveController> KEY = new Key<>(SlaveController.class);
    private final Map<DeviceID, byte[]> passiveRegistrationTokens = new HashMap<>();


    private final Electronicmodule m = ELECTRONICMODULE.as("m");

    // Common <code>SELECT ... FROM ElectronicModule</code> header.
    private final SelectJoinStep<Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String>> moduleSelect = create.select(
            m.SLAVEID,
            m.NAME,
            m.GPIOPIN,
            m.USBPORT,
            m.WLANIP,
            m.WLANPORT,
            m.WLANUSERNAME,
            m.WLANPASSWORD,
            m.MODULETYPE,
            m.TYPE)
            .from(m);

    /**
     * Add a new Module.
     *
     * @param module Module to add.
     */
    public void addModule(Module module) throws DatabaseControllerException {
        try {
            DBModule dbModule = new DBModule().initFromModule(module);
            create.insertInto(m,
                    m.SLAVEID,
                    m.NAME,
                    m.GPIOPIN,
                    m.USBPORT,
                    m.WLANIP,
                    m.WLANPORT,
                    m.WLANUSERNAME,
                    m.WLANPASSWORD,
                    m.MODULETYPE,
                    m.TYPE)
                    .values(dbModule.getSlaveID(),
                            dbModule.getName(),
                            dbModule.getGpioPort(),
                            dbModule.getUsbPort(),
                            dbModule.getWlanIP(),
                            dbModule.getWlanPort(),
                            dbModule.getWlanUsername(),
                            dbModule.getWlanPassword(),
                            dbModule.getModuleType(),
                            dbModule.getType())
                    .execute();

        } catch (DataAccessException e) {
            throw new DatabaseControllerException("The given Slave does not exist in the database"
                    + " or the name is already used by another Module", e);
        }
    }

    /**
     * Remove a Module.
     *
     * @param moduleName Name of the Module to remove.
     */
    public void removeModule(String moduleName) {
        create.deleteFrom(m)
                .where(m.NAME.equal(moduleName))
                .execute();
    }

    /**
     * Get all information of a single Module by name.
     *
     * @param moduleName The name of the Module.
     * @return The requested Module.
     */
    public Module getModule(String moduleName) {
        Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String> moduleRecord
                = moduleSelect.where(m.NAME.equal(moduleName)).fetchOne();

        if (moduleRecord != null) {
            return new DBModule().initFromRecord(moduleRecord).toModule();
        }

        return null;
    }

    /**
     * Get all Modules of a given type.
     *
     * @param type Type of the Modules to get.
     * @return List of all Modules with given type.
     */
    @NotNull
    public List<Module> getModulesByType(CoreConstants.ModuleType type) {
        Result<Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String>> result =
                moduleSelect.where(m.MODULETYPE.eq(type.toString())).fetch();

        List<Module> modules = new LinkedList<>();
        for (Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String> moduleRecord : result) {
            modules.add(new DBModule().initFromRecord(moduleRecord).toModule());
        }

        return modules;
    }

    /**
     * Get all Modules of a given Slave.
     *
     * @param slaveDeviceID DeviceID of the Slave.
     * @return All Modules of the Slave as a list.
     */
    public List<Module> getModulesOfSlave(DeviceID slaveDeviceID) {
        de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave s = SLAVE.as("s");

        Result<Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String>> result
                = moduleSelect
                .join(s).on(m.SLAVEID.equal(s._ID))
                .where(s.FINGERPRINT.equal(slaveDeviceID.getIDString())).fetch();

        List<Module> modules = new LinkedList<>();
        for (Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String> moduleRecord : result) {
            modules.add(new DBModule().initFromRecord(moduleRecord).toModule());
        }

        return modules;
    }

    /**
     * Gets a list of all Modules.
     */
    public List<Module> getModules() {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");

        /*
        //Notice: again changed order for convenience reasons when creating the ModuleAccessPoint.
        Cursor modulesCursor = databaseConnector.executeSql("select" +
                " m." + DatabaseContract.ElectronicModule.COLUMN_GPIO_PIN
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_USB_PORT
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_WLAN_PORT
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_WLAN_USERNAME
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_WLAN_PASSWORD
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_WLAN_IP
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_MODULE_TYPE
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_CONNECTOR_TYPE
                + ", s." + DatabaseContract.Slave.COLUMN_FINGERPRINT
                + ", m." + DatabaseContract.ElectronicModule.COLUMN_NAME
                + " from " + DatabaseContract.ElectronicModule.TABLE_NAME + " m"
                + " join " + DatabaseContract.Slave.TABLE_NAME + " s"
                + " on m." + DatabaseContract.ElectronicModule.COLUMN_SLAVE_ID
                + " = s." + DatabaseContract.Slave.COLUMN_ID, new String[]{});
        List<Module> modules = new LinkedList<>();
        while (modulesCursor.moveToNext()) {
            String[] combinedModuleAccessPointInformation =
                    new String[ModuleAccessPoint.COMBINED_AMOUNT_OF_ACCESS_INFORMATION];
            for (int i = 0; i < ModuleAccessPoint.COMBINED_AMOUNT_OF_ACCESS_INFORMATION; i++) {
                combinedModuleAccessPointInformation[i] = modulesCursor.getString(i);
            }
            ModuleAccessPoint moduleAccessPoint = ModuleAccessPoint
                    .fromCombinedModuleAccessPointInformation(combinedModuleAccessPointInformation,
                            modulesCursor.getString(ModuleAccessPoint.COMBINED_AMOUNT_OF_ACCESS_INFORMATION + 1));

            String moduleType = modulesCursor.getString(ModuleAccessPoint.COMBINED_AMOUNT_OF_ACCESS_INFORMATION);
            modules.add(new Module(
                    modulesCursor.getString(ModuleAccessPoint.COMBINED_AMOUNT_OF_ACCESS_INFORMATION + 3),
                    new DeviceID(modulesCursor.getString(ModuleAccessPoint.COMBINED_AMOUNT_OF_ACCESS_INFORMATION + 2)),
                    CoreConstants.ModuleType.valueOf(moduleType),
                    moduleAccessPoint));
        }
        return modules;
        */
    }

    /**
     * Change the name of a Module.
     *
     * @param oldName Old Module name.
     * @param newName New Module name.
     */
    public void changeModuleName(String oldName, String newName) throws AlreadyInUseException {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        try {
            databaseConnector.executeSql("update " + DatabaseContract.ElectronicModule.TABLE_NAME
                            + " set " + DatabaseContract.ElectronicModule.COLUMN_NAME
                            + " = ? where " + DatabaseContract.ElectronicModule.COLUMN_NAME + " = ?",
                    new String[]{newName, oldName});
        } catch (SQLiteConstraintException sqlce) {
            throw new AlreadyInUseException("The given name is already used by another Module.", sqlce);
        }
        */
    }

    /**
     * Get a Slave by its DeviceID.
     *
     * @param slaveID DeviceID of the Slave.
     * @return The Slave associated with the DeviceID.
     */
    public Slave getSlave(DeviceID slaveID) {
        de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave s = SLAVE.as("s");
        Record1<String> slaveRecord = create.select(s.NAME)
                .from(s)
                .where(s.FINGERPRINT.equal(slaveID.getIDString()))
                .fetchOne();

        if (slaveRecord != null) {
            return new Slave(slaveRecord.value1(), slaveID, passiveRegistrationTokens.get(slaveID));
        }
        return null;
    }

    /**
     * Get a list of all Slaves.
     */
    public List<Slave> getSlaves() {
        Result<Record2<String, String>> result = create.select(SLAVE.NAME, SLAVE.FINGERPRINT)
                .from(SLAVE)
                .fetch();
        List<Slave> slaves = new LinkedList<>();
        for (Record2<String, String> slave : result) {
            DeviceID slaveID = new DeviceID(slave.value2());
            slaves.add(new Slave(slave.value1(), slaveID, passiveRegistrationTokens.get(slaveID)));
        }
        return slaves;
    }

    /**
     * Change the name of a Slave.
     *
     * @param oldName Old Slave name.
     * @param newName New Slave name.
     */
    public void changeSlaveName(String oldName, String newName) throws AlreadyInUseException {
        //TODO port to jooq
        throw new UnsupportedOperationException("Not implemented, yet!");
        /*
        try {
            databaseConnector.executeSql("update " + DatabaseContract.Slave.TABLE_NAME
                            + " set " + DatabaseContract.Slave.COLUMN_NAME
                            + " = ? where " + DatabaseContract.Slave.COLUMN_NAME + " = ?",
                    new String[]{newName, oldName});
        } catch (SQLiteConstraintException sqlce) {
            throw new AlreadyInUseException("The given name is already used by another Slave.", sqlce);
        }
        */
    }

    /**
     * Add a new Slave.
     *
     * @param slave Slave to add.
     */
    public void addSlave(Slave slave) throws AlreadyInUseException {
        try {
            create.insertInto(SLAVE, SLAVE.NAME, SLAVE.FINGERPRINT)
                    .values(slave.getName(), slave.getSlaveID().getIDString())
                    .execute();
            passiveRegistrationTokens.put(slave.getSlaveID(), slave.getPassiveRegistrationToken());
        } catch (DataAccessException e) {
            throw new AlreadyInUseException("The given name or fingerprint is already used by another Slave.", e);
        }
    }

    /**
     * Remove a Slave.
     *
     * @param slaveID DeviceID of the Slave.
     */
    public void removeSlave(DeviceID slaveID) throws IsReferencedException {
        try {
            create.deleteFrom(SLAVE)
                    .where(SLAVE.FINGERPRINT.eq(slaveID.getIDString()))
                    .execute();
        } catch (DataAccessException e) {
            throw new IsReferencedException("This slave is in use. At least one module depends on it.", e);
        }
    }

    /**
     * Get internal database id for a given Module.
     *
     * @param moduleName Name of the Module.
     * @return The database id of the given Module or null if the module does not exist
     */
    public Integer getModuleID(String moduleName) {
        return queryModuleID(moduleName);
    }

    private Integer getSlaveID(DeviceID deviceID) {
        Record1<Integer> slaveRecord = create.select(SLAVE._ID)
                .from(SLAVE)
                .where(SLAVE.FINGERPRINT.equal(deviceID.getIDString()))
                .fetchOne();

        if (slaveRecord != null) {
            return slaveRecord.value1();
        }

        return null;
    }

    private DeviceID getSlaveDeviceID(int id) {
        de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave s = SLAVE.as("s");
        String fingerprint = create.select(s.FINGERPRINT)
                .from(s)
                .where(s._ID.equal(id))
                .fetchOne().value1();

        return new DeviceID(fingerprint);
    }


    /**
     * The DBModule is used to easily convert between database records and the Module DTO.
     */
    private class DBModule {
        private Integer slaveID = null;
        private String name = null;
        private Integer gpioPort = null;
        private Integer usbPort = null;
        private Integer wlanPort = null;
        private String wlanUsername = null;
        private String wlanPassword = null;
        private String wlanIP = null;
        private String moduleType = null;
        private String type = null;

        /**
         * To initialize a DBModule, use init/builder methods {@link #initFromModule(Module)} or
         * {@link #initFromRecord(Record10)}
         */
        private DBModule() {
        }


        /**
         * Initializes this DBModule from the given Module DTO.
         *
         * @param module the module
         * @return this
         */
        private DBModule initFromModule(Module module) {
            ModuleAccessPoint accessPoint = module.getModuleAccessPoint();

            this.slaveID = SlaveController.this.getSlaveID(module.getAtSlave());
            this.name = module.getName();
            this.moduleType = module.getModuleType().toString();
            this.type = accessPoint.getType();

            if (accessPoint instanceof GPIOAccessPoint) {
                this.gpioPort = ((GPIOAccessPoint) accessPoint).getPort();
            } else if (accessPoint instanceof USBAccessPoint) {
                this.usbPort = ((USBAccessPoint) accessPoint).getPort();
            } else if (accessPoint instanceof WLANAccessPoint) {
                WLANAccessPoint wlanAccessPoint = ((WLANAccessPoint) accessPoint);
                this.wlanIP = wlanAccessPoint.getiPAddress();
                this.wlanPort = wlanAccessPoint.getPort();
                this.wlanUsername = wlanAccessPoint.getUsername();
                this.wlanPassword = wlanAccessPoint.getPassword();
            }

            return this;
        }

        /**
         * Creates the corresponding Module DTO.
         *
         * @return the module
         */
        private Module toModule() {
            CoreConstants.ModuleType moduleType = CoreConstants.ModuleType.valueOf(this.moduleType);
            ModuleAccessPoint accessPoint;

            switch (this.moduleType) {
                case USBAccessPoint.TYPE:
                    accessPoint = new USBAccessPoint(usbPort);
                    break;
                case GPIOAccessPoint.TYPE:
                    accessPoint = new GPIOAccessPoint(gpioPort);
                    break;
                case WLANAccessPoint.TYPE:
                    accessPoint = new WLANAccessPoint(wlanPort, wlanUsername, wlanPassword, wlanIP);
                    break;
                case MockAccessPoint.TYPE:
                    accessPoint = new MockAccessPoint();
                    break;
                default:
                    accessPoint = null;
            }

            return new Module(name, SlaveController.this.getSlaveDeviceID(slaveID), moduleType, accessPoint);
        }

        /**
         * Initializes this DBModule from the given record of the form:
         * SLAVEID, NAME, GPIOPIN, USBPORT, WLANIP, WLANPORT, WLANUSERNAME, WLANPASSWORD, MODULETYPE, TYPE
         *
         * @param record
         * @return this
         */
        private DBModule initFromRecord(Record10<Integer, String, Integer, Integer, String, Integer, String, String, String, String> record) {
            this.slaveID = record.value1();
            this.name = record.value2();
            this.gpioPort = record.value3();
            this.usbPort = record.value4();
            this.wlanIP = record.value5();
            this.wlanPort = record.value6();
            this.wlanUsername = record.value7();
            this.wlanPassword = record.value8();
            this.moduleType = record.value9();
            this.type = record.value10();

            return this;
        }

        private int getSlaveID() {
            return slaveID;
        }

        private String getName() {
            return name;
        }

        private int getGpioPort() {
            return gpioPort;
        }

        private int getUsbPort() {
            return usbPort;
        }

        private int getWlanPort() {
            return wlanPort;
        }

        private String getWlanUsername() {
            return wlanUsername;
        }

        private String getWlanPassword() {
            return wlanPassword;
        }

        private String getWlanIP() {
            return wlanIP;
        }

        private String getModuleType() {
            return moduleType;
        }

        private String getType() {
            return type;
        }
    }
}