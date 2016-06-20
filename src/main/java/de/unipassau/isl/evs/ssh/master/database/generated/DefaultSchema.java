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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.8.2"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DefaultSchema extends SchemaImpl {

    private static final long serialVersionUID = 1845893114;

    /**
     * The reference instance of <code></code>
     */
    public static final DefaultSchema DEFAULT_SCHEMA = new DefaultSchema();

    /**
     * The table <code>DeviceGroup</code>.
     */
    public final Devicegroup DEVICEGROUP = de.unipassau.isl.evs.ssh.master.database.generated.tables.Devicegroup.DEVICEGROUP;

    /**
     * The table <code>ElectronicModule</code>.
     */
    public final Electronicmodule ELECTRONICMODULE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;

    /**
     * The table <code>HolidayLog</code>.
     */
    public final Holidaylog HOLIDAYLOG = de.unipassau.isl.evs.ssh.master.database.generated.tables.Holidaylog.HOLIDAYLOG;

    /**
     * The table <code>Permission</code>.
     */
    public final Permission PERMISSION = de.unipassau.isl.evs.ssh.master.database.generated.tables.Permission.PERMISSION;

    /**
     * The table <code>PermissionTemplate</code>.
     */
    public final Permissiontemplate PERMISSIONTEMPLATE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Permissiontemplate.PERMISSIONTEMPLATE;

    /**
     * The table <code>Slave</code>.
     */
    public final Slave SLAVE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave.SLAVE;

    /**
     * The table <code>UserDevice</code>.
     */
    public final Userdevice USERDEVICE = de.unipassau.isl.evs.ssh.master.database.generated.tables.Userdevice.USERDEVICE;

    /**
     * The table <code>composed_of_permission</code>.
     */
    public final ComposedOfPermission COMPOSED_OF_PERMISSION = de.unipassau.isl.evs.ssh.master.database.generated.tables.ComposedOfPermission.COMPOSED_OF_PERMISSION;

    /**
     * The table <code>has_permission</code>.
     */
    public final HasPermission HAS_PERMISSION = de.unipassau.isl.evs.ssh.master.database.generated.tables.HasPermission.HAS_PERMISSION;

    /**
     * No further instances allowed
     */
    private DefaultSchema() {
        super("", null);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        List result = new ArrayList();
        result.addAll(getTables0());
        return result;
    }

    private final List<Table<?>> getTables0() {
        return Arrays.<Table<?>>asList(
            Devicegroup.DEVICEGROUP,
            Electronicmodule.ELECTRONICMODULE,
            Holidaylog.HOLIDAYLOG,
            Permission.PERMISSION,
            Permissiontemplate.PERMISSIONTEMPLATE,
            Slave.SLAVE,
            Userdevice.USERDEVICE,
            ComposedOfPermission.COMPOSED_OF_PERMISSION,
            HasPermission.HAS_PERMISSION);
    }
}
