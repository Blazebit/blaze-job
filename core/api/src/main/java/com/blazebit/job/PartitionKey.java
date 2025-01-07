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

import java.util.Collections;
import java.util.Set;

/**
 * A testable description of a subset of job instances.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface PartitionKey {

    /**
     * Returns the name of the partition key that is unique and used for configuration of the scheduler.
     *
     * @return the name of the partition key
     */
    String getName();

    /**
     * Returns the number of jobs to schedule in parallel within one scheduler transaction.
     *
     * @return The number of job to schedule
     */
    default int getProcessCount() {
        return 1;
    }

    /**
     * Returns the concrete job instance type that is described by this partition key.
     *
     * @return the concrete job instance type
     */
    default Set<Class<? extends JobInstance<?>>> getJobInstanceTypes() {
        return (Set<Class<? extends JobInstance<?>>>) (Set<?>) Collections.singleton(JobInstance.class);
    }

    /**
     * Returns whether the given job instance is part of this partition key.
     *
     * @param jobInstance The job instance to test
     * @return whether the given job instance is part of this partition key
     */
    boolean matches(JobInstance<?> jobInstance);

    /**
     * The transaction timeout for job processing of the partition.
     * When -1, the default transaction timeout is used.
     *
     * @return The transaction timeout
     */
    default int getTransactionTimeoutMillis() {
        return -1;
    }

    /**
     * The amount of seconds to backoff when a job processor throws a {@link JobTemporaryException}.
     * When -1, the default temporary error backoff is used.
     *
     * @return The temporary error backoff
     */
    default int getTemporaryErrorBackoffSeconds() {
        return -1;
    }

    /**
     * The amount of seconds to backoff when a job processor throws a {@link JobRateLimitException}.
     * When -1, the default rate limit backoff is used.
     *
     * @return The rate limit backoff
     */
    default int getRateLimitBackoffSeconds() {
        return -1;
    }

}
