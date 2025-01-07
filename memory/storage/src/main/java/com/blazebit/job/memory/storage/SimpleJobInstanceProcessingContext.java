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

package com.blazebit.job.memory.storage;

import com.blazebit.job.JobContext;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.PartitionKey;

/**
 * A simple {@link JobInstanceProcessingContext} implementation.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class SimpleJobInstanceProcessingContext implements JobInstanceProcessingContext<Object> {

    private final JobContext jobContext;
    private final JobInstance<?> jobInstance;

    /**
     * Create a simple instance.
     *
     * @param jobContext The job context
     * @param jobInstance The job instance
     */
    public SimpleJobInstanceProcessingContext(JobContext jobContext, JobInstance<?> jobInstance) {
        this.jobContext = jobContext;
        this.jobInstance = jobInstance;
    }

    @Override
    public JobContext getJobContext() {
        return jobContext;
    }

    @Override
    public Object getLastProcessed() {
        return jobInstance.getLastProcessed();
    }

    @Override
    public int getProcessCount() {
        return 0;
    }

    @Override
    public int getPartitionId() {
        return 0;
    }

    @Override
    public int getPartitionCount() {
        return 0;
    }

    @Override
    public PartitionKey getPartitionKey() {
        return null;
    }
}
