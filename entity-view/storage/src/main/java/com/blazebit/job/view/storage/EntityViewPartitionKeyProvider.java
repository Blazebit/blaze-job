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
package com.blazebit.job.view.storage;

import com.blazebit.job.ConfigurationSource;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.Partition;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.Partitions;
import com.blazebit.job.ServiceProvider;
import com.blazebit.job.spi.PartitionKeyProvider;
import com.blazebit.job.view.model.EntityViewPartitionKey;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.metamodel.ManagedViewType;
import com.blazebit.persistence.view.metamodel.ViewType;

import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.Metamodel;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A {@link PartitionKeyProvider} implementation that makes use of the JPA metamodel and configuration attributes for creating relevant {@link EntityViewPartitionKey}s.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class EntityViewPartitionKeyProvider implements PartitionKeyProvider {

    /**
     * Configuration property for the job trigger id attribute name.
     * The default value is "id".
     */
    public static final String JOB_TRIGGER_ID_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_trigger_id_attribute_name";
    /**
     * Configuration property for the job trigger schedule attribute name.
     * The default value is "scheduleTime".
     */
    public static final String JOB_TRIGGER_SCHEDULE_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_trigger_schedule_attribute_name";
    /**
     * Configuration property for the job trigger last execution time attribute name.
     * The default value is "lastExecutionTime".
     */
    public static final String JOB_TRIGGER_LAST_EXECUTION_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_trigger_last_execution_attribute_name";
    /**
     * Configuration property for the job trigger state attribute name.
     * The default value is "state".
     */
    public static final String JOB_TRIGGER_STATE_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_trigger_state_attribute_name";
    /**
     * Configuration property for a mapping function <code>Function&lt;JobInstanceState, Object&gt;</code> from job trigger state to the actual model state.
     * The default value is a function that just returns the passed in {@link JobInstanceState}.
     */
    public static final String JOB_TRIGGER_STATE_VALUE_MAPPING_FUNCTION_PROPERTY = "job.view.storage.job_trigger_state_value_mapping_function";
    /**
     * Configuration property for the job instance id attribute name.
     * The default value is "id".
     */
    public static final String JOB_INSTANCE_ID_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_instance_id_attribute_name";
    /**
     * Configuration property for the job instance partition key attribute name.
     * The default value is "id".
     */
    public static final String JOB_INSTANCE_PARTITION_KEY_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_instance_partition_key_attribute_name";
    /**
     * Configuration property for the job instance schedule attribute name.
     * The default value is "scheduleTime".
     */
    public static final String JOB_INSTANCE_SCHEDULE_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_instance_schedule_attribute_name";
    /**
     * Configuration property for the job instance last execution time attribute name.
     * The default value is "lastExecutionTime".
     */
    public static final String JOB_INSTANCE_LAST_EXECUTION_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_instance_last_execution_attribute_name";
    /**
     * Configuration property for the job instance state attribute name.
     * The default value is "state".
     */
    public static final String JOB_INSTANCE_STATE_ATTRIBUTE_NAME_PROPERTY = "job.view.storage.job_instance_state_attribute_name";
    /**
     * Configuration property for a mapping function <code>Function&lt;JobInstanceState, Object&gt;</code> from job instance state to the actual model state.
     * The default value is a function that just returns the passed in {@link JobInstanceState}.
     */
    public static final String JOB_INSTANCE_STATE_VALUE_MAPPING_FUNCTION_PROPERTY = "job.view.storage.job_instance_state_value_mapping_function";

    private final Map<String, PartitionKey> jobTriggerPartitionKeys;
    private final Map<String, PartitionKey> jobInstancePartitionKeys;

    /**
     * Creates a new partition key provider that makes use of the service provider and configuration source to determine the {@link EntityViewManager} and attribute names.
     *
     * @param serviceProvider     The service provider
     * @param configurationSource The configuration source
     */
    public EntityViewPartitionKeyProvider(ServiceProvider serviceProvider, ConfigurationSource configurationSource) {
        this(
            serviceProvider.getService(CriteriaBuilderFactory.class),
            serviceProvider.getService(EntityViewManager.class),
            configurationSource.getPropertyOrDefault(JOB_TRIGGER_ID_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "id"),
            configurationSource.getPropertyOrDefault(JOB_TRIGGER_SCHEDULE_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "scheduleTime"),
            configurationSource.getPropertyOrDefault(JOB_TRIGGER_LAST_EXECUTION_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "lastExecutionTime"),
            configurationSource.getPropertyOrDefault(JOB_TRIGGER_STATE_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "state"),
            configurationSource.getPropertyOrDefault(JOB_TRIGGER_STATE_VALUE_MAPPING_FUNCTION_PROPERTY, Function.class, null, o -> Function.identity()),
            configurationSource.getPropertyOrDefault(JOB_INSTANCE_ID_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "id"),
            configurationSource.getPropertyOrDefault(JOB_INSTANCE_PARTITION_KEY_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "id"),
            configurationSource.getPropertyOrDefault(JOB_INSTANCE_SCHEDULE_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "scheduleTime"),
            configurationSource.getPropertyOrDefault(JOB_INSTANCE_LAST_EXECUTION_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "lastExecutionTime"),
            configurationSource.getPropertyOrDefault(JOB_INSTANCE_STATE_ATTRIBUTE_NAME_PROPERTY, String.class, Function.identity(), o -> "state"),
            configurationSource.getPropertyOrDefault(JOB_INSTANCE_STATE_VALUE_MAPPING_FUNCTION_PROPERTY, Function.class, null, o -> Function.identity())
        );
    }

    /**
     * Creates a new partition key provider.
     *
     * @param criteriaBuilderFactory                The criteria builder factory
     * @param entityViewManager                     The entity view manager
     * @param jobTriggerIdAttributeName             The trigger id attribute name
     * @param jobTriggerScheduleAttributeName       The trigger schedule attribute name
     * @param jobTriggerLastExecutionAttributeName  The trigger last execution attribute name
     * @param jobTriggerStateAttributeName          The trigger state attribute name
     * @param jobTriggerStateValueMapper            The trigger state value mapping function
     * @param jobInstanceIdAttributeName            The job instance id attribute name
     * @param jobInstancePartitionKeyAttributeName  The job instance partition key attribute name
     * @param jobInstanceScheduleAttributeName      The job instance schedule attribute name
     * @param jobInstanceLastExecutionAttributeName The job instance last execution attribute name
     * @param jobInstanceStateAttributeName         The job instance state attribute name
     * @param jobInstanceStateValueMapper           The job instance state value mapping function
     */
    public EntityViewPartitionKeyProvider(CriteriaBuilderFactory criteriaBuilderFactory, EntityViewManager entityViewManager, String jobTriggerIdAttributeName, String jobTriggerScheduleAttributeName, String jobTriggerLastExecutionAttributeName, String jobTriggerStateAttributeName, Function<JobInstanceState, Object> jobTriggerStateValueMapper,
                                          String jobInstanceIdAttributeName, String jobInstancePartitionKeyAttributeName, String jobInstanceScheduleAttributeName, String jobInstanceLastExecutionAttributeName, String jobInstanceStateAttributeName, Function<JobInstanceState, Object> jobInstanceStateValueMapper) {
        if (entityViewManager == null) {
            throw new JobException("No entity view manager given!");
        }
        Map<String, PartitionKey> jobTriggerPartitionKeys = new TreeMap<>();
        Map<String, PartitionKey> jobInstancePartitionKeys = new TreeMap<>();
        Metamodel metamodel = criteriaBuilderFactory.getService(Metamodel.class);

        StringBuilder errors = new StringBuilder();
        for (ViewType<?> viewType : entityViewManager.getMetamodel().getViews()) {
            Class<? extends JobInstance<?>> viewJavaType = (Class<? extends JobInstance<?>>) viewType.getJavaType();
            if (JobInstance.class.isAssignableFrom(viewJavaType)) {
                Class<? extends JobInstance<?>> entityClass = (Class<? extends JobInstance<?>>) viewType.getEntityClass();
                String inheritanceMapping = viewType.getInheritanceMapping();
                Function<String, String> partitionKeyPredicateProvider;
                if (inheritanceMapping == null) {
                    Set<ManagedViewType<?>> inheritanceSubtypes = (Set<ManagedViewType<?>>) viewType.getInheritanceSubtypes();
                    // An inheritance enabled base view is not considered as partition key, only the subtypes
                    if (inheritanceSubtypes.size() > 1 || !inheritanceSubtypes.contains(viewType)) {
                        continue;
                    }
                    partitionKeyPredicateProvider = null;
                } else {
                    String[] parts = inheritanceMapping.split("this");
                    partitionKeyPredicateProvider = new PartsRenderingFunction(parts);
                }

                Partition[] partitions;
                Partitions annotation = viewJavaType.getAnnotation(Partitions.class);
                if (annotation == null) {
                    Partition partition = viewJavaType.getAnnotation(Partition.class);
                    if (partition == null) {
                        partitions = PartitionLiteral.INSTANCE;
                    } else {
                        partitions = new Partition[]{ partition };
                    }
                } else {
                    partitions = annotation.value();
                }

                for (Partition partition : partitions) {
                    String partitionNameBase = viewJavaType.getName();
                    if (!partition.name().isEmpty()) {
                        partitionNameBase = partition.name();
                    }
                    int processCount = partition.processCount();
                    int transactionTimeoutMillis = partition.transactionTimeoutMillis();
                    int temporaryErrorBackoffSeconds = partition.temporaryErrorBackoffSeconds();
                    int rateLimitBackoffSeconds = partition.rateLimitBackoffSeconds();

                    for (int i = 0; i < partition.partitionCount(); i++) {
                        String partitionName;
                        if (partition.partitionCount() > 1) {
                            partitionName = partitionNameBase + "-" + i;
                        } else {
                            partitionName = partitionNameBase;
                        }
                        EntityViewPartitionKey existingPartitionKey;
                        if (JobTrigger.class.isAssignableFrom(entityClass)) {
                            existingPartitionKey = (EntityViewPartitionKey) jobTriggerPartitionKeys.get(partitionName);
                        } else {
                            existingPartitionKey = (EntityViewPartitionKey) jobInstancePartitionKeys.get(partitionName);
                        }
                        if (existingPartitionKey != null) {
                            processCount = Math.max(processCount, existingPartitionKey.getProcessCount());
                            transactionTimeoutMillis = Math.max(transactionTimeoutMillis, existingPartitionKey.getTransactionTimeoutMillis());
                            temporaryErrorBackoffSeconds = Math.max(temporaryErrorBackoffSeconds, existingPartitionKey.getTemporaryErrorBackoffSeconds());
                            rateLimitBackoffSeconds = Math.max(rateLimitBackoffSeconds, existingPartitionKey.getRateLimitBackoffSeconds());
                            EntityType<?> entity = metamodel.entity(entityClass);
                            EntityType<?> existingEntity = metamodel.entity(existingPartitionKey.getEntityClass());
                            entityClass = (Class<? extends JobInstance<?>>) getCommonSuperclass(entity, existingEntity);
                            if (entityClass == null) {
                                errors.append("\n * The entity view type ").append(existingPartitionKey.getEntityView().getName()).append(" and ").append(viewJavaType.getName()).append(" use the same partition name '").append(partitionName).append("' but have no common entity super type which is necessary for querying!");
                                continue;
                            }
                            if (existingPartitionKey.getEntityView() != viewJavaType) {
                                errors.append("\n * The entity view type ").append(existingPartitionKey.getEntityView().getName()).append(" and ").append(viewJavaType.getName()).append(" use the same partition name '").append(partitionName).append("' which is disallowed!");
                                continue;
                            }
                            if (partitionKeyPredicateProvider == null) {
                                partitionKeyPredicateProvider = existingPartitionKey::getPartitionPredicate;
                            } else {
                                String existingPredicate = existingPartitionKey.getPartitionPredicate("e");
                                if (existingPredicate != null) {
                                    if (!existingPredicate.contains(partitionKeyPredicateProvider.apply("e"))) {
                                        Function<String, String> oldPartitionKeyPredicateProvider = partitionKeyPredicateProvider;
                                        partitionKeyPredicateProvider = alias -> oldPartitionKeyPredicateProvider.apply(alias) + " OR " + existingPartitionKey.getPartitionPredicate(alias);
                                    }
                                }
                            }
                            if (!partition.predicate().isEmpty()) {
                                String[] parts = partition.predicate().replace("{partition}", "" + i).split("\\{alias}");
                                PartsRenderingFunction additionalPartitionKeyPredicateProvider = new PartsRenderingFunction(parts);

                                String existingPredicate = partitionKeyPredicateProvider.apply("e");
                                if (existingPredicate != null && !existingPredicate.isEmpty()) {
                                    if (!existingPredicate.contains(partitionKeyPredicateProvider.apply("e"))) {
                                        Function<String, String> oldPartitionKeyPredicateProvider = partitionKeyPredicateProvider;
                                        partitionKeyPredicateProvider = alias -> "(" + oldPartitionKeyPredicateProvider.apply(alias) + ") AND " + additionalPartitionKeyPredicateProvider.apply(alias);
                                    }
                                } else {
                                    partitionKeyPredicateProvider = additionalPartitionKeyPredicateProvider;
                                }
                            }
                        }

                        if (JobTrigger.class.isAssignableFrom(viewJavaType)) {
                            jobTriggerPartitionKeys.put(partitionName,
                                EntityViewPartitionKey.builder()
                                    .withName(viewJavaType.getName())
                                    .withProcessCount(processCount)
                                    .withTransactionTimeoutMillis(transactionTimeoutMillis)
                                    .withTemporaryErrorBackoffSeconds(temporaryErrorBackoffSeconds)
                                    .withRateLimitBackoffSeconds(rateLimitBackoffSeconds)
                                    .withEntityClass(entityClass)
                                    .withEntityView(viewJavaType)
                                    .withJobInstanceType(viewJavaType)
                                    .withPartitionPredicateProvider(partitionKeyPredicateProvider)
                                    .withIdAttributeName(jobTriggerIdAttributeName)
                                    .withScheduleAttributeName(jobTriggerScheduleAttributeName)
                                    .withLastExecutionAttributeName(jobTriggerLastExecutionAttributeName)
                                    .withPartitionKeyAttributeName(jobTriggerIdAttributeName)
                                    .withStateAttributeName(jobTriggerStateAttributeName)
                                    .withStateValueMappingFunction(jobTriggerStateValueMapper)
                                    .build()
                            );
                        } else {
                            jobInstancePartitionKeys.put(partitionName,
                                EntityViewPartitionKey.builder()
                                    .withName(viewJavaType.getName())
                                    .withProcessCount(processCount)
                                    .withTransactionTimeoutMillis(transactionTimeoutMillis)
                                    .withTemporaryErrorBackoffSeconds(temporaryErrorBackoffSeconds)
                                    .withRateLimitBackoffSeconds(rateLimitBackoffSeconds)
                                    .withEntityClass(entityClass)
                                    .withEntityView(viewJavaType)
                                    .withJobInstanceType(viewJavaType)
                                    .withPartitionPredicateProvider(partitionKeyPredicateProvider)
                                    .withIdAttributeName(jobInstanceIdAttributeName)
                                    .withScheduleAttributeName(jobInstanceScheduleAttributeName)
                                    .withLastExecutionAttributeName(jobInstanceLastExecutionAttributeName)
                                    .withPartitionKeyAttributeName(jobInstancePartitionKeyAttributeName)
                                    .withStateAttributeName(jobInstanceStateAttributeName)
                                    .withStateValueMappingFunction(jobInstanceStateValueMapper)
                                    .build()
                            );
                        }
                    }
                }
            }
        }
        if (errors.length() != 0) {
            errors.insert(0, "There are errors in the job instance partition configuration:");
            throw new JobException(errors.toString());
        }

        this.jobTriggerPartitionKeys = jobTriggerPartitionKeys;
        this.jobInstancePartitionKeys = jobInstancePartitionKeys;
    }

    private static Class<?> getCommonSuperclass(IdentifiableType<?> t1, IdentifiableType<?> t2) {
        Class<?> class1 = t1.getJavaType();
        if (t1 == t2) {
            return class1;
        }
        Class<?> class2 = t2.getJavaType();
        if (class2.isAssignableFrom(class1)) {
            return class2;
        } else if (class1.isAssignableFrom(class2)) {
            return class1;
        } else {
            Class<?> c = null;
            if (t1.getSupertype() != null) {
                c = getCommonSuperclass(t1.getSupertype(), t2);
            }
            if (c == null && t2.getSupertype() != null) {
                c = getCommonSuperclass(t1, t2.getSupertype());
            }
            return c;
        }
    }

    @Override
    public Collection<PartitionKey> getDefaultTriggerPartitionKeys() {
        return jobTriggerPartitionKeys.values();
    }

    @Override
    public Collection<PartitionKey> getDefaultJobInstancePartitionKeys() {
        return jobInstancePartitionKeys.values();
    }

    /**
     * A partition literal.
     *
     * @author Christian Beikov
     * @since 1.0.0
     */
    private static class PartitionLiteral implements Partition {

        public static final Partition[] INSTANCE = new Partition[]{ new PartitionLiteral() };

        private PartitionLiteral() {
        }

        @Override
        public String name() {
            return "";
        }

        @Override
        public int processCount() {
            return 1;
        }

        @Override
        public String predicate() {
            return "";
        }

        @Override
        public int partitionCount() {
            return 1;
        }

        @Override
        public int transactionTimeoutMillis() {
            return -1;
        }

        @Override
        public int temporaryErrorBackoffSeconds() {
            return -1;
        }

        @Override
        public int rateLimitBackoffSeconds() {
            return -1;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Partition.class;
        }
    }

    /**
     * A part rendering function.
     *
     * @author Christian Beikov
     * @since 1.0.0
     */
    private static class PartsRenderingFunction implements Function<String, String> {

        private final String[] parts;

        /**
         * Creates the rendering function.
         *
         * @param parts The parts
         */
        public PartsRenderingFunction(String[] parts) {
            this.parts = parts;
        }

        @Override
        public String apply(String alias) {
            int length = parts.length;
            if (length == 1) {
                return parts[0];
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < length - 1; i++) {
                    sb.append(parts[i]).append(alias);
                }
                sb.append(parts[length - 1]);
                return sb.toString();
            }
        }
    }
}
