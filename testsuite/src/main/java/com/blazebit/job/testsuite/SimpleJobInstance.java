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

package com.blazebit.job.testsuite;

import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.memory.model.AbstractJobInstance;
import com.blazebit.job.memory.model.JobConfiguration;

import java.time.Instant;

public class SimpleJobInstance extends AbstractJobInstance<Long> {

    private JobConfiguration jobConfiguration = new JobConfiguration();
    private boolean longRunning;

    public SimpleJobInstance() {
        this(false);
    }

    public SimpleJobInstance(boolean longRunning) {
        setCreationTime(Instant.now());
        setScheduleTime(getCreationTime());
        this.longRunning = longRunning;
    }

    @Override
    public boolean isLongRunning() {
        return longRunning;
    }

    @Override
    public Long getPartitionKey() {
        return getId();
    }

    @Override
    public JobConfiguration getJobConfiguration() {
        return jobConfiguration;
    }

    @Override
    public void onChunkSuccess(JobInstanceProcessingContext<?> processingContext) {
    }
}
