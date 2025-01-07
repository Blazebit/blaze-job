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
package com.blazebit.job.processor.hibernate.insertselect;

import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobInstanceProcessor;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.InsertCriteriaBuilder;

import jakarta.persistence.EntityManager;

/**
 * An abstract job instance processor implementation that produces target entities via a INSERT-SELECT statement.
 *
 * @param <ID> The job instance cursor type
 * @param <T> The result type of the processing
 * @param <I> The job instance type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractInsertSelectJobInstanceProcessor<ID, T, I extends JobInstance<?>> implements JobInstanceProcessor<ID, I> {

    /**
     * Creates a new job instance processor.
     */
    protected AbstractInsertSelectJobInstanceProcessor() {
    }

    @Override
    public ID process(I jobInstance, JobInstanceProcessingContext<ID> context) {
        CriteriaBuilderFactory cbf = getCriteriaBuilderFactory(context);
        EntityManager em = getEntityManager(context);
        if (cbf == null) {
            throw new JobException("No CriteriaBuilderFactory given!");
        }
        if (em == null) {
            throw new JobException("No EntityManager given!");
        }
        InsertCriteriaBuilder<T> insertCriteriaBuilder = cbf.insert(em, getTargetEntityClass(jobInstance))
                .from(getJobInstanceEntityClass(jobInstance), "jobInstance");

        insertCriteriaBuilder.where("jobInstance." + getJobInstanceIdPath(jobInstance)).eq(jobInstance.getId());
        insertCriteriaBuilder.setMaxResults(context.getProcessCount());

        bindTargetAttributes(insertCriteriaBuilder, jobInstance, context, "jobInstance");

        return execute(insertCriteriaBuilder, jobInstance, context);
    }

    /**
     * Returns the {@link EntityManager} to use.
     *
     * @param context The job instance processing context
     * @return The entity manager
     */
    protected EntityManager getEntityManager(JobInstanceProcessingContext<ID> context) {
        return context.getJobContext().getService(EntityManager.class);
    }

    /**
     * Returns the {@link CriteriaBuilderFactory} to use.
     *
     * @param context The job instance processing context
     * @return The criteria builder factory
     */
    protected CriteriaBuilderFactory getCriteriaBuilderFactory(JobInstanceProcessingContext<ID> context) {
        return context.getJobContext().getService(CriteriaBuilderFactory.class);
    }

    /**
     * Binds the attributes of the target entity type on the insert criteria builder based on the job instance alias.
     *
     * @param insertCriteriaBuilder The insert criteria builder
     * @param jobInstance The job instance
     * @param context The job instance processing context
     * @param jobInstanceAlias The job instance FROM clause alias
     */
    protected abstract void bindTargetAttributes(InsertCriteriaBuilder<T> insertCriteriaBuilder, I jobInstance, JobInstanceProcessingContext<ID> context, String jobInstanceAlias);

    /**
     * Executes the given insert criteria builder and returns the id of the last processed object or <code>null</code> if processing should stop.
     *
     * @param insertCriteriaBuilder The insert criteria builder
     * @param jobInstance The job instance
     * @param context The job instance processing context
     * @return the id of the last processed object or <code>null</code> if processing should stop
     */
    protected abstract ID execute(InsertCriteriaBuilder<T> insertCriteriaBuilder, I jobInstance, JobInstanceProcessingContext<ID> context);

    /**
     * Returns the entity class of the target entities that should be produced by this processor.
     *
     * @param jobInstance The job instance
     * @return the entity class
     */
    protected abstract Class<T> getTargetEntityClass(I jobInstance);

    /**
     * Returns the entity class of the job instance.
     *
     * @param jobInstance The job instance
     * @return the entity class
     */
    protected abstract Class<I> getJobInstanceEntityClass(I jobInstance);

    /**
     * Returns the id attribute path for the job instance entity type.
     *
     * @param jobInstance The job instance
     * @return the id attribute path
     */
    protected abstract String getJobInstanceIdPath(I jobInstance);

}
