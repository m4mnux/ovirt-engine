package org.ovirt.engine.core.bll;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.ovirt.engine.core.bll.tasks.CommandCoordinatorUtil;
import org.ovirt.engine.core.common.BackendService;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.compat.DateTime;
import org.ovirt.engine.core.utils.timer.OnTimerMethodAnnotation;
import org.ovirt.engine.core.utils.timer.SchedulerUtilQuartzImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CommandEntityCleanupManager implements BackendService {

    private static Logger log = LoggerFactory.getLogger(CommandEntityCleanupManager.class);

    @Inject
    private SchedulerUtilQuartzImpl schedulerUtil;

    @PostConstruct
    private void init() {
        log.info("Start initializing {}", getClass().getSimpleName());
        Calendar calendar = new GregorianCalendar();
        Date commandEntityCleanupTime = Config.<DateTime> getValue(ConfigValues.CommandEntityCleanupTime);
        calendar.setTimeInMillis(commandEntityCleanupTime.getTime());

        String cronExpression = String.format("%d %d %d * * ?", calendar.get(Calendar.SECOND),
                calendar.get(Calendar.MINUTE), calendar.get(Calendar.HOUR_OF_DAY));

        log.info("Setting command entity cleanup manager to run at: {}", cronExpression);
        schedulerUtil.scheduleACronJob(this, "onTimer", new Class[] {}, new Object[] {}, cronExpression);
        log.info("Finished initializing {}", getClass().getSimpleName());
    }

    @OnTimerMethodAnnotation("onTimer")
    public void onTimer() {
        try {
            log.info("Start deleteAgedOutCommandEntities");
            DateTime latestTimeToKeep = DateTime.getNow().addDays(
                    Config.<Integer>getValue(ConfigValues.CommandEntityAgingThreshold)
                            * -1);
            CommandCoordinatorUtil.removeAllCommandsBeforeDate(latestTimeToKeep);
            log.info("Finished deleteAgedOutCommandEntities");
        } catch (RuntimeException e) {
            log.error("deleteAgedOutCommandEntities failed with exception", e);
        }
    }

}
