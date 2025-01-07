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
 * A listener for job instance processing events.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobInstanceListener {

    /**
     * A callback for a job instance after a chunk was successfully processed.
     *
     * @param jobInstance The job instance
     * @param context The job instance processing context
     */
    void onJobInstanceChunkSuccess(JobInstance<?> jobInstance, JobInstanceProcessingContext<?> context);

    /**
     * A callback for a job instance after an error happened during job instance processing.
     *
     * @param jobInstance The job instance
     * @param context The job instance processing context
     */
    void onJobInstanceError(JobInstance<?> jobInstance, JobInstanceProcessingContext<?> context);

    /**
     * A callback for a job instance after job instance processing finished for the job instance.
     *
     * @param jobInstance The job instance
     * @param context The job instance processing context
     */
    void onJobInstanceSuccess(JobInstance<?> jobInstance, JobInstanceProcessingContext<?> context);

}
