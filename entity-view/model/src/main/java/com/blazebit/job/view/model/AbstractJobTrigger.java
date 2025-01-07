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

package com.blazebit.job.view.model;

import com.blazebit.job.JobContext;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.Schedule;

/**
 * An abstract entity view implementing the {@link JobTrigger} interface.
 *
 * @param <T> The job type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractJobTrigger<T extends AbstractJob> extends AbstractJobInstance<Long> implements JobTrigger {

    private static final long serialVersionUID = 1L;

    @Override
    public void onChunkSuccess(JobInstanceProcessingContext<?> processingContext) {
    }

    /**
     * Sets the given name.
     *
     * @param name The name
     */
    public abstract void setName(String name);

    @Override
    public abstract T getJob();

    @Override
    public abstract JobConfigurationView getJobConfiguration();

    @Override
    public abstract boolean isAllowOverlap();

    /**
     * Sets whether overlapping executions are allowed.
     *
     * @param allowOverlap whether overlapping executions are allowed
     */
    public abstract void setAllowOverlap(boolean allowOverlap);

    /**
     * Returns the cron expression for the schedule.
     *
     * @return the cron expression for the schedule
     */
    public abstract String getScheduleCronExpression();

    /**
     * Sets the cron expression for the schedule.
     *
     * @param scheduleCronExpression The cron expression
     */
    public abstract void setScheduleCronExpression(String scheduleCronExpression);

    @Override
    public Schedule getSchedule(JobContext jobContext) {
        return getScheduleCronExpression() == null ? null : jobContext.getScheduleFactory().createSchedule(getScheduleCronExpression());
    }

}
