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
package com.blazebit.job.jpa.storage;

import com.blazebit.job.ConfigurationSource;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.Partition;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.Partitions;
import com.blazebit.job.ServiceProvider;
import com.blazebit.job.jpa.model.JpaPartitionKey;
import com.blazebit.job.spi.PartitionKeyProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.Metamodel;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * A {@link PartitionKeyProvider} implementation that makes use of the JPA metamodel and configuration attributes for creating relevant {@link JpaPartitionKey}s.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JpaPartitionKeyProvider implements PartitionKeyProvider {

    /**
     * Configuration property for the job trigger id attribute name.
     * The default value is "id".
     */
    public static final String JOB_TRIGGER_ID_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_trigger_id_attribute_name";
    /**
     * Configuration property for the job trigger schedule attribute name.
     * The default value is "scheduleTime".
     */
    public static final String JOB_TRIGGER_SCHEDULE_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_trigger_schedule_attribute_name";
    /**
     * Configuration property for the job trigger last execution time attribute name.
     * The default value is "lastExecutionTime".
     */
    public static final String JOB_TRIGGER_LAST_EXECUTION_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_trigger_last_execution_attribute_name";
    /**
     * Configuration property for the job trigger state attribute name.
     * The default value is "state".
     */
    public static final String JOB_TRIGGER_STATE_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_trigger_state_attribute_name";
    /**
     * Configuration property for a mapping function <code>Function&lt;JobInstanceState, Object&gt;</code> from job trigger state to the actual model state.
     * The default value is a function that just returns the passed in {@link JobInstanceState}.
     */
    public static final String JOB_TRIGGER_STATE_VALUE_MAPPING_FUNCTION_PROPERTY = "job.jpa.storage.job_trigger_state_value_mapping_function";
    /**
     * Configuration property for the job instance id attribute name.
     * The default value is "id".
     */
    public static final String JOB_INSTANCE_ID_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_instance_id_attribute_name";
    /**
     * Configuration property for the job instance partition key attribute name.
     * The default value is "id".
     */
    public static final String JOB_INSTANCE_PARTITION_KEY_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_instance_partition_key_attribute_name";
    /**
     * Configuration property for the job instance schedule attribute name.
     * The default value is "scheduleTime".
     */
    public static final String JOB_INSTANCE_SCHEDULE_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_instance_schedule_attribute_name";
    /**
     * Configuration property for the job instance last execution time attribute name.
     * The default value is "lastExecutionTime".
     */
    public static final String JOB_INSTANCE_LAST_EXECUTION_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_instance_last_execution_attribute_name";
    /**
     * Configuration property for the job instance state attribute name.
     * The default value is "state".
     */
    public static final String JOB_INSTANCE_STATE_ATTRIBUTE_NAME_PROPERTY = "job.jpa.storage.job_instance_state_attribute_name";
    /**
     * Configuration property for a mapping function <code>Function&lt;JobInstanceState, Object&gt;</code> from job instance state to the actual model state.
     * The default value is a function that just returns the passed in {@link JobInstanceState}.
     */
    public static final String JOB_INSTANCE_STATE_VALUE_MAPPING_FUNCTION_PROPERTY = "job.jpa.storage.job_instance_state_value_mapping_function";

    private final Map<String, PartitionKey> jobTriggerPartitionKeys;
    private final Map<String, PartitionKey> jobInstancePartitionKeys;

    /**
     * Creates a new partition key provider that makes use of the service provider and configuration source to determine the {@link EntityManager} and attribute names.
     *
     * @param serviceProvider     The service provider
     * @param configurationSource The configuration source
     */
    public JpaPartitionKeyProvider(ServiceProvider serviceProvider, ConfigurationSource configurationSource) {
        this(
            serviceProvider.getService(EntityManager.class),
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
     * @param entityManager                         The entity manager
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
    public JpaPartitionKeyProvider(EntityManager entityManager, String jobTriggerIdAttributeName, String jobTriggerScheduleAttributeName, String jobTriggerLastExecutionAttributeName, String jobTriggerStateAttributeName, Function<JobInstanceState, Object> jobTriggerStateValueMapper,
                                   String jobInstanceIdAttributeName, String jobInstancePartitionKeyAttributeName, String jobInstanceScheduleAttributeName, String jobInstanceLastExecutionAttributeName, String jobInstanceStateAttributeName, Function<JobInstanceState, Object> jobInstanceStateValueMapper) {
        if (entityManager == null) {
            throw new JobException("No entity manager given!");
        }

        Map<String, PartitionKey> jobTriggerPartitionKeys = new TreeMap<>();
        Map<String, PartitionKey> jobInstancePartitionKeys = new TreeMap<>();
        Map<EntityType<?>, List<EntityType<?>>> entitySubtypeMap = new HashMap<>();
        Metamodel metamodel = entityManager.getMetamodel();
        for (EntityType<?> entity : metamodel.getEntities()) {
            Class<?> javaType = entity.getJavaType();
            // We only query non-abstract entity types
            if (javaType != null && !Modifier.isAbstract(javaType.getModifiers())) {
                if (JobTrigger.class.isAssignableFrom(javaType) || JobInstance.class.isAssignableFrom(javaType)) {
                    List<EntityType<?>> subtypes = new ArrayList<>();
                    entitySubtypeMap.put(entity, subtypes);
                    while (entity.getSupertype() instanceof EntityType<?>) {
                        EntityType<?> supertype = (EntityType<?>) entity.getSupertype();
                        Class<?> supertypeJavaType = supertype.getJavaType();
                        if (supertypeJavaType != null && !Modifier.isAbstract(supertypeJavaType.getModifiers())) {
                            List<EntityType<?>> superSubtypes = entitySubtypeMap.compute(supertype, (e, list) -> list == null ? new ArrayList<>() : list);
                            superSubtypes.add(entity);
                            if (subtypes != null) {
                                superSubtypes.addAll(subtypes);
                            }

                            entity = supertype;
                            subtypes = entitySubtypeMap.get(entity);
                        } else {
                            entity = supertype;
                            if (subtypes.isEmpty()) {
                                subtypes = entitySubtypeMap.get(entity);
                            } else {
                                // We propagate all subtypes up to non-abstract supertypes
                                subtypes = new ArrayList<>(subtypes);
                                subtypes.addAll(entitySubtypeMap.get(entity));
                            }
                        }
                    }
                }
            }
        }

        StringBuilder errors = new StringBuilder();
        for (Map.Entry<EntityType<?>, List<EntityType<?>>> entry : entitySubtypeMap.entrySet()) {
            EntityType<?> entity = entry.getKey();
            Class<? extends JobInstance<?>> javaType = (Class<? extends JobInstance<?>>) entity.getJavaType();
            Function<String, String> partitionKeyPredicateProvider;
            if (entry.getValue().isEmpty()) {
                partitionKeyPredicateProvider = null;
            } else {
                partitionKeyPredicateProvider = alias -> "TYPE(" + alias + ") = " + entity.getName();
            }


            Partition[] partitions;
            Partitions annotation = javaType.getAnnotation(Partitions.class);
            if (annotation == null) {
                Partition partition = javaType.getAnnotation(Partition.class);
                if (partition == null) {
                    partitions = PartitionLiteral.INSTANCE;
                } else {
                    partitions = new Partition[]{ partition };
                }
            } else {
                partitions = annotation.value();
            }

            for (Partition partition : partitions) {
                String partitionNameBase = javaType.getName();
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

                    JpaPartitionKey existingPartitionKey;
                    if (JobTrigger.class.isAssignableFrom(javaType)) {
                        existingPartitionKey = (JpaPartitionKey) jobTriggerPartitionKeys.get(partitionName);
                    } else {
                        existingPartitionKey = (JpaPartitionKey) jobInstancePartitionKeys.get(partitionName);
                    }
                    if (existingPartitionKey != null) {
                        processCount = Math.max(processCount, existingPartitionKey.getProcessCount());
                        transactionTimeoutMillis = Math.max(transactionTimeoutMillis, existingPartitionKey.getTransactionTimeoutMillis());
                        temporaryErrorBackoffSeconds = Math.max(temporaryErrorBackoffSeconds, existingPartitionKey.getTemporaryErrorBackoffSeconds());
                        rateLimitBackoffSeconds = Math.max(rateLimitBackoffSeconds, existingPartitionKey.getRateLimitBackoffSeconds());
                        EntityType<?> existingEntity = metamodel.entity(existingPartitionKey.getEntityClass());
                        javaType = (Class<? extends JobInstance<?>>) getCommonSuperclass(entity, existingEntity);
                        if (javaType == null) {
                            errors.append("\n * The entity type " + existingEntity.getName() + " and " + entity.getName() + " use the same partition name '" + partitionName + "' but have no common super type which is necessary for querying!");
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

                    if (JobTrigger.class.isAssignableFrom(javaType)) {
                        jobTriggerPartitionKeys.put(partitionName,
                            JpaPartitionKey.builder()
                                .withName(partitionName)
                                .withEntityClass(javaType)
                                .withProcessCount(processCount)
                                .withTransactionTimeoutMillis(transactionTimeoutMillis)
                                .withTemporaryErrorBackoffSeconds(temporaryErrorBackoffSeconds)
                                .withRateLimitBackoffSeconds(rateLimitBackoffSeconds)
                                .withJobInstanceType(javaType)
                                .withPartitionPredicateProvider(partitionKeyPredicateProvider)
                                .withIdAttributeName(jobTriggerIdAttributeName)
                                .withScheduleAttributeName(jobTriggerScheduleAttributeName)
                                .withLastExecutionAttributeName(jobTriggerLastExecutionAttributeName)
                                .withPartitionKeyAttributeName(jobTriggerIdAttributeName)
                                .withStateAttributeName(jobTriggerStateAttributeName)
                                .withStateValueMappingFunction(jobTriggerStateValueMapper)
                                .build()
                        );
                    } else if (JobInstance.class.isAssignableFrom(javaType)) {
                        jobInstancePartitionKeys.put(partitionName,
                            JpaPartitionKey.builder()
                                .withName(partitionName)
                                .withEntityClass(javaType)
                                .withProcessCount(processCount)
                                .withTransactionTimeoutMillis(transactionTimeoutMillis)
                                .withTemporaryErrorBackoffSeconds(temporaryErrorBackoffSeconds)
                                .withRateLimitBackoffSeconds(rateLimitBackoffSeconds)
                                .withJobInstanceType(javaType)
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
