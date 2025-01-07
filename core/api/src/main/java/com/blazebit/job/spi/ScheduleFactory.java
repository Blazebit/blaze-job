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
package com.blazebit.job.spi;

import com.blazebit.job.Schedule;

import java.time.Instant;

/**
 * Interface implemented by the job implementation provider.
 *
 * Implementations are instantiated via {@link java.util.ServiceLoader}.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface ScheduleFactory {

    /**
     * Returns a cron expression that will fire when the given instant is reached.
     *
     * @param instant The instant for which to create the cron expression
     * @return the cron expression
     */
    String asCronExpression(Instant instant);

    /**
     * Creates a schedule from the given schedule implementation specific cron expression.
     * The general cron syntax is standardized, but depending on the concrete implementation it might be possible
     * to e.g. specify the year as well.
     *
     * @param cronExpression The schedule implementation specific cron expression
     * @return a new schedule
     */
    Schedule createSchedule(String cronExpression);
}
