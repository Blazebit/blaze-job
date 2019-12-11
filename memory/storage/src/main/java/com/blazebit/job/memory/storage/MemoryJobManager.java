/*
 * Copyright 2018 - 2019 Blazebit.
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

package com.blazebit.job.memory.storage;

import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobManager;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.Schedule;
import com.blazebit.job.memory.model.AbstractJobInstance;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of the {@link JobManager} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class MemoryJobManager implements JobManager {

    /**
     * A property for configuring a custom set of job instances as storage.
     * The default storage is a concurrent hash based set implementation.
     */
    public static final String JOB_INSTANCES_PROPERTY = "job.memory.storage.jobInstances";

    private final JobContext jobContext;
    private final Clock clock;
    private final AtomicLong jobInstanceCounter = new AtomicLong();
    private final Set<JobInstance<?>> jobInstances;

    /**
     * Creates a job manager that makes use of the job context to determine the job instance set.
     *
     * @param jobContext The job context
     */
    public MemoryJobManager(JobContext jobContext) {
        this.jobContext = jobContext;
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        Object jobInstancesProperty = jobContext.getProperty(JOB_INSTANCES_PROPERTY);
        if (jobInstancesProperty == null) {
            this.jobInstances = Collections.newSetFromMap(new ConcurrentHashMap<>());
        } else if (jobInstancesProperty instanceof Set<?>) {
            this.jobInstances = (Set<JobInstance<?>>) jobInstancesProperty;
        } else {
            throw new JobException("The property value for " + JOB_INSTANCES_PROPERTY + " must be an instance of java.util.Set if given!");
        }
    }

    /**
     * Creates a job manager.
     *
     * @param jobContext   The job context
     * @param jobInstances The job instances set
     */
    public MemoryJobManager(JobContext jobContext, Set<JobInstance<?>> jobInstances) {
        this.jobContext = jobContext;
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        this.jobInstances = jobInstances;
    }

    @Override
    public void addJobInstance(JobInstance<?> jobInstance) {
        ((AbstractJobInstance<Long>) jobInstance).setId(jobInstanceCounter.incrementAndGet());
        if (jobInstance.getScheduleTime() == null) {
            if (jobInstance instanceof JobTrigger) {
                jobInstance.setScheduleTime(((JobTrigger) jobInstance).getSchedule(jobContext).nextSchedule(Schedule.scheduleContext(clock.millis())));
            } else {
                throw new JobException("Invalid null schedule time for job instance: " + jobInstance);
            }
        }
        jobInstances.add(jobInstance);
        if (jobInstance.getState() == JobInstanceState.NEW) {
            jobContext.refreshJobInstanceSchedules(jobInstance);
        }
    }

    @Override
    public List<JobInstance<?>> getJobInstancesToProcess(int partition, int partitionCount, int limit, PartitionKey partitionKey) {
        return jobInstances.stream()
            .filter(i -> i.getState() == JobInstanceState.NEW
                && i.getScheduleTime().toEpochMilli() <= clock.millis()
                && (partitionCount == 1 || (i.getPartitionKey() % partitionCount) == partition)
                && partitionKey.matches(i)
            )
            .sorted(Comparator.comparing(JobInstance::getScheduleTime))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public Instant getNextSchedule(int partition, int partitionCount, PartitionKey partitionKey) {
        return jobInstances.stream()
            .filter(i -> i.getState() == JobInstanceState.NEW
                && (partitionCount == 1 || (i.getPartitionKey() % partitionCount) == partition)
                && partitionKey.matches(i)
            )
            .sorted(Comparator.comparing(JobInstance::getScheduleTime))
            .map(JobInstance::getScheduleTime)
            .findFirst()
            .orElse(null);
    }

    @Override
    public void updateJobInstance(JobInstance<?> jobInstance) {
        if (jobInstance.getJobConfiguration().getMaximumDeferCount() > jobInstance.getDeferCount()) {
            jobInstance.markDropped();
        }
    }
}
