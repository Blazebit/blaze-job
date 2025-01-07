/*
 * Copyright 2018 - 2025 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blazebit.job.schedule.cron;

import com.blazebit.job.Schedule;
import com.blazebit.job.ScheduleContext;

import java.text.ParseException;
import java.util.Date;

/**
 * A {@link Schedule} implementation based on Terracotta's {@link CronExpression} implementation.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class CronSchedule implements Schedule {

    private final CronExpression cronExpression;

    /**
     * Creates a new schedule based on the given cron expression.
     *
     * @param cronExpression The cron expression
     * @throws ParseException when the cron expression is invalid
     */
    public CronSchedule(String cronExpression) throws ParseException {
        this.cronExpression = new CronExpression(cronExpression);
    }

    @Override
    public long nextEpochSchedule(ScheduleContext ctx) {
        Date nextValidTimeAfter = cronExpression.getNextValidTimeAfter(new Date(ctx.getLastScheduleTime()));
        // if getLastScheduledExecutionTime() returned a date-time that happened before the cron expression, it returns null
        if (nextValidTimeAfter == null) {
            // if the schedule ran before, we are done and return the last scheduled time
            // which will result in de-scheduling the task
            if (ctx.getLastCompletionTime() > ctx.getLastScheduleTime()) {
                return ctx.getLastScheduleTime();
            }
            // otherwise we assume now is the next schedule
            return System.currentTimeMillis();
        }
        return nextValidTimeAfter.getTime();
    }
}
