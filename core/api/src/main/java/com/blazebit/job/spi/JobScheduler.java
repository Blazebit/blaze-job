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

import com.blazebit.job.JobInstance;

import java.util.concurrent.TimeUnit;

/**
 * Interface implemented by the job implementation provider.
 *
 * Schedules job instances for a specific partition based on their schedule.
 * It uses the {@link com.blazebit.job.JobManager} to access the jobs of the partition.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobScheduler {

    /**
     * Start scheduling job instances.
     */
    void start();

    /**
     * Force a refresh of the next schedule.
     */
    default void refreshSchedules() {
        refreshSchedules(0L);
    }

    /**
     * Inform the scheduler about the given new schedule so it can refresh internal schedules.
     *
     * @param earliestNewSchedule The newest schedule
     */
    void refreshSchedules(long earliestNewSchedule);

    /**
     * Tell the scheduler to reschedule the given job instance.
     *
     * @param jobInstance The job instance to reschedule
     */
    void reschedule(JobInstance<?> jobInstance);

    /**
     * Returns the cluster position where the given long running job instance is running, or <code>-1</code>.
     *
     * @param jobInstance The long running job instance
     * @return The cluster position
     */
    int getClusterPosition(JobInstance<?> jobInstance);

    /**
     * Returns the thread stack trace of the given long running job instance if it is still running, or <code>null</code>.
     *
     * @param jobInstance The long running job instance
     * @return The trace of the job instance processor
     */
    String getTrace(JobInstance<?> jobInstance);

    /**
     * Cancels the given long running job instance if it is still running.
     *
     * @param jobInstance The long running job instance to cancel
     */
    void cancel(JobInstance<?> jobInstance);

    /**
     * Stops the job scheduler.
     * After this method finished no further jobs are scheduled but there may still be running jobs.
     */
    void stop();

    /**
     * Stops the job scheduler and waits up to the given amount of time for currently running jobs to finish.
     * After this method finished no further jobs are scheduled.
     *
     * @param timeout The maximum time to wait
     * @param unit The time unit of the timeout argument
     * @throws InterruptedException if interrupted while waiting
     */
    void stop(long timeout, TimeUnit unit) throws InterruptedException;
}
