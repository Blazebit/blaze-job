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

import com.blazebit.job.Job;
import com.blazebit.job.JobConfiguration;
import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobManager;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.Schedule;
import com.blazebit.job.jpa.model.JpaJobInstance;
import com.blazebit.job.jpa.model.JpaJobTrigger;
import com.blazebit.job.jpa.model.JpaPartitionKey;
import com.blazebit.job.jpa.model.JpaTriggerBasedJobInstance;
import com.blazebit.job.spi.TransactionSupport;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
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
import java.util.function.Function;

/**
 * A JPA based implementation of the {@link JobManager} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JpaJobManager implements JobManager {

    private final JobContext jobContext;
    private final Clock clock;
    private final EntityManager entityManager;
    private final Set<Class<?>> entityClasses;

    /**
     * Creates a job manager that makes use of the job context to determine the {@link EntityManager}.
     *
     * @param jobContext The job context
     */
    public JpaJobManager(JobContext jobContext) {
        this(
            jobContext,
            jobContext.getService(EntityManager.class)
        );
    }

    /**
     * Creates a job manager.
     *
     * @param jobContext    The job context
     * @param entityManager The entity manager
     */
    public JpaJobManager(JobContext jobContext, EntityManager entityManager) {
        if (entityManager == null) {
            throw new JobException("No entity manager given!");
        }
        if (jobContext.getTransactionSupport() == TransactionSupport.NOOP) {
            throw new JobException("JPA storage requires transaction support!");
        }
        this.jobContext = jobContext;
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        this.entityManager = entityManager;
        Set<Class<?>> entityClasses = new HashSet<>();
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            if (entity.getJavaType() != null) {
                entityClasses.add(entity.getJavaType());
            }
        }
        this.entityClasses = entityClasses;
    }

    /**
     * Casts the job trigger to a {@link JpaJobTrigger}.
     *
     * @param jobTrigger The job trigger
     * @return the JPA job trigger
     */
    protected JpaJobTrigger getJobTrigger(JobTrigger jobTrigger) {
        if (!(jobTrigger instanceof JpaJobTrigger)) {
            throw new IllegalArgumentException("The job trigger does not implement the JpaJobTrigger interface from blaze-notify-job-jpa-model!");
        }
        return (JpaJobTrigger) jobTrigger;
    }

    /**
     * Casts the job instance to a {@link JpaJobInstance}.
     *
     * @param jobInstance The job instance
     * @return the JPA job instance
     */
    protected JpaJobInstance<?> getJobInstance(JobInstance<?> jobInstance) {
        if (!(jobInstance instanceof JpaJobInstance<?>)) {
            throw new IllegalArgumentException("The job instance does not implement the JpaJobInstance interface from blaze-notify-job-jpa-model!");
        }
        return (JpaJobInstance<?>) jobInstance;
    }

    /**
     * Sets the given job on the given job trigger.
     *
     * @param jobTrigger The job trigger
     * @param job        The job
     */
    protected void setJob(JobTrigger jobTrigger, Job job) {
        getJobTrigger(jobTrigger).setJob(job);
    }

    /**
     * Sets the given job trigger on the given trigger based job instance.
     *
     * @param jpaTriggerBasedJobInstance The trigger based job instance
     * @param jobTrigger                 The job trigger
     */
    protected void setTrigger(JpaTriggerBasedJobInstance<?> jpaTriggerBasedJobInstance, JobTrigger jobTrigger) {
        jpaTriggerBasedJobInstance.setTrigger(jobTrigger);
    }

    /**
     * Finds a job of the given entity class by the given job id.
     *
     * @param entityClass The job entity class
     * @param jobId       The job id
     * @return the job or <code>null</code>
     */
    protected Job findJob(Class<?> entityClass, long jobId) {
        return (Job) entityManager.find(entityClass, jobId);
    }

    /**
     * Finds a job trigger of the given entity class by the given job trigger id.
     *
     * @param entityClass The job trigger entity class
     * @param triggerId   The job trigger id
     * @return the job trigger or <code>null</code>
     */
    protected JobTrigger findJobTrigger(Class<?> entityClass, long triggerId) {
        return (JobTrigger) entityManager.find(entityClass, triggerId);
    }

    /**
     * Returns the entity class of the given job.
     *
     * @param job The job
     * @return the entity class of the given job
     */
    protected Class<?> getEntityClass(Job job) {
        return getEntityClass(job.getClass());
    }

    /**
     * Returns the entity class of the given job trigger.
     *
     * @param jobTrigger The job trigger
     * @return the entity class of the given job trigger
     */
    protected Class<?> getEntityClass(JobTrigger jobTrigger) {
        return getEntityClass(jobTrigger.getClass());
    }

    /**
     * Returns the entity class of the given class.
     *
     * @param clazz The class
     * @return the entity class
     */
    protected Class<?> getEntityClass(Class<?> clazz) {
        while (clazz != Object.class && !entityClasses.contains(clazz)) {
            clazz = clazz.getSuperclass();
        }

        return clazz;
    }

    private void addJobTrigger(JobTrigger jobTrigger) {
        if (jobTrigger.getJob().getId() == null) {
            if (!entityManager.contains(jobTrigger.getJob())) {
                entityManager.persist(jobTrigger.getJob());
            }
            setJob(jobTrigger, jobTrigger.getJob());
        } else if (!entityManager.contains(jobTrigger.getJob())) {
            setJob(jobTrigger, findJob(getEntityClass(jobTrigger.getJob()), jobTrigger.getJob().getId()));
        }
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
        if (!entityManager.contains(jobTrigger)) {
            entityManager.persist(jobTrigger);
        }
        if (jobTrigger.getState() == JobInstanceState.NEW && !jobContext.isScheduleRefreshedOnly()) {
            jobContext.getTransactionSupport().registerPostCommitListener(() -> {
                jobContext.refreshJobInstanceSchedules(jobTrigger);
            });
        }
    }

    @Override
    public void addJobInstance(JobInstance<?> jobInstance) {
        if (jobInstance instanceof JpaJobTrigger) {
            addJobTrigger((JobTrigger) jobInstance);
            return;
        }
        JpaJobInstance<?> jpaJobInstance = getJobInstance(jobInstance);
        if (jpaJobInstance instanceof JpaTriggerBasedJobInstance<?>) {
            JpaTriggerBasedJobInstance<?> jpaTriggerBasedJobInstance = (JpaTriggerBasedJobInstance<?>) jpaJobInstance;
            JobTrigger trigger = (jpaTriggerBasedJobInstance).getTrigger();
            if (trigger.getJob().getId() == null) {
                addJobTrigger(trigger);
            } else if (!entityManager.contains(trigger)) {
                setTrigger(jpaTriggerBasedJobInstance, findJobTrigger(getEntityClass(trigger), trigger.getId()));
            }
        }
        if (!entityManager.contains(jobInstance)) {
            entityManager.persist(jobInstance);
        }
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
        if (!(partitionKey instanceof JpaPartitionKey)) {
            throw new IllegalArgumentException("The given partition key does not implement JpaPartitionKey: " + partitionKey);
        }
        JpaPartitionKey jpaPartitionKey = (JpaPartitionKey) partitionKey;
        String partitionPredicate = jpaPartitionKey.getPartitionPredicate("e");
        String idAttributeName = jpaPartitionKey.getIdAttributeName();
        String partitionKeyAttributeName = jpaPartitionKey.getPartitionKeyAttributeName();
        String scheduleAttributeName = jpaPartitionKey.getScheduleAttributeName();
        String statePredicate = jpaPartitionKey.getStatePredicate("e");
        Function<JobInstanceState, Object> stateValueMappingFunction = jpaPartitionKey.getStateValueMappingFunction();
        String joinFetches = jpaPartitionKey.getJoinFetches("e");
        Instant now = clock.instant();
        TypedQuery<? extends JobInstance<?>> typedQuery = entityManager.createQuery(
            "SELECT e FROM " + jpaPartitionKey.getEntityClass().getName() + " e " +
                joinFetches + " " +
                "WHERE e." + scheduleAttributeName + " <= :now " +
                (partitionPredicate.isEmpty() ? "" : "AND " + partitionPredicate + " ") +
                (partitionCount > 1 ? "AND MOD(e." + partitionKeyAttributeName + ", " + partitionCount + ") = " + partition + " " : "") +
                (statePredicate == null || statePredicate.isEmpty() ? "" : "AND " + statePredicate + " ") +
                (ids.isEmpty() ? "" : "AND e." + idAttributeName + " IN :ids ") +
                "ORDER BY e." + scheduleAttributeName + " ASC, e." + idAttributeName + " ASC",
            jpaPartitionKey.getEntityClass()
        );
        typedQuery.setParameter("now", now);
        if (stateValueMappingFunction != null) {
            typedQuery.setParameter("readyState", stateValueMappingFunction.apply(JobInstanceState.NEW));
        }
        if (!ids.isEmpty()) {
            typedQuery.setParameter("ids", ids);
        }
        List<JobInstance<?>> jobInstances = (List<JobInstance<?>>) (List) typedQuery
            // TODO: lockMode for update? advisory locks?
            // TODO: PostgreSQL 9.5 supports the skip locked clause, but since then, we have to use advisory locks
//                .where("FUNCTION('pg_try_advisory_xact_lock', id.userId)").eqExpression("true")
            .setHint("org.hibernate.lockMode.e", "UPGRADE_SKIPLOCKED")
            .setMaxResults(limit)
            .getResultList();

        // Detach the job instances to avoid accidental flushes due to changes
//        for (int i = 0; i < jobInstances.size(); i++) {
//            entityManager.detach(jobInstances.get(i));
//        }

        return jobInstances;
    }

    @Override
    public List<JobInstance<?>> getRunningJobInstances(int partition, int partitionCount, PartitionKey partitionKey) {
        if (!(partitionKey instanceof JpaPartitionKey)) {
            throw new IllegalArgumentException("The given partition key does not implement JpaPartitionKey: " + partitionKey);
        }
        JpaPartitionKey jpaPartitionKey = (JpaPartitionKey) partitionKey;
        String partitionPredicate = jpaPartitionKey.getPartitionPredicate("e");
        String partitionKeyAttributeName = jpaPartitionKey.getPartitionKeyAttributeName();
        String statePredicate = jpaPartitionKey.getStatePredicate("e");
        Function<JobInstanceState, Object> stateValueMappingFunction = jpaPartitionKey.getStateValueMappingFunction();
        String joinFetches = jpaPartitionKey.getJoinFetches("e");
        TypedQuery<? extends JobInstance<?>> typedQuery = entityManager.createQuery(
            "SELECT e FROM " + jpaPartitionKey.getEntityClass().getName() + " e " +
                joinFetches + " " +
                "WHERE " + statePredicate +
                (partitionPredicate.isEmpty() ? "" : "AND " + partitionPredicate + " ") +
                (partitionCount > 1 ? "AND MOD(e." + partitionKeyAttributeName + ", " + partitionCount + ") = " + partition + " " : ""),
            jpaPartitionKey.getEntityClass()
        );
        typedQuery.setParameter("readyState", stateValueMappingFunction.apply(JobInstanceState.RUNNING));
        List<JobInstance<?>> jobInstances = (List<JobInstance<?>>) (List) typedQuery.getResultList();

        // Detach the job instances to avoid accidental flushes due to changes
//        for (int i = 0; i < jobInstances.size(); i++) {
//            entityManager.detach(jobInstances.get(i));
//        }

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
        if (!(partitionKey instanceof JpaPartitionKey)) {
            throw new IllegalArgumentException("The given partition key does not implement JpaPartitionKey: " + partitionKey);
        }
        JpaPartitionKey jpaPartitionKey = (JpaPartitionKey) partitionKey;
        String partitionPredicate = jpaPartitionKey.getPartitionPredicate("e");
        String idAttributeName = jpaPartitionKey.getIdAttributeName();
        String partitionKeyAttributeName = jpaPartitionKey.getPartitionKeyAttributeName();
        String scheduleAttributeName = jpaPartitionKey.getScheduleAttributeName();
        String statePredicate = jpaPartitionKey.getStatePredicate("e");
        Function<JobInstanceState, Object> stateValueMappingFunction = jpaPartitionKey.getStateValueMappingFunction();

        TypedQuery<Instant> typedQuery = entityManager.createQuery(
            "SELECT e." + scheduleAttributeName + " FROM " + jpaPartitionKey.getEntityClass().getName() + " e " +
                "WHERE 1=1 " +
                (statePredicate == null || statePredicate.isEmpty() ? "" : "AND " + statePredicate + " ") +
                (partitionPredicate.isEmpty() ? "" : "AND " + partitionPredicate + " ") +
                (partitionCount > 1 ? "AND MOD(e." + partitionKeyAttributeName + ", " + partitionCount + ") = " + partition + " " : "") +
                (ids.isEmpty() ? "" : "AND e." + idAttributeName + " IN :ids") +
                "ORDER BY e." + scheduleAttributeName + " ASC, e." + idAttributeName + " ASC",
            Instant.class
        );
        if (stateValueMappingFunction != null) {
            typedQuery.setParameter("readyState", stateValueMappingFunction.apply(JobInstanceState.NEW));
        }
        if (!ids.isEmpty()) {
            typedQuery.setParameter("ids", ids);
        }

        List<Instant> nextSchedule = typedQuery.setMaxResults(1).getResultList();
        return nextSchedule.size() == 0 ? null : nextSchedule.get(0);
    }

    @Override
    public void updateJobInstance(JobInstance<?> jobInstance) {
        if (jobInstance.getJobConfiguration().getMaximumDeferCount() > jobInstance.getDeferCount()) {
            jobInstance.markDropped(new SimpleJobInstanceProcessingContext(jobContext, jobInstance));
        }
        if (!entityManager.isJoinedToTransaction()) {
            entityManager.joinTransaction();
        }
        if (!entityManager.contains(jobInstance)) {
            entityManager.merge(jobInstance);
            if (jobInstance.getState() == JobInstanceState.REMOVED) {
                entityManager.flush();
                removeJobInstance(jobInstance);
            }
        } else if (jobInstance.getState() == JobInstanceState.REMOVED) {
            removeJobInstance(jobInstance);
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
        entityManager.remove(jobInstance);
    }

    @Override
    public int removeJobInstances(Set<JobInstanceState> states, Instant executionTimeOlderThan, PartitionKey partitionKey) {
        JpaPartitionKey jpaPartitionKey = (JpaPartitionKey) partitionKey;
        String stateExpression = jpaPartitionKey.getStateExpression("i");
        if (stateExpression != null && !stateExpression.isEmpty() && !states.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM ").append(jpaPartitionKey.getEntityClass().getName()).append(" i ")
                .append("WHERE ").append(stateExpression).append(" IN (");
            int i = 0;
            int size = states.size();
            for (; i != size; i++) {
                sb.append("param").append(i).append(',');
            }
            sb.setCharAt(sb.length() - 1, ')');
            if (executionTimeOlderThan != null) {
                sb.append(" AND i.").append(jpaPartitionKey.getLastExecutionAttributeName()).append(" < :lastExecution");
            }
            Query query = entityManager.createQuery(sb.toString());
            i = 0;
            for (JobInstanceState state : states) {
                query.setParameter("param" + i, jpaPartitionKey.getStateValueMappingFunction().apply(state));
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
