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
package de.unipassau.isl.evs.ssh.master.database.generated.tables;


import de.unipassau.isl.evs.ssh.master.database.generated.DefaultSchema;
import de.unipassau.isl.evs.ssh.master.database.generated.Keys;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.records.PermissiontemplateRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.TableImpl;


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
public class Permissiontemplate extends TableImpl<PermissiontemplateRecord> {

    private static final long serialVersionUID = -983950292;

    /**
     * The reference instance of <code>PermissionTemplate</code>
     */
    public static final Permissiontemplate PERMISSIONTEMPLATE = new Permissiontemplate();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<PermissiontemplateRecord> getRecordType() {
        return PermissiontemplateRecord.class;
    }

    /**
     * The column <code>PermissionTemplate._ID</code>.
     */
    public final TableField<PermissiontemplateRecord, Integer> _ID = createField("_ID", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

    /**
     * The column <code>PermissionTemplate.name</code>.
     */
    public final TableField<PermissiontemplateRecord, String> NAME = createField("name", org.jooq.impl.SQLDataType.VARCHAR.nullable(false), this, "");

    /**
     * Create a <code>PermissionTemplate</code> table reference
     */
    public Permissiontemplate() {
        this("PermissionTemplate", null);
    }

    /**
     * Create an aliased <code>PermissionTemplate</code> table reference
     */
    public Permissiontemplate(String alias) {
        this(alias, PERMISSIONTEMPLATE);
    }

    private Permissiontemplate(String alias, Table<PermissiontemplateRecord> aliased) {
        this(alias, aliased, null);
    }

    private Permissiontemplate(String alias, Table<PermissiontemplateRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UniqueKey<PermissiontemplateRecord> getPrimaryKey() {
        return Keys.PK_PERMISSIONTEMPLATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UniqueKey<PermissiontemplateRecord>> getKeys() {
        return Arrays.<UniqueKey<PermissiontemplateRecord>>asList(Keys.PK_PERMISSIONTEMPLATE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Permissiontemplate as(String alias) {
        return new Permissiontemplate(alias, this);
    }

    /**
     * Rename this table
     */
    public Permissiontemplate rename(String name) {
        return new Permissiontemplate(name, null);
    }
}
