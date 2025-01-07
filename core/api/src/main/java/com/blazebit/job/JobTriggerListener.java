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
 * A listener for job trigger processing events.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobTriggerListener extends JobInstanceListener {

    @Override
    default void onJobInstanceChunkSuccess(JobInstance<?> jobInstance, JobInstanceProcessingContext<?> context) {
        if (jobInstance instanceof JobTrigger) {
            onJobTriggerSuccess((JobTrigger) jobInstance, context.getJobContext());
        }
    }

    @Override
    default void onJobInstanceError(JobInstance<?> jobInstance, JobInstanceProcessingContext<?> context) {
        if (jobInstance instanceof JobTrigger) {
            onJobTriggerError((JobTrigger) jobInstance, context.getJobContext());
        }
    }

    @Override
    default void onJobInstanceSuccess(JobInstance<?> jobInstance, JobInstanceProcessingContext<?> context) {
        if (jobInstance instanceof JobTrigger) {
            onJobTriggerEnded((JobTrigger) jobInstance, context.getJobContext());
        }
    }

    /**
     * A callback for a trigger after an error happened during job triggering.
     *
     * @param jobTrigger The job trigger
     * @param context The job context
     */
    void onJobTriggerError(JobTrigger jobTrigger, JobContext context);

    /**
     * A callback for a trigger after a job was successfully triggered.
     *
     * @param jobTrigger The job trigger
     * @param context The job context
     */
    void onJobTriggerSuccess(JobTrigger jobTrigger, JobContext context);

    /**
     * A callback for a trigger after the job trigger schedule ended.
     *
     * @param jobTrigger The job trigger
     * @param context The job context
     */
    void onJobTriggerEnded(JobTrigger jobTrigger, JobContext context);

}
