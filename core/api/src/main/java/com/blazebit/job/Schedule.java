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
package com.blazebit.job;

import java.time.Instant;

/**
 * An abstraction for time schedules.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface Schedule {

    /**
     * Returns {@link #nextEpochSchedule(ScheduleContext)} for {@link #now()} wrapped in an {@link Instant}.
     *
     * @return the next schedule
     */
    default Instant nextSchedule() {
        return nextSchedule(now());
    }

    /**
     * Returns {@link #nextEpochSchedule(ScheduleContext)} wrapped in an {@link Instant}.
     *
     * @param ctx The schedule context
     * @return the next schedule
     */
    default Instant nextSchedule(ScheduleContext ctx) {
        return Instant.ofEpochMilli(nextEpochSchedule(ctx));
    }

    /**
     * Returns {@link #nextEpochSchedule(ScheduleContext)} for {@link #now()}.
     *
     * @return the next epoch schedule
     */
    default long nextEpochSchedule() {
        return nextEpochSchedule(now());
    }

    /**
     * The next epoch in milliseconds, when the schedule should fire.
     * Returning the value {@link ScheduleContext#getLastScheduleTime()}
     * means that there is no next schedule.
     *
     * @param ctx The schedule context
     * @return the next schedule in epoch milliseconds
     */
    long nextEpochSchedule(ScheduleContext ctx);

    /**
     * Returns a schedule context with the current time as last schedule time.
     *
     * @return a schedule context
     */
    static ScheduleContext now() {
        final long now = System.currentTimeMillis();
        return scheduleContext(now);
    }

    /**
     * Returns a schedule context with the given time as last schedule time.
     *
     * @param lastScheduleTime The last schedule time
     * @return a schedule context
     */
    static ScheduleContext scheduleContext(long lastScheduleTime) {
        return new ScheduleContext() {
            @Override
            public long getLastScheduleTime() {
                return lastScheduleTime;
            }

            @Override
            public long getLastExecutionTime() {
                return 0;
            }

            @Override
            public long getLastCompletionTime() {
                return 0;
            }
        };
    }
}
