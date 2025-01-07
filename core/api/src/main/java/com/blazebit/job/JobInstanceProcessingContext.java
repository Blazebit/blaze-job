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
 * A context for the processing of a job instance.
 *
 * @param <T> The processing result type
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JobInstanceProcessingContext<T> {

    /**
     * Returns the job context.
     *
     * @return the job context
     */
    JobContext getJobContext();

    /**
     * Returns the last processed object.
     *
     * @return the last processed object
     */
    T getLastProcessed();

    /**
     * Returns the amount of objects to process.
     *
     * @return the amount of objects to process
     */
    int getProcessCount();

    /**
     * Returns the partition id that is between 0 and {@link #getPartitionCount()}.
     *
     * @return the partition id
     */
    int getPartitionId();

    /**
     * Returns the partition count roughly representing the number of parallel job instance processors that are allowed to run.
     *
     * @return the partition count
     */
    int getPartitionCount();

    /**
     * Returns the partition key representing the subset of the job instances that are processable.
     *
     * @return the partition key
     */
    PartitionKey getPartitionKey();
}
