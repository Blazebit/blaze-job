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
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceProcessor;

/**
 * A factory for creating job instance processors for a job instance.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobInstanceProcessorFactory {

    /**
     * Creates a job instance processor for the given job instance.
     *
     * @param jobContext The job context
     * @param jobInstance The job instance
     * @param <T> The job instance type
     * @return The job processor
     * @throws JobException if the job instance processor can't be created
     */
    <T extends JobInstance<?>> JobInstanceProcessor<?, T> createJobInstanceProcessor(JobContext jobContext, T jobInstance);

    /**
     * Creates a job instance processor factory that always returns the given job instance processor.
     *
     * @param jobInstanceProcessor The job instance processor to return
     * @return the job instance processor factory
     */
    static JobInstanceProcessorFactory of(JobInstanceProcessor<?, ?> jobInstanceProcessor) {
        return new JobInstanceProcessorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends JobInstance<?>> JobInstanceProcessor<?, T> createJobInstanceProcessor(JobContext jobContext, T jobInstance) {
                return (JobInstanceProcessor<?, T>) jobInstanceProcessor;
            }
        };
    }
}
