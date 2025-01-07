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

import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobProcessor;
import com.blazebit.job.JobTrigger;

/**
 * A factory for creating job processors for a job trigger.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobProcessorFactory {

    /**
     * Creates a job processor for the given job trigger.
     *
     * @param jobContext The job context
     * @param jobTrigger The job trigger
     * @param <T> The job instance type
     * @return The job processor
     * @throws JobException if the job processor can't be created
     */
    <T extends JobTrigger> JobProcessor<T> createJobProcessor(JobContext jobContext, T jobTrigger);

    /**
     * Creates a job processor factory that always returns the given job processor.
     *
     * @param jobProcessor The job processor to return
     * @return the job processor factory
     */
    static JobProcessorFactory of(JobProcessor<?> jobProcessor) {
        return new JobProcessorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends JobTrigger> JobProcessor<T> createJobProcessor(JobContext jobContext, T jobTrigger) {
                return (JobProcessor<T>) jobProcessor;
            }
        };
    }
}
