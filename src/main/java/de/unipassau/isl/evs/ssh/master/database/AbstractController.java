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

import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.Container;
import org.jooq.DSLContext;
import org.jooq.Record1;

import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;

/**
 * The AbstractController is the common super class of all database controller classes and handles access to JOOQ's
 * DSLContext.
 *
 * @author Wolfgang Popp
 */
abstract class AbstractController extends AbstractComponent {

    /**
     * Used in subclasses to create SQL queries.
     */
    protected DSLContext create;

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
     * Queries the ID of the given module.
     *
     * @param moduleName the name of the given module
     * @return the id or null if the given module was not found
     */
    Integer queryModuleID(String moduleName) {
        Record1<Integer> moduleRecord = create.select(ELECTRONICMODULE._ID)
                .from(ELECTRONICMODULE)
                .where(ELECTRONICMODULE.NAME.equal(moduleName))
                .fetchOne();
        if (moduleRecord != null) {
            return moduleRecord.value1();
        }
        return null;
    }
}
