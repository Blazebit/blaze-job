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

/**
 * An abstraction for recurring schedules that depend on the time of the previous execution.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface ScheduleContext {

    /**
     * Returns the epoch in milliseconds when the last schedule was planned.
     *
     * @return the epoch in milliseconds when the last schedule was planned.
     */
    long getLastScheduleTime();

    /**
     * Returns the epoch in milliseconds when the last schedule actually happened.
     *
     * @return the epoch in milliseconds when the last schedule actually happened.
     */
    long getLastExecutionTime();

    /**
     * Returns the epoch in milliseconds when the last schedule actually completed.
     *
     * @return the epoch in milliseconds when the last schedule actually completed.
     */
    long getLastCompletionTime();
}
