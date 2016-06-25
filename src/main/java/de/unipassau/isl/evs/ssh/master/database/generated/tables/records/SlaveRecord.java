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
package de.unipassau.isl.evs.ssh.master.database.generated.tables.records;


import de.unipassau.isl.evs.ssh.master.database.generated.tables.Slave;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;


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
public class SlaveRecord extends UpdatableRecordImpl<SlaveRecord> implements Record3<Integer, String, String> {

    private static final long serialVersionUID = 1025055111;

    /**
     * Setter for <code>Slave._ID</code>.
     */
    public void set_Id(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>Slave._ID</code>.
     */
    public Integer get_Id() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>Slave.name</code>.
     */
    public void setName(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>Slave.name</code>.
     */
    public String getName() {
        return (String) get(1);
    }

    /**
     * Setter for <code>Slave.fingerprint</code>.
     */
    public void setFingerprint(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>Slave.fingerprint</code>.
     */
    public String getFingerprint() {
        return (String) get(2);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record3 type implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public Row3<Integer, String, String> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Row3<Integer, String, String> valuesRow() {
        return (Row3) super.valuesRow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<Integer> field1() {
        return Slave.SLAVE._ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field2() {
        return Slave.SLAVE.NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Field<String> field3() {
        return Slave.SLAVE.FINGERPRINT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer value1() {
        return get_Id();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value2() {
        return getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String value3() {
        return getFingerprint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlaveRecord value1(Integer value) {
        set_Id(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlaveRecord value2(String value) {
        setName(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlaveRecord value3(String value) {
        setFingerprint(value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlaveRecord values(Integer value1, String value2, String value3) {
        value1(value1);
        value2(value2);
        value3(value3);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached SlaveRecord
     */
    public SlaveRecord() {
        super(Slave.SLAVE);
    }

    /**
     * Create a detached, initialised SlaveRecord
     */
    public SlaveRecord(Integer _Id, String name, String fingerprint) {
        super(Slave.SLAVE);

        set(0, _Id);
        set(1, name);
        set(2, fingerprint);
    }
}
