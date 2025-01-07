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
package com.blazebit.job.schedule.spring;

import com.blazebit.job.Schedule;
import com.blazebit.job.ScheduleContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.util.Date;

/**
 * A {@link Schedule} implementation based on Spring's {@link Trigger} implementation.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class TriggerSchedule implements Schedule {

    private final Trigger trigger;

    /**
     * Creates a new schedule from the given trigger.
     *
     * @param trigger The trigger
     */
    public TriggerSchedule(Trigger trigger) {
        this.trigger = trigger;
    }

    @Override
    public long nextEpochSchedule(ScheduleContext ctx) {
        Date nextValidTimeAfter = trigger.nextExecutionTime(new DelegatingTriggerContext(ctx));
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

    /**
     * A Spring {@link TriggerContext} implementation that delegates to {@link ScheduleContext}.
     *
     * @author Christian Beikov
     * @since 1.0.0
     */
    private static class DelegatingTriggerContext implements TriggerContext {
        private final ScheduleContext delegate;

        private DelegatingTriggerContext(ScheduleContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public Date lastScheduledExecutionTime() {
            return new Date(delegate.getLastScheduleTime());
        }

        @Override
        public Date lastActualExecutionTime() {
            return new Date(delegate.getLastExecutionTime());
        }

        @Override
        public Date lastCompletionTime() {
            return new Date(delegate.getLastCompletionTime());
        }
    }

}
