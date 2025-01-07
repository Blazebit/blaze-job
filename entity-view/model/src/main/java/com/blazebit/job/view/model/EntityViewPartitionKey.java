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

package com.blazebit.job.view.model;

import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.PartitionKey;
import com.blazebit.persistence.WhereBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A description of a subset of job instances that can be applied to a query.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface EntityViewPartitionKey extends PartitionKey {

    /**
     * Returns the entity view class to use for fetching jobs with this partition key.
     *
     * @return the entity view class
     */
    Class<? extends JobInstance<?>> getEntityView();

    /**
     * Returns the entity class for the job instance type.
     *
     * @return the entity class
     */
    Class<? extends JobInstance<?>> getEntityClass();

    /**
     * A JPQL predicate for filtering for this partition.
     * The job alias refers to an entity of the type as given in {@link #getEntityClass()}.
     *
     * @param jobAlias The FROM clause alias for the job
     * @return The partition JPQL predicate or an empty string
     */
    String getPartitionPredicate(String jobAlias);

    /**
     * Returns the attribute name of the identifier attribute of the entity type as given in {@link #getEntityClass()}.
     *
     * @return the attribute name of the identifier attribute
     */
    String getIdAttributeName();

    /**
     * Returns the attribute name of the schedule attribute of the entity type as given in {@link #getEntityClass()}.
     *
     * @return the attribute name of the schedule attribute
     */
    String getScheduleAttributeName();

    /**
     * Returns the attribute name of the last execution attribute of the entity type as given in {@link #getEntityClass()}.
     *
     * @return the attribute name of the last execution attribute
     */
    String getLastExecutionAttributeName();

    /**
     * Returns the attribute name of the partition key attribute of the entity type as given in {@link #getEntityClass()}.
     *
     * @return the attribute name of the partition key attribute
     */
    String getPartitionKeyAttributeName();

    /**
     * Applies the predicate for matching only jobs that are ready to process.
     *
     * @param jobAlias The FROM clause alias for the job
     * @param whereBuilder The where builder to apply the predicate to
     */
    void applyStatePredicate(String jobAlias, WhereBuilder<?> whereBuilder);

    /**
     * Returns the expression for the state of a job.
     *
     * @param jobAlias The FROM clause alias for the job
     * @return The state JPQL expression or an empty string
     */
    String getStateExpression(String jobAlias);

    /**
     * Returns the state value for ready jobs that must be bound in a query to the parameter name "readyState".
     * A <code>null</code> value means that no parameter should be bound.
     *
     * @return The ready state value of <code>null</code>
     */
    Function<JobInstanceState, Object> getStateValueMappingFunction();

    @Override
    default boolean matches(JobInstance<?> jobInstance) {
        throw new UnsupportedOperationException("A EntityViewPartitionKey does not need to support this!");
    }

    /**
     * A builder for {@link EntityViewPartitionKey} instances.
     *
     * @author Christian Beikov
     * @since 1.0.0
     */
    interface EntityViewPartitionKeyBuilder {
        /**
         * Sets the given name as partition name.
         *
         * @param name The name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withName(String name);

        /**
         * Sets the number of jobs to schedule in parallel within one scheduler transaction.
         *
         * @param processCount The number of jobs to process
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withProcessCount(int processCount);

        /**
         * Sets the given transaction timeout.
         *
         * @param transactionTimeoutMillis The job id attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withTransactionTimeoutMillis(int transactionTimeoutMillis);

        /**
         * Sets the given temporary error backoff.
         *
         * @param temporaryErrorBackoffSeconds The job id attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withTemporaryErrorBackoffSeconds(int temporaryErrorBackoffSeconds);

        /**
         * Sets the given rate limit backoff.
         *
         * @param rateLimitBackoffSeconds The job id attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withRateLimitBackoffSeconds(int rateLimitBackoffSeconds);

        /**
         * Sets the given class as entity view class.
         *
         * @param entityViewClass The entity view class
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withEntityView(Class<? extends JobInstance<?>> entityViewClass);

        /**
         * Sets the given class as entity class.
         *
         * @param entityClass The entity class
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withEntityClass(Class<? extends JobInstance<?>> entityClass);

        /**
         * Sets the given job instance type.
         *
         * @param jobInstanceType The job instance type
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withJobInstanceType(Class<? extends JobInstance<?>> jobInstanceType);

        /**
         * Sets the given partition predicate provider.
         *
         * @param partitionPredicateProvider The partition predicate provider
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withPartitionPredicateProvider(Function<String, String> partitionPredicateProvider);

        /**
         * Sets the given job id attribute name.
         *
         * @param idAttributeName The job id attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withIdAttributeName(String idAttributeName);

        /**
         * Sets the given job schedule attribute name.
         *
         * @param scheduleAttributeName The job schedule attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withScheduleAttributeName(String scheduleAttributeName);

        /**
         * Sets the given job last execution attribute name.
         *
         * @param lastExecutionAttributeName The job last execution attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withLastExecutionAttributeName(String lastExecutionAttributeName);

        /**
         * Sets the given job partition key attribute name.
         *
         * @param partitionKeyAttributeName The job partition key attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withPartitionKeyAttributeName(String partitionKeyAttributeName);

        /**
         * Sets the given job state attribute name.
         *
         * @param stateAttributeName The job state attribute name
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withStateAttributeName(String stateAttributeName);

        /**
         * Sets the given state value mapping function.
         *
         * @param stateValueMappingFunction The state value mapping function
         * @return this for chaining
         */
        EntityViewPartitionKeyBuilder withStateValueMappingFunction(Function<JobInstanceState, Object> stateValueMappingFunction);

        /**
         * Returns a new {@link EntityViewPartitionKey} for the configuration of this builder.
         *
         * @return the {@link EntityViewPartitionKey}
         */
        EntityViewPartitionKey build();
    }

    /**
     * Returns a new builder for a new {@link EntityViewPartitionKey}.
     *
     * @return the builder
     */
    static EntityViewPartitionKeyBuilder builder() {
        return new EntityViewPartitionKeyBuilder() {
            String name0;
            int processCount0 = 1;
            int transactionTimeoutMillis0 = -1;
            int temporaryErrorBackoffSeconds0 = -1;
            int rateLimitBackoffSeconds0 = -1;
            Class<? extends JobInstance<?>> entityView0;
            Class<? extends JobInstance<?>> entityClass0;
            Set<Class<? extends JobInstance<?>>> jobInstanceTypes0 = new HashSet<>();
            Function<String, String> partitionPredicateProvider0;
            String idAttributeName0;
            String scheduleAttributeName0;
            String lastExecutionAttributeName0;
            String partitionKeyAttributeName0;
            String stateAttributeName0;
            Function<JobInstanceState, Object> stateValueMappingFunction0;

            @Override
            public EntityViewPartitionKeyBuilder withName(String name) {
                this.name0 = name;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withProcessCount(int processCount) {
                this.processCount0 = processCount;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withTransactionTimeoutMillis(int transactionTimeoutMillis) {
                this.transactionTimeoutMillis0 = transactionTimeoutMillis;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withTemporaryErrorBackoffSeconds(int temporaryErrorBackoffSeconds) {
                this.temporaryErrorBackoffSeconds0 = temporaryErrorBackoffSeconds;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withRateLimitBackoffSeconds(int rateLimitBackoffSeconds) {
                this.rateLimitBackoffSeconds0 = rateLimitBackoffSeconds;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withEntityView(Class<? extends JobInstance<?>> entityViewClass) {
                this.entityView0 = entityViewClass;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withEntityClass(Class<? extends JobInstance<?>> entityClass) {
                this.entityClass0 = entityClass;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withJobInstanceType(Class<? extends JobInstance<?>> jobInstanceType) {
                this.jobInstanceTypes0.add(jobInstanceType);
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withPartitionPredicateProvider(Function<String, String> partitionPredicateProvider) {
                this.partitionPredicateProvider0 = partitionPredicateProvider;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withIdAttributeName(String idAttributeName) {
                this.idAttributeName0 = idAttributeName;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withScheduleAttributeName(String scheduleAttributeName) {
                this.scheduleAttributeName0 = scheduleAttributeName;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withLastExecutionAttributeName(String lastExecutionAttributeName) {
                this.lastExecutionAttributeName0 = lastExecutionAttributeName;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withPartitionKeyAttributeName(String partitionKeyAttributeName) {
                this.partitionKeyAttributeName0 = partitionKeyAttributeName;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withStateAttributeName(String stateAttributeName) {
                this.stateAttributeName0 = stateAttributeName;
                return this;
            }

            @Override
            public EntityViewPartitionKeyBuilder withStateValueMappingFunction(Function<JobInstanceState, Object> stateValueMappingFunction) {
                this.stateValueMappingFunction0 = stateValueMappingFunction;
                return this;
            }

            @Override
            public EntityViewPartitionKey build() {
                return new EntityViewPartitionKey() {
                    private final String name = name0;
                    private final int processCount = processCount0;
                    private final int transactionTimeoutMillis = transactionTimeoutMillis0;
                    private final int temporaryErrorBackoffSeconds = temporaryErrorBackoffSeconds0;
                    private final int rateLimitBackoffSeconds = rateLimitBackoffSeconds0;
                    private final Class<? extends JobInstance<?>> entityView = entityView0;
                    private final Class<? extends JobInstance<?>> entityClass = entityClass0;
                    private final Set<Class<? extends JobInstance<?>>> jobInstanceTypes = new HashSet<>(jobInstanceTypes0);
                    private final Function<String, String> partitionPredicateProvider = partitionPredicateProvider0;
                    private final String idAttributeName = idAttributeName0;
                    private final String scheduleAttributeName = scheduleAttributeName0;
                    private final String lastExecutionAttributeName = lastExecutionAttributeName0;
                    private final String partitionKeyAttributeName = partitionKeyAttributeName0;
                    private final String stateAttributeName = stateAttributeName0;
                    private final Function<JobInstanceState, Object> stateValueMappingFunction = stateValueMappingFunction0;

                    @Override
                    public String getName() {
                        return name;
                    }

                    @Override
                    public int getProcessCount() {
                        return processCount;
                    }

                    @Override
                    public Class<? extends JobInstance<?>> getEntityView() {
                        return entityView;
                    }

                    @Override
                    public Set<Class<? extends JobInstance<?>>> getJobInstanceTypes() {
                        return jobInstanceTypes;
                    }

                    @Override
                    public int getTransactionTimeoutMillis() {
                        return transactionTimeoutMillis;
                    }

                    @Override
                    public int getTemporaryErrorBackoffSeconds() {
                        return temporaryErrorBackoffSeconds;
                    }

                    @Override
                    public int getRateLimitBackoffSeconds() {
                        return rateLimitBackoffSeconds;
                    }

                    @Override
                    public Class<? extends JobInstance<?>> getEntityClass() {
                        return entityClass;
                    }

                    @Override
                    public String getPartitionPredicate(String jobAlias) {
                        return partitionPredicateProvider == null ? "" : partitionPredicateProvider.apply(jobAlias);
                    }

                    @Override
                    public String getIdAttributeName() {
                        return idAttributeName;
                    }

                    @Override
                    public String getScheduleAttributeName() {
                        return scheduleAttributeName;
                    }

                    @Override
                    public String getLastExecutionAttributeName() {
                        return lastExecutionAttributeName;
                    }

                    @Override
                    public String getPartitionKeyAttributeName() {
                        return partitionKeyAttributeName;
                    }

                    @Override
                    public void applyStatePredicate(String jobAlias, WhereBuilder<?> whereBuilder) {
                        Object stateValue = stateValueMappingFunction.apply(JobInstanceState.NEW);
                        if (stateValue instanceof List<?>) {
                            whereBuilder.where(jobAlias + "." + stateAttributeName).inLiterals((List<?>) stateValue);
                        } else {
                            whereBuilder.where(jobAlias + "." + stateAttributeName).eqLiteral(stateValue);
                        }
                    }

                    @Override
                    public String getStateExpression(String jobAlias) {
                        return jobAlias + "." + stateAttributeName;
                    }

                    @Override
                    public Function<JobInstanceState, Object> getStateValueMappingFunction() {
                        return stateValueMappingFunction;
                    }

                    @Override
                    public String toString() {
                        return name;
                    }
                };
            }
        };
    }
}
