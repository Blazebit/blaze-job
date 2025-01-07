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

import com.blazebit.actor.ActorContext;
import com.blazebit.job.JobContext;
import com.blazebit.job.PartitionKey;

/**
 * Interface implemented by the job implementation provider.
 *
 * Implementations are instantiated via {@link java.util.ServiceLoader}.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobSchedulerFactory {

    /**
     * Creates a job scheduler for the given actor and partition.
     *
     * @param context The job context
     * @param actorContext The actor context
     * @param actorName The actor name
     * @param processCount The amount of jobs to process at once
     * @param partitionKey The partition
     * @return A job scheduler
     */
    JobScheduler createJobScheduler(JobContext context, ActorContext actorContext, String actorName, int processCount, PartitionKey partitionKey);
}
