package de.unipassau.isl.evs.ssh.master.database;

import de.unipassau.isl.evs.ssh.core.container.AbstractComponent;
import de.unipassau.isl.evs.ssh.core.container.Container;
import org.jooq.DSLContext;
import org.jooq.Record1;

import static de.unipassau.isl.evs.ssh.master.database.generated.tables.Electronicmodule.ELECTRONICMODULE;

/**
 * Created by popeye on 6/20/16.
 */
abstract class AbstractController extends AbstractComponent {
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
