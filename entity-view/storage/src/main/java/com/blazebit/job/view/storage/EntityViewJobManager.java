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

import com.blazebit.job.JobConfiguration;
import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobManager;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.Schedule;
import com.blazebit.job.spi.TransactionSupport;
import com.blazebit.job.view.model.EntityViewPartitionKey;
import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.metamodel.EntityType;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JPA based implementation of the {@link JobManager} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class EntityViewJobManager implements JobManager {

    private final JobContext jobContext;
    private final Clock clock;
    private final EntityManager entityManager;
    private final EntityViewManager entityViewManager;
    private final CriteriaBuilderFactory criteriaBuilderFactory;
    private final Set<Class<?>> entityClasses;

    /**
     * Creates a job manager that makes use of the job context to determine the {@link EntityManager}.
     *
     * @param jobContext The job context
     */
    public EntityViewJobManager(JobContext jobContext) {
        this(
            jobContext,
            jobContext.getService(EntityManager.class),
            jobContext.getService(EntityViewManager.class),
            jobContext.getService(CriteriaBuilderFactory.class)
        );
    }

    /**
     * Creates a job manager.
     *
     * @param jobContext             The job context
     * @param entityManager          The entity manager
     * @param entityViewManager      The entity view manager
     * @param criteriaBuilderFactory The criteria builder factory
     */
    public EntityViewJobManager(JobContext jobContext, EntityManager entityManager, EntityViewManager entityViewManager, CriteriaBuilderFactory criteriaBuilderFactory) {
        if (jobContext == null) {
            throw new JobException("No job context given!");
        }
        if (entityManager == null) {
            throw new JobException("No entity manager given!");
        }
        if (entityViewManager == null) {
            throw new JobException("No entity view manager given!");
        }
        if (criteriaBuilderFactory == null) {
            throw new JobException("No criteria builder factory given!");
        }
        if (jobContext.getTransactionSupport() == TransactionSupport.NOOP) {
            throw new JobException("JPA storage requires transaction support!");
        }
        this.jobContext = jobContext;
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        this.entityManager = entityManager;
        this.entityViewManager = entityViewManager;
        this.criteriaBuilderFactory = criteriaBuilderFactory;
        Set<Class<?>> entityClasses = new HashSet<>();
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            if (entity.getJavaType() != null) {
                entityClasses.add(entity.getJavaType());
            }
        }
        this.entityClasses = entityClasses;
    }

    @Override
    public void addJobInstance(JobInstance<?> jobInstance) {
        if (jobInstance instanceof JobTrigger) {
            JobTrigger jobTrigger = (JobTrigger) jobInstance;
            JobConfiguration jobConfiguration = jobTrigger.getJob().getJobConfiguration();
            JobConfiguration triggerJobConfiguration = jobTrigger.getJobConfiguration();
            if (jobConfiguration != null && triggerJobConfiguration != null && jobConfiguration.getParameters() != null) {
                Map<String, Serializable> jobParameters = triggerJobConfiguration.getParameters();
                for (Map.Entry<String, Serializable> entry : jobConfiguration.getParameters().entrySet()) {
                    jobParameters.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
            if (jobTrigger.getScheduleTime() == null) {
                jobTrigger.setScheduleTime(jobTrigger.getSchedule(jobContext).nextSchedule(Schedule.scheduleContext(clock.millis())));
            }
        }
        entityViewManager.save(entityManager, jobInstance);
        if (jobInstance.getState() == JobInstanceState.NEW && !jobContext.isScheduleRefreshedOnly()) {
            jobContext.getTransactionSupport().registerPostCommitListener(() -> {
                jobContext.refreshJobInstanceSchedules(jobInstance);
            });
        }
    }

    @Override
    public List<JobInstance<?>> getJobInstancesToProcess(int partition, int partitionCount, int limit, PartitionKey partitionKey, Set<JobInstance<?>> jobInstancesToInclude) {
        List<Object> ids = new ArrayList<>();
        if (jobInstancesToInclude != null) {
            for (JobInstance<?> jobInstance : jobInstancesToInclude) {
                ids.add(jobInstance.getId());
            }
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
        }
        if (!(partitionKey instanceof EntityViewPartitionKey)) {
            throw new IllegalArgumentException("The given partition key does not implement EntityViewPartitionKey: " + partitionKey);
        }
        EntityViewPartitionKey entityViewPartitionKey = (EntityViewPartitionKey) partitionKey;
        Instant now = clock.instant();
        CriteriaBuilder<Object> criteriaBuilder = createCriteriaBuilder(now, partition, partitionCount, entityViewPartitionKey, ids);
        CriteriaBuilder<JobInstance<?>> cb = entityViewManager.applySetting(EntityViewSetting.create((Class<JobInstance<?>>) entityViewPartitionKey.getEntityView()), criteriaBuilder);
        List<JobInstance<?>> jobInstances = cb.getQuery()
            .setHint("org.hibernate.lockMode.e", "UPGRADE_SKIPLOCKED")
            .setMaxResults(limit)
            .getResultList();

        return jobInstances;
    }

    @Override
    public List<JobInstance<?>> getRunningJobInstances(int partition, int partitionCount, PartitionKey partitionKey) {
        if (!(partitionKey instanceof EntityViewPartitionKey)) {
            throw new IllegalArgumentException("The given partition key does not implement EntityViewPartitionKey: " + partitionKey);
        }
        EntityViewPartitionKey entityViewPartitionKey = (EntityViewPartitionKey) partitionKey;
        String partitionPredicate = entityViewPartitionKey.getPartitionPredicate("e");
        String partitionKeyAttributeName = entityViewPartitionKey.getPartitionKeyAttributeName();

        CriteriaBuilder<Object> criteriaBuilder = criteriaBuilderFactory.create(entityManager, Object.class)
            .from(entityViewPartitionKey.getEntityClass(), "e");
        if (!partitionPredicate.isEmpty()) {
            criteriaBuilder.whereExpression(partitionPredicate);
        }
        if (partitionCount > 1) {
            criteriaBuilder.where("MOD(e." + partitionKeyAttributeName + ", " + partitionCount + ")").eqLiteral(partition);
        }
        criteriaBuilder.where(entityViewPartitionKey.getStateExpression("e")).eqLiteral(entityViewPartitionKey.getStateValueMappingFunction().apply(JobInstanceState.RUNNING));
        CriteriaBuilder<JobInstance<?>> cb = entityViewManager.applySetting(EntityViewSetting.create((Class<JobInstance<?>>) entityViewPartitionKey.getEntityView()), criteriaBuilder);
        List<JobInstance<?>> jobInstances = cb.getQuery()
            .getResultList();

        return jobInstances;
    }

    @Override
    public Instant getNextSchedule(int partition, int partitionCount, PartitionKey partitionKey, Set<JobInstance<?>> jobInstancesToInclude) {
        List<Object> ids = new ArrayList<>();
        if (jobInstancesToInclude != null) {
            for (JobInstance<?> jobInstance : jobInstancesToInclude) {
                ids.add(jobInstance.getId());
            }
            if (ids.isEmpty()) {
                return null;
            }
        }
        if (!(partitionKey instanceof EntityViewPartitionKey)) {
            throw new IllegalArgumentException("The given partition key does not implement EntityViewPartitionKey: " + partitionKey);
        }
        EntityViewPartitionKey entityViewPartitionKey = (EntityViewPartitionKey) partitionKey;
        String scheduleAttributeName = entityViewPartitionKey.getScheduleAttributeName();
        CriteriaBuilder<Instant> criteriaBuilder = createCriteriaBuilder(null, partition, partitionCount, entityViewPartitionKey, ids);
        criteriaBuilder.select("e." + scheduleAttributeName);
        List<Instant> nextSchedule = criteriaBuilder.setMaxResults(1).getResultList();
        return nextSchedule.size() == 0 ? null : nextSchedule.get(0);
    }

    private <T> CriteriaBuilder<T> createCriteriaBuilder(Instant now, int partition, int partitionCount, EntityViewPartitionKey entityViewPartitionKey, List<Object> ids) {
        String partitionPredicate = entityViewPartitionKey.getPartitionPredicate("e");
        String idAttributeName = entityViewPartitionKey.getIdAttributeName();
        String partitionKeyAttributeName = entityViewPartitionKey.getPartitionKeyAttributeName();
        String scheduleAttributeName = entityViewPartitionKey.getScheduleAttributeName();

        CriteriaBuilder<Object> cb = criteriaBuilderFactory.create(entityManager, Object.class)
            .from(entityViewPartitionKey.getEntityClass(), "e");
        if (now != null) {
            cb.where("e." + scheduleAttributeName).le(now);
        }
        if (!partitionPredicate.isEmpty()) {
            cb.whereExpression(partitionPredicate);
        }
        if (partitionCount > 1) {
            cb.where("MOD(e." + partitionKeyAttributeName + ", " + partitionCount + ")").eqLiteral(partition);
        }
        entityViewPartitionKey.applyStatePredicate("e", cb);
        if (!ids.isEmpty()) {
            cb.where("e." + idAttributeName).in(ids);
        }
        cb.orderByAsc("e." + scheduleAttributeName).orderByAsc("e." + idAttributeName);
        return (CriteriaBuilder<T>) cb;
    }

    @Override
    public void updateJobInstance(JobInstance<?> jobInstance) {
        if (jobInstance.getJobConfiguration().getMaximumDeferCount() > jobInstance.getDeferCount()) {
            jobInstance.markDropped(new SimpleJobInstanceProcessingContext(jobContext, jobInstance));
        }
        if (!entityManager.isJoinedToTransaction()) {
            entityManager.joinTransaction();
        }
        if (jobInstance.getState() == JobInstanceState.REMOVED) {
            removeJobInstance(jobInstance);
        } else {
            entityViewManager.save(entityManager, jobInstance);
        }

        entityManager.flush();
        if (jobInstance.getState() == JobInstanceState.NEW && !jobContext.isScheduleRefreshedOnly()) {
            jobContext.getTransactionSupport().registerPostCommitListener(() -> {
                jobContext.refreshJobInstanceSchedules(jobInstance);
            });
        }
    }

    @Override
    public void removeJobInstance(JobInstance<?> jobInstance) {
        entityViewManager.remove(entityManager, jobInstance);
    }

    @Override
    public int removeJobInstances(Set<JobInstanceState> states, Instant executionTimeOlderThan, PartitionKey partitionKey) {
        EntityViewPartitionKey entityViewPartitionKey = (EntityViewPartitionKey) partitionKey;
        String stateExpression = entityViewPartitionKey.getStateExpression("i");
        if (stateExpression != null && !stateExpression.isEmpty() && !states.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM ").append(entityViewPartitionKey.getEntityClass().getName()).append(" i ")
                .append("WHERE ").append(stateExpression).append(" IN (");
            int i = 0;
            int size = states.size();
            for (; i != size; i++) {
                sb.append("param").append(i).append(',');
            }
            sb.setCharAt(sb.length() - 1, ')');
            if (executionTimeOlderThan != null) {
                sb.append(" AND i.").append(entityViewPartitionKey.getLastExecutionAttributeName()).append(" < :lastExecution");
            }
            Query query = entityManager.createQuery(sb.toString());
            i = 0;
            for (JobInstanceState state : states) {
                query.setParameter("param" + i, entityViewPartitionKey.getStateValueMappingFunction().apply(state));
                i++;
            }
            if (executionTimeOlderThan != null) {
                query.setParameter("lastExecution", executionTimeOlderThan);
            }
            return query.executeUpdate();
        }
        return 0;
    }
}
