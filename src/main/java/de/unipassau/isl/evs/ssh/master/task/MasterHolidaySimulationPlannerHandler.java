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

package de.unipassau.isl.evs.ssh.master.task;

import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.container.Component;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.database.dto.HolidayAction;
import de.unipassau.isl.evs.ssh.core.database.dto.Module;
import de.unipassau.isl.evs.ssh.core.messaging.Message;
import de.unipassau.isl.evs.ssh.core.messaging.RoutingKey;
import de.unipassau.isl.evs.ssh.core.messaging.payload.HolidaySimulationPayload;
import de.unipassau.isl.evs.ssh.core.messaging.payload.LightPayload;
import de.unipassau.isl.evs.ssh.core.messaging.payload.MessagePayload;
import de.unipassau.isl.evs.ssh.core.schedule.Scheduler;
import de.unipassau.isl.evs.ssh.master.database.HolidayController;
import de.unipassau.isl.evs.ssh.master.database.SlaveController;
import de.unipassau.isl.evs.ssh.master.handler.AbstractMasterHandler;
import de.unipassau.isl.evs.ssh.master.network.broadcast.NotificationBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys.MASTER_HOLIDAY_GET;
import static de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys.MASTER_HOLIDAY_SET;
import static de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys.SLAVE_LIGHT_SET;
import static de.unipassau.isl.evs.ssh.core.messaging.payload.NotificationPayload.NotificationType.HOLIDAY_MODE_SWITCHED_OFF;
import static de.unipassau.isl.evs.ssh.core.messaging.payload.NotificationPayload.NotificationType.HOLIDAY_MODE_SWITCHED_ON;
import static de.unipassau.isl.evs.ssh.core.sec.Permission.TOGGLE_HOLIDAY_SIMULATION;

/**
 * This handler calculates what actions need to take place in order to execute the holiday simulation.
 * It then tells the scheduler which HolidayTasks need to be scheduled for which time and also
 * issues a schedule entry for itself, so it is executed again after all planned tasks are finished.
 *
 * @author Christoph Fraedrich
 */
public class MasterHolidaySimulationPlannerHandler extends AbstractMasterHandler implements Component {
    private static final long SCHEDULE_LOOKAHEAD_MILLIS = TimeUnit.HOURS.toMillis(1);
    public static final Key<MasterHolidaySimulationPlannerHandler> KEY = new Key<>(MasterHolidaySimulationPlannerHandler.class);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final LinkedList<ScheduledFuture<?>> actions = new LinkedList<>();
    private boolean runHolidaySimulation = false;

    @Override
    public RoutingKey[] getRoutingKeys() {
        return new RoutingKey[]{MASTER_HOLIDAY_GET, MASTER_HOLIDAY_SET};
    }

    @Override
    public void handle(Message.AddressedMessage message) {
        if (MASTER_HOLIDAY_GET.matches(message)) {
            replyStatus(message);
        } else if (MASTER_HOLIDAY_SET.matches(message)) {
            MASTER_HOLIDAY_SET.getPayload(message);
            handleHolidaySet(message, MASTER_HOLIDAY_SET.getPayload(message));
        } else {
            invalidMessage(message);
        }
    }

    private void handleHolidaySet(Message.AddressedMessage message, HolidaySimulationPayload holidaySimulationPayload) {
        if (hasPermission(message.getFromID(), TOGGLE_HOLIDAY_SIMULATION)) {
            runHolidaySimulation = holidaySimulationPayload.switchOn();
            replyStatus(message);
            final NotificationBroadcaster notificationBroadcaster = requireComponent(NotificationBroadcaster.KEY);
            if (runHolidaySimulation) {
                notificationBroadcaster.sendMessageToAllReceivers(HOLIDAY_MODE_SWITCHED_ON);
                scheduleActions();
            } else {
                notificationBroadcaster.sendMessageToAllReceivers(HOLIDAY_MODE_SWITCHED_OFF);
                cancelActions();
            }
        } else {
            sendNoPermissionReply(message, TOGGLE_HOLIDAY_SIMULATION);
        }
    }

    private void replyStatus(Message.AddressedMessage message) {
        HolidaySimulationPayload payload = new HolidaySimulationPayload(runHolidaySimulation);
        Message reply = new Message(payload);
        sendReply(message, reply);
    }

    private void scheduleActions() {
        logger.info("HolidayPlanner calculating...");
        //Cannot do anything without the container
        if (runHolidaySimulation && getContainer() != null) {
            final long planningStartTime = System.currentTimeMillis();
            //replays last week
            List<HolidayAction> lastWeek = requireComponent(HolidayController.KEY).getHolidayActions(
                    planningStartTime - TimeUnit.DAYS.toMillis(7),
                    planningStartTime - TimeUnit.DAYS.toMillis(7) + SCHEDULE_LOOKAHEAD_MILLIS
            );

            Scheduler scheduler = requireComponent(Scheduler.KEY);
            for (HolidayAction a : lastWeek) {
                Runnable action = new HolidayLightAction(a.getModuleName(), a.getActionName());
                long delay = (a.getTimeStamp() + TimeUnit.DAYS.toMillis(7) - planningStartTime) / 1000;
                actions.add(scheduler.schedule(action, delay, TimeUnit.SECONDS));
            }
        }
    }

    private void cancelActions() {
        for (ScheduledFuture<?> action : actions) {
            action.cancel(false);
        }
        actions.clear();
    }

    @Override
    public void init(Container container) {
        super.init(container);
    }

    @Override
    public void destroy() {
        cancelActions();
        super.destroy();
    }

    /**
     * Private class representing an action which has to be executed when the holiday simulation
     * is active.
     */
    private class HolidayLightAction implements Runnable {

        final String moduleName;
        final String actionName;

        private HolidayLightAction(String moduleName, String actionName) {
            this.moduleName = moduleName;
            this.actionName = actionName;
        }

        @Override
        public void run() {
            boolean on = false;
            if (getContainer() != null) {
                Module module = getContainer().require(SlaveController.KEY).getModule(moduleName);
                if (actionName.equals(CoreConstants.LogActions.LIGHT_ON_ACTION)) {
                    on = true;
                } else if (actionName.equals(CoreConstants.LogActions.LIGHT_OFF_ACTION)) {
                    on = false;
                }
                MessagePayload payload = new LightPayload(on, module);
                Message message = new Message(payload);
                sendMessage(module.getAtSlave(), SLAVE_LIGHT_SET, message);
            }
        }
    }

    public boolean isRunHolidaySimulation() {
        return runHolidaySimulation;
    }
}