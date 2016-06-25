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

/**
 * This class is generated by jOOQ
 */
package de.unipassau.isl.evs.ssh.master.database.generated;


import de.unipassau.isl.evs.ssh.master.database.generated.tables.ComposedOfPermission;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.HasPermission;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Holidaylog;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Permission;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice;

import javax.annotation.Generated;


/**
 * Convenience access to all tables in 
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.8.2"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Tables {

    /**
     * The table <code>DeviceGroup</code>.
     */
    public static final Devicegroup DEVICEGROUP = de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup.DEVICEGROUP;

    /**
     * The table <code>ElectronicModule</code>.
     */
    public static final Electronicmodule ELECTRONICMODULE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;

    /**
     * The table <code>HolidayLog</code>.
     */
    public static final Holidaylog HOLIDAYLOG = de.unipassau.isl.evs.ssh.master.database.generated.tables.Holidaylog.HOLIDAYLOG;

    /**
     * The table <code>Permission</code>.
     */
    public static final Permission PERMISSION = de.unipassau.isl.evs.ssh.master.database.generated.tables.Permission.PERMISSION;

    /**
     * The table <code>PermissionTemplate</code>.
     */
    public static final Permissiontemplate PERMISSIONTEMPLATE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate.PERMISSIONTEMPLATE;

    /**
     * The table <code>Slave</code>.
     */
    public static final Slave SLAVE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave.SLAVE;

    /**
     * The table <code>UserDevice</code>.
     */
    public static final Userdevice USERDEVICE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice.USERDEVICE;

    /**
     * The table <code>composed_of_permission</code>.
     */
    public static final ComposedOfPermission COMPOSED_OF_PERMISSION = de.unipassau.isl.evs.ssh.master.database.generated.tables.ComposedOfPermission.COMPOSED_OF_PERMISSION;

    /**
     * The table <code>has_permission</code>.
     */
    public static final HasPermission HAS_PERMISSION = de.unipassau.isl.evs.ssh.master.database.generated.tables.HasPermission.HAS_PERMISSION;
}
