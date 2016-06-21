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
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.database.UnknownReferenceException;
import de.unipassau.isl.evs.ssh.core.database.dto.HolidayAction;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule;
import de.unipassau.isl.evs.ssh.master.database.generated.tables.Holidaylog;
import org.jooq.DSLContext;

import java.util.List;

import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;
import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Holidaylog.HOLIDAYLOG;

/**
 * Offers high level methods to interact with the holiday table in the database.
 *
 * @author Leon Sell
 */
public class HolidayController extends AbstractController {
    public static final Key<HolidayController> KEY = new Key<>(HolidayController.class);

    /**
     * Add a new action to the database.
     *
     * @param action     Action to be added to the database.
     * @param moduleName Module where the action occurs.
     * @param timestamp  The timestamp of the action.
     */
    public void addHolidayLogEntry(String action, String moduleName, long timestamp) throws UnknownReferenceException {
        if (Strings.isNullOrEmpty(moduleName)) {
            create.insertInto(HOLIDAYLOG, HOLIDAYLOG.ACTION, HOLIDAYLOG.TIMESTAMP)
                    .values(action, timestamp).execute();
        } else {
            Integer moduleID = create.select(ELECTRONICMODULE._ID)
                    .from(ELECTRONICMODULE)
                    .where(ELECTRONICMODULE.NAME.equal(moduleName))
                    .fetchOne().value1();

            create.insertInto(HOLIDAYLOG, HOLIDAYLOG.ACTION, HOLIDAYLOG.ELECTRONICMODULEID, HOLIDAYLOG.TIMESTAMP)
                    .values(action, moduleID, timestamp).execute();
        }
    }

    /**
     * Add a new action to the database. With the current time as the timestamp.
     *
     * @param action     Action to be added to the database.
     * @param moduleName Module where the action occurs.
     */
    public void addHolidayLogEntryNow(String action, String moduleName) throws UnknownReferenceException {
        addHolidayLogEntry(action, moduleName, System.currentTimeMillis());
    }

    /**
     * Returns all actions logged and saved into the holiday table in a given range of time.
     *
     * @param from Start point in time of the range.
     * @param to   End point in time of the range.
     * @return List of the entries found.
     */
    public List<HolidayAction> getHolidayActions(long from, long to) {

        Holidaylog h = HOLIDAYLOG.as("h");
        Electronicmodule m = ELECTRONICMODULE.as("m");

        return create.select(h.ACTION, m.NAME, h.TIMESTAMP)
                .from(h).leftJoin(m).on(h.ELECTRONICMODULEID.equal(m._ID))
                .where(h.TIMESTAMP.between(from, to)).fetchInto(HolidayAction.class);
    }
}