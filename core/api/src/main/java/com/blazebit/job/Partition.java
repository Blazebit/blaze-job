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

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link PartitionKey} configuration for a {@link JobInstance} class.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@Repeatable(Partitions.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Partition {

    /**
     * The partition name. If empty, uses the class name of the annotated {@link JobInstance}.
     *
     * @return The partition name
     */
    String name() default "";

    /**
     * Defines the partition predicate to use for fetching jobs for the partition.
     * The string <code>{alias}</code> is replaced with the job instance alias.
     * The string <code>{partition}</code> is replaced with the zero-based partition number.
     *
     * @return The partition predicate
     */
    String predicate() default "";

    /**
     * The number of jobs to schedule in parallel within one scheduler transaction.
     *
     * @return The number of job to schedule
     */
    int processCount() default 1;

    /**
     * Defines how many partitions should be created. If the value is greater than 1,
     * it will create partitions with a name that is suffixed by the partition number, starting at 0.
     *
     * @return The number of partitions ot create
     */
    int partitionCount() default 1;

    /**
     * The transaction timeout for job processing of the partition.
     * When -1, the default transaction timeout is used.
     *
     * @return The transaction timeout
     */
    int transactionTimeoutMillis() default -1;

    /**
     * The amount of seconds to backoff when a job processor throws a {@link JobTemporaryException}.
     * When -1, the default temporary error backoff is used.
     *
     * @return The temporary error backoff
     */
    int temporaryErrorBackoffSeconds() default -1;

    /**
     * The amount of seconds to backoff when a job processor throws a {@link JobRateLimitException}.
     * When -1, the default rate limit backoff is used.
     *
     * @return The rate limit backoff
     */
    int rateLimitBackoffSeconds() default -1;

}
