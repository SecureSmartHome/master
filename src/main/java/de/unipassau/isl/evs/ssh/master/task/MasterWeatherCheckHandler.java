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

import com.google.common.base.Strings;
import de.ncoder.typedmap.Key;
import de.unipassau.isl.evs.ssh.core.CoreConstants;
import de.unipassau.isl.evs.ssh.core.container.Component;
import de.unipassau.isl.evs.ssh.core.container.Container;
import de.unipassau.isl.evs.ssh.core.messaging.Message;
import de.unipassau.isl.evs.ssh.core.messaging.RoutingKey;
import de.unipassau.isl.evs.ssh.core.messaging.payload.DoorStatusPayload;
import de.unipassau.isl.evs.ssh.core.messaging.payload.NotificationPayload;
import de.unipassau.isl.evs.ssh.core.schedule.ExecutionServiceComponent;
import de.unipassau.isl.evs.ssh.core.schedule.Scheduler;
import de.unipassau.isl.evs.ssh.master.MasterConfiguration;
import de.unipassau.isl.evs.ssh.master.handler.AbstractMasterHandler;
import de.unipassau.isl.evs.ssh.master.network.broadcast.NotificationBroadcaster;
import net.aksingh.owmjapis.CurrentWeather;
import net.aksingh.owmjapis.OpenWeatherMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static de.unipassau.isl.evs.ssh.core.messaging.RoutingKeys.MASTER_DOOR_STATUS_UPDATE;

/**
 * Task/Handler that periodically checks the records of weather data provider and issues notifications
 * based on a configured set of rules.
 *
 * @author Christoph Fraedrich
 */
public class MasterWeatherCheckHandler extends AbstractMasterHandler implements Component {
    public static final Key<MasterWeatherCheckHandler> KEY = new Key<>(MasterWeatherCheckHandler.class);
    private static final long CHECK_INTERVAL_MINUTES = 5;
    private static final long FAILURE_UPDATE_TIMER = TimeUnit.MINUTES.toMillis(45);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private long timeStamp = -1;
    private final Map<String, Boolean> openForModule = new HashMap<>();
    private final OpenWeatherMap owm = new OpenWeatherMap(CoreConstants.OPENWEATHERMAP_API_KEY);
    private ScheduledFuture scheduledFuture;

    @Override
    public void handle(Message.AddressedMessage message) {
        if (MASTER_DOOR_STATUS_UPDATE.matches(message)) {
            final DoorStatusPayload doorStatusPayload = MASTER_DOOR_STATUS_UPDATE.getPayload(message);
            openForModule.put(doorStatusPayload.getModuleName(), doorStatusPayload.isOpen());
        } else {
            invalidMessage(message);
        }
    }

    @Override
    public RoutingKey[] getRoutingKeys() {
        return new RoutingKey[]{MASTER_DOOR_STATUS_UPDATE};
    }

    @Override
    public void init(Container container) {
        final Scheduler scheduler = container.require(Scheduler.KEY);
        final MasterConfiguration config = requireComponent(MasterConfiguration.KEY);
        final WeatherCheckRunner task = new WeatherCheckRunner(config.getLocation());
        scheduledFuture = scheduler.scheduleAtFixedRate(task, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void destroy() {
        if (getContainer() != null) {
            scheduledFuture.cancel(true);
        }
    }

    private class WeatherCheckRunner implements Runnable {
        private final String city;

        private WeatherCheckRunner(String city) {
            this.city = city;
        }

        public void run() {
            if (Strings.isNullOrEmpty(city)) {
                return;
            }

            logger.info("Inquiring weather data for " + city);
            //Presentation Mode
            if (city.equals("Mordor")) {
                for (Boolean isOpen : openForModule.values()) {
                    if (isOpen) {
                        sendWarningNotification();
                        break;
                    }
                }
                return;
            }

            requireComponent(ExecutionServiceComponent.KEY).execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        CurrentWeather cw = owm.currentWeatherByCityName(city);
                        if (cw == null || cw.getRainInstance() == null) {
                            WeatherServiceFailed(city);
                            return;
                        }

                        if (cw.getRainInstance().hasRain()) {
                            for (Boolean isOpen : openForModule.values()) {
                                if (isOpen) {
                                    sendWarningNotification();
                                    break;
                                }
                            }
                        }
                    } catch (IOException e) {
                        logger.error(e.getLocalizedMessage());
                        WeatherServiceFailed(city);
                    } catch (Exception e) {
                        logger.error(e.getLocalizedMessage());
                    }
                }
            });
        }
    }

    private void WeatherServiceFailed(String city) {
        if (timeStamp == -1 || timeStamp - System.currentTimeMillis() > FAILURE_UPDATE_TIMER) {
            requireComponent(NotificationBroadcaster.KEY).sendMessageToAllReceivers(
                    NotificationPayload.NotificationType.WEATHER_SERVICE_FAILED, city);
            timeStamp = System.currentTimeMillis();
        }
    }

    private void sendWarningNotification() {
        //No hardcoded strings, only in strings.xml
        NotificationBroadcaster notificationBroadcaster = requireComponent(NotificationBroadcaster.KEY);
        notificationBroadcaster.sendMessageToAllReceivers(NotificationPayload.NotificationType.WEATHER_WARNING);
    }
}