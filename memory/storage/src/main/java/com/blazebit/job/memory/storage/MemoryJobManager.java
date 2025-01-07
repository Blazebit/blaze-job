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

package com.blazebit.job.memory.storage;

import com.blazebit.actor.spi.ClusterNodeInfo;
import com.blazebit.actor.spi.ClusterStateManager;
import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobManager;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.Schedule;
import com.blazebit.job.memory.model.MemoryJobInstance;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An in-memory implementation of the {@link JobManager} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class MemoryJobManager implements JobManager {

    /**
     * A property for configuring a custom map for job instances as storage.
     * The default storage is a concurrent hash map implementation.
     */
    public static final String JOB_INSTANCES_PROPERTY = "job.memory.storage.job_instances";

    /**
     * A property for configuring a custom map for job instances as storage.
     * The default initial value is 1.
     */
    public static final String SEQUENCE_INITIAL_VALUE_PROPERTY = "job.memory.storage.sequence_initial_value";

    /**
     * A property for configuring a custom map for job instances as storage.
     * The default pool size is 1.
     */
    public static final String SEQUENCE_POOL_SIZE_PROPERTY = "job.memory.storage.sequence_pool_size";

    /**
     * A property for configuring whether replication should happen synchronously or asynchronously.
     * The default is <code>false</code> meaning that is replicates asynchronously.
     */
    public static final String REPLICATE_SYNCHRONOUS_PROPERTY = "job.memory.storage.replicate_synchronously";

    private static final Logger LOG = Logger.getLogger(MemoryJobManager.class.getName());

    private final JobContext jobContext;
    private final ClusterStateManager clusterStateManager;
    private final Clock clock;
    private final AtomicLong jobInstanceSequence;
    private final ArrayBlockingQueue<Long> sequencePool;
    private final Map<Long, MemoryJobInstance<?>> jobInstances;
    private final boolean synchronousReplication;

    private volatile boolean initialized;

    /**
     * Creates a job manager that makes use of the job context to determine the job instance set.
     *
     * @param jobContext The job context
     */
    public MemoryJobManager(JobContext jobContext) {
        this.jobContext = jobContext;
        this.clusterStateManager = jobContext.getService(ClusterStateManager.class);
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        long sequenceInitialValue = 1L;
        Object sequenceInitialValueProperty = jobContext.getProperty(SEQUENCE_INITIAL_VALUE_PROPERTY);
        if (sequenceInitialValueProperty instanceof Number) {
            sequenceInitialValue = ((Number) sequenceInitialValueProperty).longValue();
        } else if (sequenceInitialValueProperty instanceof String) {
            sequenceInitialValue = Long.parseLong((String) sequenceInitialValueProperty);
        } else if (sequenceInitialValueProperty != null) {
            throw new JobException("The property value for " + SEQUENCE_INITIAL_VALUE_PROPERTY + " must be an instance of Number or String if given!");
        }
        this.jobInstanceSequence = new AtomicLong(sequenceInitialValue);
        int sequencePoolSize = 1;
        Object sequencePoolSizeProperty = jobContext.getProperty(SEQUENCE_POOL_SIZE_PROPERTY);
        if (sequencePoolSizeProperty instanceof Number) {
            sequencePoolSize = ((Number) sequencePoolSizeProperty).intValue();
        } else if (sequencePoolSizeProperty instanceof String) {
            sequencePoolSize = Integer.parseInt((String) sequencePoolSizeProperty);
        } else if (sequencePoolSizeProperty != null) {
            throw new JobException("The property value for " + SEQUENCE_POOL_SIZE_PROPERTY + " must be an instance of Number or String if given!");
        }
        this.sequencePool = new ArrayBlockingQueue<>(sequencePoolSize);
        Object jobInstancesProperty = jobContext.getProperty(JOB_INSTANCES_PROPERTY);
        if (jobInstancesProperty == null) {
            this.jobInstances = new ConcurrentHashMap<>();
        } else if (jobInstancesProperty instanceof Map<?, ?>) {
            this.jobInstances = (Map<Long, MemoryJobInstance<?>>) jobInstancesProperty;
        } else {
            throw new JobException("The property value for " + JOB_INSTANCES_PROPERTY + " must be an instance of java.util.Map if given!");
        }
        Object synchronousReplicationProperty = jobContext.getProperty(REPLICATE_SYNCHRONOUS_PROPERTY);
        if (synchronousReplicationProperty == null) {
            this.synchronousReplication = false;
        } else if (synchronousReplicationProperty instanceof Boolean) {
            this.synchronousReplication = (Boolean) synchronousReplicationProperty;
        } else if (synchronousReplicationProperty instanceof String) {
            this.synchronousReplication = Boolean.parseBoolean((String) synchronousReplicationProperty);
        } else {
            throw new JobException("The property value for " + REPLICATE_SYNCHRONOUS_PROPERTY + " must be an instance of String or Boolean if given!");
        }
        if (clusterStateManager != null) {
            registerListeners();
        }
    }

    /**
     * Creates a job manager.
     *  @param jobContext            The job context
     * @param jobInstances           The job instances set
     * @param sequencePoolSize       The size of the pool for sequence values
     * @param initialSequenceValue   The initial value of the sequence
     * @param synchronousReplication Whether to use synchronous or asynchronous replication
     */
    public MemoryJobManager(JobContext jobContext, Map<Long, MemoryJobInstance<?>> jobInstances, int sequencePoolSize, long initialSequenceValue, boolean synchronousReplication) {
        this.jobContext = jobContext;
        this.clusterStateManager = jobContext.getService(ClusterStateManager.class);
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        this.jobInstanceSequence = new AtomicLong(initialSequenceValue);
        this.sequencePool = new ArrayBlockingQueue<>(sequencePoolSize);
        this.jobInstances = jobInstances;
        this.synchronousReplication = synchronousReplication;
        if (clusterStateManager != null) {
            registerListeners();
        }
    }

    private void registerListeners() {
        clusterStateManager.registerListener(ReplicationRequestEvent.class, e -> {
            if (clusterStateManager.getCurrentNodeInfo().isCoordinator()) {
                e.setInitialReplicationData(new InitialReplicationData(jobInstanceSequence.get(), jobInstances.values()));
            }
        });
        clusterStateManager.registerListener(NextSequenceValueEvent.class, e -> {
            if (clusterStateManager.getCurrentNodeInfo().isCoordinator()) {
                int amount = e.getAmount();
                long startValue = jobInstanceSequence.getAndAdd(amount);
                long[] values = new long[amount];
                for (int i = 0; i < amount; i++) {
                    values[i] = startValue + i;
                }
                e.setSequenceValues(values);
                clusterStateManager.fireEventExcludeSelf(new SequenceReplicationEvent(startValue + amount), synchronousReplication);
            }
        });
        clusterStateManager.registerListener(JobInstanceReplicationEvent.class, e -> {
            MemoryJobInstance<?> jobInstance = e.getJobInstance();
            if (e.isRemoved()) {
                jobInstances.remove(e.getId());
            } else {
                jobInstances.compute(e.getId(), (k, v) -> {
                    if (v == null || v.getVersion() <= jobInstance.getVersion()) {
                        return jobInstance;
                    } else {
                        return v;
                    }
                });
            }
        });
        clusterStateManager.registerListener(SequenceReplicationEvent.class, e -> {
            updateSequence(e.getCurrentValue());
        });
        clusterStateManager.registerListener(clusterNodeInfo -> {
            if (!initialized) {
                initialized = true;
                // Can't request data if there is no cluster or if we are the coordinator
                if (clusterNodeInfo.getClusterSize() > 1 && !clusterNodeInfo.isCoordinator()) {
                    InitialReplicationData initialReplicationData = null;
                    int joinAttempts = 3;
                    do {
                        // We just joined the cluster, so request the current jobs and sequence value
                        try {
                            Map<ClusterNodeInfo, Future<InitialReplicationData>> futureMap = clusterStateManager.fireEventExcludeSelf(new ReplicationRequestEvent());
                            // First, we only block on our currently known coordinator
                            for (Map.Entry<ClusterNodeInfo, Future<InitialReplicationData>> entry : futureMap.entrySet()) {
                                if (entry.getKey().isCoordinator()) {
                                    initialReplicationData = entry.getValue().get();
                                    if (initialReplicationData != null) {
                                        break;
                                    }
                                }
                            }

                            // If that node didn't return anything, we look through the responses of all nodes
                            if (initialReplicationData == null) {
                                for (Future<InitialReplicationData> future : futureMap.values()) {
                                    initialReplicationData = future.get();
                                    if (initialReplicationData != null) {
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "Exception happened during initial data replication", e);
                        }
                    } while (initialReplicationData == null && --joinAttempts != 0);

                    if (initialReplicationData == null) {
                        throw new JobException("Couldn't properly join cluster. No initial data received!");
                    }

                    updateSequence(initialReplicationData.getSequenceValue());
                    for (MemoryJobInstance<?> jobInstance : initialReplicationData.getJobInstances()) {
                        jobInstances.compute((Long) jobInstance.getId(), (k, v) -> {
                            if (v == null || v.getVersion() <= jobInstance.getVersion()) {
                                return jobInstance;
                            } else {
                                return v;
                            }
                        });
                    }
                }
            }
        });
    }

    private void updateSequence(long newValue) {
        long currentValue = jobInstanceSequence.get();
        while (currentValue < newValue) {
            if (jobInstanceSequence.compareAndSet(currentValue, newValue)) {
                break;
            }
            currentValue = jobInstanceSequence.get();
        }
    }

    private long nextId() {
        Long valueFromPool = sequencePool.poll();
        if (valueFromPool != null) {
            return valueFromPool;
        }
        if (clusterStateManager == null || clusterStateManager.getCurrentNodeInfo().isCoordinator()) {
            synchronized (sequencePool) {
                valueFromPool = sequencePool.poll();
                if (valueFromPool != null) {
                    return valueFromPool;
                }
                long lastValue = jobInstanceSequence.get();
                int amount = sequencePool.remainingCapacity();
                while (!jobInstanceSequence.compareAndSet(lastValue, lastValue + amount)) {
                    lastValue = jobInstanceSequence.get();
                }
                for (int i = 1; i < amount; i++) {
                    sequencePool.offer(lastValue + i);
                }
                if (clusterStateManager != null) {
                    clusterStateManager.fireEventExcludeSelf(new SequenceReplicationEvent(lastValue + amount), synchronousReplication);
                }
                return lastValue;
            }
        } else {
            try {
                Map<ClusterNodeInfo, Future<long[]>> map = clusterStateManager.fireEventExcludeSelf(new NextSequenceValueEvent(1));
                for (Map.Entry<ClusterNodeInfo, Future<long[]>> entry : map.entrySet()) {
                    if (entry.getKey().isCoordinator()) {
                        long[] values = entry.getValue().get();
                        if (values != null) {
                            return values[0];
                        }
                        break;
                    }
                }
                for (Future<long[]> future : map.values()) {
                    long[] values = future.get();
                    if (values != null) {
                        return values[0];
                    }
                }

            } catch (Exception ex) {
                throw new RuntimeException("Couldn't retrieve next sequence value", ex);
            }

            throw new IllegalStateException("No node was able to generate a next sequence value");
        }
    }

    private void replicate(MemoryJobInstance<?> jobInstance, boolean removed) {
        if (clusterStateManager != null && !clusterStateManager.isStandalone()) {
            clusterStateManager.fireEventExcludeSelf(new JobInstanceReplicationEvent((Long) jobInstance.getId(), removed ? null : jobInstance, removed), synchronousReplication);
        }
    }

    @Override
    public void addJobInstance(JobInstance<?> jobInstance) {
        if (!(jobInstance instanceof MemoryJobInstance<?>)) {
            throw new IllegalArgumentException("Expected instance of " + MemoryJobInstance.class.getName() + " but got: " + jobInstance);
        }
        MemoryJobInstance<?> memoryJobInstance = (MemoryJobInstance<?>) jobInstance;
        long id = nextId();
        ((MemoryJobInstance<Long>) memoryJobInstance).setId(id);
        if (memoryJobInstance.getScheduleTime() == null) {
            if (memoryJobInstance instanceof JobTrigger) {
                memoryJobInstance.setScheduleTime(((JobTrigger) memoryJobInstance).getSchedule(jobContext).nextSchedule(Schedule.scheduleContext(clock.millis())));
            } else {
                throw new JobException("Invalid null schedule time for job instance: " + memoryJobInstance);
            }
        }
        replicate(memoryJobInstance, false);
        jobInstances.put(id, memoryJobInstance);
        if (memoryJobInstance.getState() == JobInstanceState.NEW && !jobContext.isScheduleRefreshedOnly()) {
            jobContext.refreshJobInstanceSchedules(memoryJobInstance);
        }
    }

    @Override
    public List<JobInstance<?>> getJobInstancesToProcess(int partition, int partitionCount, int limit, PartitionKey partitionKey, Set<JobInstance<?>> jobInstancesToInclude) {
        Stream<MemoryJobInstance<?>> limitedStream = streamJobInstances(partition, partitionCount, partitionKey).limit(limit);
        if (jobInstancesToInclude != null) {
            Set<JobInstance<?>> jobInstances = new HashSet<>(jobInstancesToInclude);
            if (jobInstances.isEmpty()) {
                return Collections.emptyList();
            } else {
                return Stream.concat(
                    streamJobInstances(partition, partitionCount, partitionKey).filter(jobInstances::contains),
                    limitedStream
                ).collect(Collectors.toList());
            }
        } else {
            return limitedStream.collect(Collectors.toList());
        }
    }

    @Override
    public List<JobInstance<?>> getRunningJobInstances(int partition, int partitionCount, PartitionKey partitionKey) {
        return jobInstances.values().stream()
            .filter(i -> i.getState() == JobInstanceState.RUNNING
                && i.getScheduleTime().toEpochMilli() <= clock.millis()
                && (partitionCount == 1 || (i.getPartitionKey() % partitionCount) == partition)
                && partitionKey.matches(i)
            )
            .collect(Collectors.toList());
    }

    private Stream<MemoryJobInstance<?>> streamJobInstances(int partition, int partitionCount, PartitionKey partitionKey) {
        return jobInstances.values().stream()
            .filter(i -> i.getState() == JobInstanceState.NEW
                && i.getScheduleTime().toEpochMilli() <= clock.millis()
                && (partitionCount == 1 || (i.getPartitionKey() % partitionCount) == partition)
                && partitionKey.matches(i)
            )
            .sorted(Comparator.comparing(JobInstance::getScheduleTime));
    }

    @Override
    public Instant getNextSchedule(int partition, int partitionCount, PartitionKey partitionKey, Set<JobInstance<?>> jobInstancesToInclude) {
        Stream<MemoryJobInstance<?>> jobInstanceStream = jobInstances.values().stream()
            .filter(i -> i.getState() == JobInstanceState.NEW
                && (partitionCount == 1 || (i.getPartitionKey() % partitionCount) == partition)
                && partitionKey.matches(i)
            );

        if (jobInstancesToInclude != null) {
            Set<JobInstance<?>> jobInstances = new HashSet<>(jobInstancesToInclude);
            if (jobInstances.isEmpty()) {
                return null;
            } else {
                jobInstanceStream = jobInstanceStream.filter(jobInstances::contains);
            }
        }

        return jobInstanceStream.sorted(Comparator.comparing(JobInstance::getScheduleTime))
            .map(JobInstance::getScheduleTime)
            .findFirst()
            .orElse(null);
    }

    @Override
    public void updateJobInstance(JobInstance<?> jobInstance) {
        if (!(jobInstance instanceof MemoryJobInstance<?>)) {
            throw new IllegalArgumentException("Expected instance of " + MemoryJobInstance.class.getName() + " but got: " + jobInstance);
        }
        MemoryJobInstance<?> memoryJobInstance = (MemoryJobInstance<?>) jobInstance;
        memoryJobInstance.setVersion(memoryJobInstance.getVersion() + 1L);
        if (jobInstance.getJobConfiguration().getMaximumDeferCount() > jobInstance.getDeferCount()) {
            jobInstance.markDropped(new SimpleJobInstanceProcessingContext(jobContext, jobInstance));
        }
        if (jobInstance.getState() == JobInstanceState.REMOVED) {
            removeJobInstance(jobInstance);
        } else {
            replicate(memoryJobInstance, false);
        }
        if (memoryJobInstance.getState() == JobInstanceState.NEW && !jobContext.isScheduleRefreshedOnly()) {
            jobContext.refreshJobInstanceSchedules(memoryJobInstance);
        }
    }

    @Override
    public void removeJobInstance(JobInstance<?> jobInstance) {
        if (!(jobInstance instanceof MemoryJobInstance<?>)) {
            throw new IllegalArgumentException("Expected instance of " + MemoryJobInstance.class.getName() + " but got: " + jobInstance);
        }
        MemoryJobInstance<?> memoryJobInstance = (MemoryJobInstance<?>) jobInstance;
        replicate(memoryJobInstance, true);
        jobInstances.remove((Long) jobInstance.getId());
    }

    @Override
    public int removeJobInstances(Set<JobInstanceState> states, Instant executionTimeOlderThan, PartitionKey partitionKey) {
        List<MemoryJobInstance<?>> removedInstances = new ArrayList<>();
        if (executionTimeOlderThan == null) {
            jobInstances.values().removeIf(i -> {
                if (states.contains(i.getState()) && partitionKey.matches(i)) {
                    removedInstances.add(i);
                    return true;
                }
                return false;
            });
        } else {
            jobInstances.values().removeIf(i -> {
                if (states.contains(i.getState()) && executionTimeOlderThan.isAfter(i.getLastExecutionTime()) && partitionKey.matches(i)) {
                    removedInstances.add(i);
                    return true;
                }
                return false;
            });
        }
        if (clusterStateManager != null && !clusterStateManager.isStandalone()) {
            for (MemoryJobInstance<?> removedInstance : removedInstances) {
                replicate(removedInstance, true);
            }
        }

        return removedInstances.size();
    }
}
