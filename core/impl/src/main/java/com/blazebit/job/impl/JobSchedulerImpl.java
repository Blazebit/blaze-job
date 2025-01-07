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
package com.blazebit.job.impl;

import com.blazebit.actor.ActorContext;
import com.blazebit.actor.ActorRunResult;
import com.blazebit.actor.ScheduledActor;
import com.blazebit.actor.spi.ClusterNodeInfo;
import com.blazebit.actor.spi.ClusterStateListener;
import com.blazebit.actor.spi.ClusterStateManager;
import com.blazebit.actor.spi.LockService;
import com.blazebit.actor.spi.Scheduler;
import com.blazebit.actor.spi.SchedulerFactory;
import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceListener;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobInstanceProcessor;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobManager;
import com.blazebit.job.JobRateLimitException;
import com.blazebit.job.JobTemporaryException;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.ScheduleContext;
import com.blazebit.job.TimeFrame;
import com.blazebit.job.spi.JobScheduler;
import com.blazebit.job.spi.TransactionSupport;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JobSchedulerImpl implements JobScheduler, ClusterStateListener {

    private static final Logger LOG = Logger.getLogger(JobSchedulerImpl.class.getName());
    private static final long COMPLETION_TX_TIMEOUT = 10_000L;

    private final JobContext jobContext;
    private final ActorContext actorContext;
    private final Clock clock;
    private final Scheduler scheduler;
    private final JobManager jobManager;
    private final JobInstanceRunner runner;
    private final String actorName;
    private final PartitionKey partitionKey;
    private final int processCount;
    private final long transactionTimeout;
    private final long temporaryErrorDeferSeconds;
    private final long rateLimitDeferSeconds;
    private final AtomicLong earliestKnownSchedule = new AtomicLong(Long.MAX_VALUE);
    private final ConcurrentMap<JobInstance<?>, Boolean> jobInstancesToSchedule = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, JobInstanceExecution> longRunningJobInstances = new ConcurrentHashMap<>();
    private volatile ClusterNodeInfo clusterNodeInfo;
    private volatile boolean closed;

    public JobSchedulerImpl(JobContext jobContext, ActorContext actorContext, SchedulerFactory schedulerFactory, String actorName, int processCount, PartitionKey partitionKey) {
        this.jobContext = jobContext;
        this.actorContext = actorContext;
        this.clock = jobContext.getService(Clock.class) == null ? Clock.systemUTC() : jobContext.getService(Clock.class);
        this.scheduler = schedulerFactory.createScheduler(actorContext, actorName + "/processor");
        this.jobManager = jobContext.getJobManager();
        this.runner = new JobInstanceRunner();
        this.actorName = actorName;
        this.processCount = processCount;
        this.partitionKey = partitionKey;
        this.transactionTimeout = partitionKey.getTransactionTimeoutMillis() < 0 ? jobContext.getTransactionTimeoutMillis() : partitionKey.getTransactionTimeoutMillis();
        this.temporaryErrorDeferSeconds = partitionKey.getTemporaryErrorBackoffSeconds() < 0 ? jobContext.getTemporaryErrorBackoffSeconds() : partitionKey.getTemporaryErrorBackoffSeconds();
        this.rateLimitDeferSeconds = partitionKey.getRateLimitBackoffSeconds() < 0 ? jobContext.getRateLimitBackoffSeconds() : partitionKey.getRateLimitBackoffSeconds();
    }

    @Override
    public void start() {
        actorContext.getActorManager().registerSuspendedActor(actorName, runner);
        ClusterStateManager clusterStateManager = actorContext.getService(ClusterStateManager.class);
        clusterStateManager.registerListener(this);
        clusterStateManager.registerListener(JobSchedulerCancelEvent.class, e -> {
            JobInstanceExecution execution = longRunningJobInstances.get(e.getJobInstanceId());
            if (execution != null) {
                execution.future.cancel(true);
            }
        });
        clusterStateManager.registerListener(JobSchedulerStatusEvent.class, e -> {
            Serializable[] jobInstanceIds = e.getJobInstanceIds();
            int[] clusterPositions = new int[jobInstanceIds.length];
            int clusterPosition = clusterNodeInfo.getClusterPosition();
            for (int i = 0; i < jobInstanceIds.length; i++) {
                if (longRunningJobInstances.containsKey(jobInstanceIds[i])) {
                    clusterPositions[i] = clusterPosition;
                } else {
                    clusterPositions[i] = -1;
                }
            }

            e.setClusterPositions(clusterPositions);
        });
        clusterStateManager.registerListener(JobSchedulerTraceEvent.class, e -> {
            JobInstanceExecution execution = longRunningJobInstances.get(e.getJobInstanceId());
            if (execution != null) {
                Thread thread = execution.thread;
                if (thread != null) {
                    StackTraceElement[] stackTrace = thread.getStackTrace();
                    StringBuilder sb = new StringBuilder();
                    for (StackTraceElement stackTraceElement : stackTrace) {
                        sb.append(stackTraceElement).append('\n');
                    }

                    e.setTrace(sb.toString());
                }
            }
        });
    }

    @Override
    public void onClusterStateChanged(ClusterNodeInfo clusterNodeInfo) {
        this.clusterNodeInfo = clusterNodeInfo;
        if (!closed) {
            Instant nextSchedule = jobManager.getNextSchedule(clusterNodeInfo.getClusterPosition(), clusterNodeInfo.getClusterSize(), partitionKey, jobContext.isScheduleRefreshedOnly() ? jobInstancesToSchedule.keySet() : null);
            if (nextSchedule == null) {
                resetEarliestKnownSchedule();
            } else {
                refreshSchedules(nextSchedule.toEpochMilli());
            }
            List<JobInstance<?>> runningJobInstances = jobManager.getRunningJobInstances(clusterNodeInfo.getClusterPosition(), clusterNodeInfo.getClusterSize(), partitionKey);
            ClusterStateManager clusterStateManager = actorContext.getService(ClusterStateManager.class);
            List<Serializable> jobInstanceIds = new ArrayList<>(runningJobInstances.size());
            for (Iterator<JobInstance<?>> iterator = runningJobInstances.iterator(); iterator.hasNext(); ) {
                JobInstance<?> runningJobInstance = iterator.next();
                JobInstanceExecution execution = longRunningJobInstances.get(runningJobInstance.getId());
                if (execution == null) {
                    jobInstanceIds.add((Serializable) runningJobInstance.getId());
                } else {
                    iterator.remove();
                }
            }
            if (!jobInstanceIds.isEmpty()) {
                LockService lockService = clusterStateManager.getLockService();
                JobSchedulerStatusEvent statusEvent = new JobSchedulerStatusEvent(jobInstanceIds.toArray(new Serializable[0]));
                Map<ClusterNodeInfo, Future<int[]>> futureMap = clusterStateManager.fireEventExcludeSelf(statusEvent);
                if (futureMap.isEmpty()) {
                    for (JobInstance<?> runningJobInstance : runningJobInstances) {
                        scheduleLongRunning(lockService, runningJobInstance);
                    }
                } else {
                    try {
                        for (Future<int[]> future : futureMap.values()) {
                            int[] clusterPositions = future.get();
                            for (int i = 0; i < clusterPositions.length; i++) {
                                if (clusterPositions[i] == -1) {
                                    // To the best of our knowledge, the job instance does not run in the cluster, so we have schedule it
                                    scheduleLongRunning(lockService, runningJobInstances.get(i));
                                }
                            }
                        }
                    } catch (Exception ex) {
                        throw new JobException("Could not get the cluster position state for running job instances.", ex);
                    }
                }
            }
        }
    }

    private void scheduleLongRunning(LockService lockService, JobInstance<?> jobInstance) {
        Instant now = clock.instant();
        MutableJobInstanceProcessingContext jobProcessingContext = new MutableJobInstanceProcessingContext(jobContext, partitionKey, processCount);
        jobProcessingContext.setPartitionCount(clusterNodeInfo.getClusterSize());
        jobProcessingContext.setPartitionId(clusterNodeInfo.getClusterPosition());
        jobProcessingContext.setLastProcessed(jobInstance.getLastProcessed());
        MutableScheduleContext scheduleContext = new MutableScheduleContext();
        Instant lastExecutionTime = jobInstance.getLastExecutionTime();
        if (lastExecutionTime == null) {
            lastExecutionTime = now;
        }
        scheduleContext.setLastScheduleTime(jobInstance.getScheduleTime().toEpochMilli());
        scheduleContext.setLastExecutionTime(lastExecutionTime.toEpochMilli());
        JobInstanceProcessor jobInstanceProcessor = jobContext.getJobInstanceProcessor(jobInstance);
        JobInstanceExecution execution = new JobInstanceExecution(jobInstance, jobInstance.getDeferCount(), scheduleContext, jobProcessingContext, null);
        jobInstance.setLastExecutionTime(Instant.now());
        jobInstance.markRunning(jobProcessingContext);
        jobManager.updateJobInstance(jobInstance);
        longRunningJobInstances.put(jobInstance.getId(), execution);
        Lock lock = lockService.getLock("jobInstance/" + jobInstance.getId());
        execution.future = scheduler.submit(new NotifyingSpecialThrowingCallable(jobInstanceProcessor, execution, lock));
    }

    @Override
    public void refreshSchedules(long earliestNewSchedule) {
        long delayMillis = rescan(earliestNewSchedule);
        if (delayMillis != -1L) {
            actorContext.getActorManager().rescheduleActor(actorName, delayMillis);
        }
    }

    @Override
    public void reschedule(JobInstance<?> jobInstance) {
        if (jobContext.isScheduleRefreshedOnly()) {
            jobInstancesToSchedule.put(jobInstance, Boolean.TRUE);
        }
        actorContext.getActorManager().rescheduleActor(actorName, 0);
    }

    private long rescan(long earliestNewSchedule) {
        if (!closed) {
            if (earliestNewSchedule == 0) {
                // This is special. We want to recheck schedules, no matter what
                ClusterNodeInfo clusterNodeInfo = this.clusterNodeInfo;
                Instant nextSchedule = jobManager.getNextSchedule(clusterNodeInfo.getClusterPosition(), clusterNodeInfo.getClusterSize(), partitionKey, jobContext.isScheduleRefreshedOnly() ? jobInstancesToSchedule.keySet() : null);
                // No new schedules available
                if (nextSchedule == null) {
                    resetEarliestKnownSchedule();
                    return -1L;
                }
                earliestNewSchedule = nextSchedule.toEpochMilli();
            }
            long earliestKnownSchedule = this.earliestKnownSchedule.get();
            // We use lower or equal because a re-schedule event could be cause from within a processor
            if (earliestNewSchedule <= earliestKnownSchedule) {
                // There are new job instances that should be scheduled before our known earliest job instance

                // We just reschedule based on the new earliest schedule if it stays that
                if (!updateEarliestKnownSchedule(earliestKnownSchedule, earliestNewSchedule)) {
                    // A different thread won the race and apparently has a job instance that should be scheduled earlier
                    return -1L;
                }
                long delayMillis = earliestNewSchedule - clock.millis();

                delayMillis = delayMillis < 0 ? 0 : delayMillis;
                return delayMillis;
            }
        }

        return -1L;
    }

    private boolean updateEarliestKnownSchedule(long oldValue, long newValue) {
        do {
            if (earliestKnownSchedule.compareAndSet(oldValue, newValue)) {
                return true;
            }

            oldValue = earliestKnownSchedule.get();
            // We use lower or equal because a re-schedule event could be cause from within a processor
        } while (oldValue <= newValue);

        return false;
    }

    private boolean updateEarliestKnownSchedule(long newValue) {
        long oldValue = earliestKnownSchedule.get();
        // We use lower or equal because a re-schedule event could be cause from within a processor
        while (oldValue <= newValue) {
            if (earliestKnownSchedule.compareAndSet(oldValue, newValue)) {
                return true;
            }

            oldValue = earliestKnownSchedule.get();
        }

        return false;
    }

    private void resetEarliestKnownSchedule() {
        long earliestKnownSchedule = this.earliestKnownSchedule.get();
        // Only reset the value if the currently known earliest schedule is in the past
        if (earliestKnownSchedule < clock.millis()) {
            updateEarliestKnownSchedule(earliestKnownSchedule, Long.MAX_VALUE);
        }
    }

    @Override
    public int getClusterPosition(JobInstance<?> jobInstance) {
        if (!jobInstance.isLongRunning()) {
            return -1;
        }
        JobInstanceExecution execution = longRunningJobInstances.get(jobInstance.getId());
        if (execution == null) {
            JobSchedulerStatusEvent event = new JobSchedulerStatusEvent(new Serializable[] { (Serializable) jobInstance.getId() });
            Map<ClusterNodeInfo, Future<int[]>> result = actorContext.getService(ClusterStateManager.class).fireEventExcludeSelf(event);
            try {
                for (Map.Entry<ClusterNodeInfo, Future<int[]>> entry : result.entrySet()) {
                    int position = entry.getValue().get()[0];
                    if (position != -1) {
                        return position;
                    }
                }
            } catch (Exception e) {
                throw new JobException("Could not retrieve cluster position for job instance: " + jobInstance , e);
            }
            return -1;
        } else {
            return clusterNodeInfo.getClusterPosition();
        }
    }

    @Override
    public String getTrace(JobInstance<?> jobInstance) {
        if (!jobInstance.isLongRunning()) {
            return null;
        }
        JobInstanceExecution execution = longRunningJobInstances.get(jobInstance.getId());
        if (execution == null) {
            JobSchedulerTraceEvent event = new JobSchedulerTraceEvent((Serializable) jobInstance.getId());
            Map<ClusterNodeInfo, Future<String>> result = actorContext.getService(ClusterStateManager.class).fireEventExcludeSelf(event);
            try {
                for (Map.Entry<ClusterNodeInfo, Future<String>> entry : result.entrySet()) {
                    String trace = entry.getValue().get();
                    if (trace != null) {
                        return trace;
                    }
                }
            } catch (Exception e) {
                throw new JobException("Could not retrieve trace for job instance: " + jobInstance , e);
            }
            return null;
        } else {
            Thread thread = execution.thread;
            if (thread != null) {
                StackTraceElement[] stackTrace = thread.getStackTrace();
                StringBuilder sb = new StringBuilder();
                for (StackTraceElement stackTraceElement : stackTrace) {
                    sb.append(stackTraceElement).append('\n');
                }

                return sb.toString();
            }
            return null;
        }
    }

    @Override
    public void cancel(JobInstance<?> jobInstance) {
        if (!jobInstance.isLongRunning()) {
            return;
        }
        JobInstanceExecution execution = longRunningJobInstances.get(jobInstance.getId());
        if (execution == null) {
            JobSchedulerCancelEvent event = new JobSchedulerCancelEvent((Serializable) jobInstance.getId());
            actorContext.getService(ClusterStateManager.class).fireEventExcludeSelf(event, false);
        } else {
            execution.future.cancel(true);
        }
    }

    @Override
    public void stop() {
        closed = true;
        actorContext.stop();
    }

    @Override
    public void stop(long timeout, TimeUnit unit) throws InterruptedException {
        closed = true;
        actorContext.stop(timeout, unit);
    }

    private class JobInstanceRunner implements ScheduledActor, Callable<ActorRunResult> {

        private final long maxBackOff = 10_000L; // 10 seconds by default
        private final long baseBackOff = 1_000L; // 1 second by default
        private int retryAttempt;

        @Override
        public ActorRunResult call() throws Exception {
            JobManager jobManager = jobContext.getJobManager();
            LockService lockService = actorContext.getService(ClusterStateManager.class).getLockService();
            ClusterNodeInfo clusterNodeInfo = JobSchedulerImpl.this.clusterNodeInfo;
            List<JobInstance<?>> jobInstancesToProcess = jobManager.getJobInstancesToProcess(clusterNodeInfo.getClusterPosition(), clusterNodeInfo.getClusterSize(), processCount, partitionKey, jobContext.isScheduleRefreshedOnly() ? jobInstancesToSchedule.keySet() : null);
            int size = jobInstancesToProcess.size();
            if (size == 0) {
                return ActorRunResult.suspend();
            }
            Instant earliestNewSchedule = Instant.MAX;
            List<JobInstanceExecution> jobInstanceExecutions = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                JobInstance<?> jobInstance = jobInstancesToProcess.get(i);
                Instant now = clock.instant();
                MutableJobInstanceProcessingContext jobProcessingContext = new MutableJobInstanceProcessingContext(jobContext, partitionKey, processCount);
                jobProcessingContext.setPartitionCount(clusterNodeInfo.getClusterSize());
                jobProcessingContext.setPartitionId(clusterNodeInfo.getClusterPosition());
                jobProcessingContext.setLastProcessed(jobInstance.getLastProcessed());
                MutableScheduleContext scheduleContext = new MutableScheduleContext();
                boolean future = false;
                Instant lastExecutionTime = jobInstance.getLastExecutionTime();
                if (lastExecutionTime == null) {
                    lastExecutionTime = now;
                }
                scheduleContext.setLastScheduleTime(jobInstance.getScheduleTime().toEpochMilli());
                scheduleContext.setLastExecutionTime(lastExecutionTime.toEpochMilli());
                try {
                    Instant deadline = jobInstance.getJobConfiguration().getDeadline();
                    if (deadline != null && deadline.compareTo(now) <= 0) {
                        jobInstance.markDeadlineReached(jobProcessingContext);
                        jobContext.forEachJobInstanceListeners(new JobInstanceErrorListenerConsumer(jobInstance, jobProcessingContext));
                    } else {
                        Set<? extends TimeFrame> executionTimeFrames = jobInstance.getJobConfiguration().getExecutionTimeFrames();
                        if (TimeFrame.isContained(executionTimeFrames, now)) {
                            int deferCount = jobInstance.getDeferCount();
                            JobInstanceProcessor jobInstanceProcessor = jobContext.getJobInstanceProcessor(jobInstance);
                            Future<Object> f;
                            // By default we execute transactional job instance processors synchronously within our transaction
                            if (jobInstanceProcessor.isTransactional()) {
                                f = new SyncJobInstanceProcessorFuture(jobInstanceProcessor, jobInstance, jobProcessingContext);
                                jobInstanceExecutions.add(new JobInstanceExecution(jobInstance, deferCount, scheduleContext, jobProcessingContext, f));
                            } else {
                                jobInstance.setLastExecutionTime(Instant.now());
                                // A long running job instance has to be marked as "running" early to avoid rescheduling
                                if (jobInstance.isLongRunning()) {
                                    jobInstance.markRunning(jobProcessingContext);
                                    jobManager.updateJobInstance(jobInstance);
                                    JobInstanceExecution execution = new JobInstanceExecution(jobInstance, deferCount, scheduleContext, jobProcessingContext, null);
                                    // If the long running job is not yet done, we have to register a watcher
                                    longRunningJobInstances.put(jobInstance.getId(), execution);
                                    Lock lock = lockService.getLock("jobInstance/" + jobInstance.getId());
                                    execution.future = scheduler.submit(new NotifyingSpecialThrowingCallable(jobInstanceProcessor, execution, lock));
                                } else {
                                    f = scheduler.submit(new SpecialThrowingCallable(jobInstanceProcessor, jobInstance, jobProcessingContext));
                                    jobInstanceExecutions.add(new JobInstanceExecution(jobInstance, deferCount, scheduleContext, jobProcessingContext, f));
                                }
                            }
                            future = true;
                        } else {
                            Instant nextSchedule = TimeFrame.getNearestTimeFrameSchedule(executionTimeFrames, now);
                            if (nextSchedule == Instant.MAX) {
                                if (LOG.isLoggable(Level.FINEST)) {
                                    LOG.log(Level.FINEST, "Dropping job instance: " + jobInstance);
                                }
                                jobInstance.markDropped(jobProcessingContext);
                                jobContext.forEachJobInstanceListeners(new JobInstanceErrorListenerConsumer(jobInstance, jobProcessingContext));
                            } else {
                                if (LOG.isLoggable(Level.FINEST)) {
                                    LOG.log(Level.FINEST, "Deferring job instance to " + nextSchedule);
                                }
                                jobInstance.markDeferred(jobProcessingContext, nextSchedule);
                                if (jobInstance.getState() == JobInstanceState.DROPPED) {
                                    jobContext.forEachJobInstanceListeners(new JobInstanceErrorListenerConsumer(jobInstance, jobProcessingContext));
                                }
                                if (jobInstance.getScheduleTime().isBefore(earliestNewSchedule)) {
                                    earliestNewSchedule = jobInstance.getScheduleTime();
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOG.log(Level.SEVERE, "An error occurred in the job scheduler", t);
                    jobInstance.markFailed(jobProcessingContext, t);
                    if (jobContext.isScheduleRefreshedOnly()) {
                        jobInstancesToSchedule.remove(jobInstance);
                    }
                    jobContext.forEachJobInstanceListeners(new JobInstanceErrorListenerConsumer(jobInstance, jobProcessingContext));
                } finally {
                    if (!future) {
                        if (jobContext.isScheduleRefreshedOnly() && jobInstance.getState() != JobInstanceState.NEW) {
                            jobInstancesToSchedule.remove(jobInstance);
                        }
                        jobManager.updateJobInstance(jobInstance);
                    }
                }
            }

            Instant rescheduleRateLimitTime = null;
            for (int i = 0; i < jobInstanceExecutions.size(); i++) {
                JobInstanceExecution execution = jobInstanceExecutions.get(i);
                JobInstance<?> jobInstance = execution.jobInstance;
                MutableJobInstanceProcessingContext jobProcessingContext = execution.jobProcessingContext;
                MutableScheduleContext scheduleContext = execution.scheduleContext;
                int deferCount = execution.deferCount;
                Future<Object> future = execution.future;
                boolean success = true;
                try {
                    Object lastProcessed = future.get();
                    jobProcessingContext.setLastProcessed(lastProcessed);
                    scheduleContext.setLastCompletionTime(clock.millis());

                    if (jobInstance.getState() == JobInstanceState.NEW) {
                        Instant nextSchedule = jobInstance.nextSchedule(jobContext, scheduleContext);
                        // This is essential for limited time or fixed time schedules. When these are done, they always return a nextSchedule equal to getLastScheduledExecutionTime()
                        if (nextSchedule.toEpochMilli() != scheduleContext.getLastScheduleTime()) {
                            // This is a recurring job that needs rescheduling
                            if (jobInstance.getDeferCount() == deferCount) {
                                jobInstance.onChunkSuccess(jobProcessingContext);
                                jobContext.forEachJobInstanceListeners(new JobInstanceChunkSuccessListenerConsumer(jobInstance, jobProcessingContext));
                            }
                            jobInstance.setScheduleTime(nextSchedule);
                            if (jobInstance.getScheduleTime().isBefore(earliestNewSchedule)) {
                                earliestNewSchedule = jobInstance.getScheduleTime();
                            }
                            continue;
                        } else if (lastProcessed != null) {
                            // Chunk processing
                            if (jobInstance.getDeferCount() == deferCount) {
                                jobInstance.onChunkSuccess(jobProcessingContext);
                                jobContext.forEachJobInstanceListeners(new JobInstanceChunkSuccessListenerConsumer(jobInstance, jobProcessingContext));
                            }
                            if (jobInstance.getScheduleTime().isBefore(earliestNewSchedule)) {
                                earliestNewSchedule = jobInstance.getScheduleTime();
                            }
                            continue;
                        }
                    }

                    // We only mark the job as DONE when it was properly executed before and still has the NEW state
                    if (jobInstance.getState() == JobInstanceState.NEW) {
                        jobInstance.markDone(jobProcessingContext, lastProcessed);
                        jobContext.forEachJobInstanceListeners(new JobInstanceSuccessListenerConsumer(jobInstance, jobProcessingContext));
                    } else if (jobInstance.getState() == JobInstanceState.DONE) {
                        jobContext.forEachJobInstanceListeners(new JobInstanceSuccessListenerConsumer(jobInstance, jobProcessingContext));
                    }
                } catch (ExecutionException ex) {
                    Throwable t;
                    if (ex.getCause() instanceof CallableThrowable) {
                        t = ex.getCause().getCause();
                    } else {
                        t = ex.getCause();
                    }
                    if (t instanceof JobRateLimitException) {
                        JobRateLimitException e = (JobRateLimitException) t;
                        LOG.log(Level.FINEST, "Deferring job instance due to rate limit", e);
                        if (rescheduleRateLimitTime == null) {
                            if (e.getDeferMillis() != -1) {
                                rescheduleRateLimitTime = clock.instant().plus(e.getDeferMillis(), ChronoUnit.MILLIS);
                            } else {
                                rescheduleRateLimitTime = clock.instant().plus(rateLimitDeferSeconds, ChronoUnit.SECONDS);
                            }
                        }
                        jobInstance.setScheduleTime(rescheduleRateLimitTime);
                        if (jobInstance.getScheduleTime().isBefore(earliestNewSchedule)) {
                            earliestNewSchedule = jobInstance.getScheduleTime();
                        }
                    } else if (t instanceof JobTemporaryException) {
                        JobTemporaryException e = (JobTemporaryException) t;
                        LOG.log(Level.FINEST, "Deferring job instance due to temporary error", e);
                        if (e.getDeferMillis() != -1) {
                            jobInstance.setScheduleTime(clock.instant().plus(e.getDeferMillis(), ChronoUnit.MILLIS));
                        } else {
                            jobInstance.setScheduleTime(clock.instant().plus(temporaryErrorDeferSeconds, ChronoUnit.SECONDS));
                        }
                        if (jobInstance.getScheduleTime().isBefore(earliestNewSchedule)) {
                            earliestNewSchedule = jobInstance.getScheduleTime();
                        }
                    } else {
                        LOG.log(Level.SEVERE, "An error occurred in the job instance processor", t);
                        success = false;
                        TransactionSupport transactionSupport = jobContext.getTransactionSupport();
                        transactionSupport.transactional(jobContext, COMPLETION_TX_TIMEOUT, true, () -> {
                            jobContext.forEachJobInstanceListeners(new JobInstanceErrorListenerConsumer(jobInstance, jobProcessingContext));
                            jobInstance.markFailed(jobProcessingContext, t);
                            jobManager.updateJobInstance(jobInstance);
                            return null;
                        }, t2 -> {
                            LOG.log(Level.SEVERE, "An error occurred in the job instance error handler", t2);
                        });
                    }
                } finally {
                    if (jobContext.isScheduleRefreshedOnly() && jobInstance.getState() != JobInstanceState.NEW) {
                        jobInstancesToSchedule.remove(jobInstance);
                    }
                    if (success) {
                        jobManager.updateJobInstance(jobInstance);
                    }
                }
            }

            if (earliestNewSchedule == Instant.MAX) {
                return ActorRunResult.suspend();
            }

            long delay = earliestNewSchedule.toEpochMilli() - clock.millis();
            if (LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, "Rescheduling in: {0}", delay);
            }
            return ActorRunResult.rescheduleIn(delay);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public ActorRunResult work() {
            if (closed) {
                return ActorRunResult.done();
            }
            TransactionSupport transactionSupport = jobContext.getTransactionSupport();
            long earliestKnownNotificationSchedule = JobSchedulerImpl.this.earliestKnownSchedule.get();
            ActorRunResult result = transactionSupport.transactional(
                jobContext,
                transactionTimeout,
                false,
                this,
                t -> LOG.log(Level.SEVERE, "An error occurred in the job scheduler", t)
            );

            if (closed) {
                return ActorRunResult.done();
            } else if (result == null) {
                // An error occurred like e.g. a TX timeout or a temporary DB issue. We do exponential back-off
                long delay = getWaitTime(maxBackOff, baseBackOff, retryAttempt++);
                LOG.log(Level.INFO, "Rescheduling due to error in: {0}", delay);
                return ActorRunResult.rescheduleIn(delay);
            }

            retryAttempt = 0;
            if (result.isSuspend()) {
                // This will reschedule based on the next schedule
                updateEarliestKnownSchedule(earliestKnownNotificationSchedule, Long.MAX_VALUE);
                if (LOG.isLoggable(Level.FINEST)) {
                    LOG.finest("Rescan due to suspend");
                }
                long delayMillis = rescan(0L);
                if (delayMillis != -1L) {
                    if (LOG.isLoggable(Level.FINEST)) {
                        LOG.log(Level.FINEST, "Rescheduling after suspend in: {0}", delayMillis);
                    }
                    return ActorRunResult.rescheduleIn(delayMillis);
                }
            } else {
                updateEarliestKnownSchedule(earliestKnownNotificationSchedule, clock.millis() + result.getDelayMillis());
            }
            // NOTE: we don't need to update earliestKnownNotificationSchedule when rescheduling immediately
            return result;
        }
    }

    private static class SpecialThrowingCallable implements Callable<Object> {
        final JobInstanceProcessor jobInstanceProcessor;
        final JobInstance<?> jobInstance;
        final MutableJobInstanceProcessingContext jobProcessingContext;

        public SpecialThrowingCallable(JobInstanceProcessor jobInstanceProcessor, JobInstance<?> jobInstance, MutableJobInstanceProcessingContext jobProcessingContext) {
            this.jobInstanceProcessor = jobInstanceProcessor;
            this.jobInstance = jobInstance;
            this.jobProcessingContext = jobProcessingContext;
        }

        @Override
        public Object call() throws Exception {
            try {
                return jobInstanceProcessor.process(jobInstance, jobProcessingContext);
            } catch (Exception ex) {
                CallableThrowable.doThrow(ex);
                return null;
            }
        }
    }

    private class NotifyingSpecialThrowingCallable implements Callable<Object> {

        private final JobInstanceProcessor jobInstanceProcessor;
        private final JobInstanceExecution execution;
        private final Lock lock;

        public NotifyingSpecialThrowingCallable(JobInstanceProcessor jobInstanceProcessor, JobInstanceExecution execution, Lock lock) {
            this.jobInstanceProcessor = jobInstanceProcessor;
            this.execution = execution;
            this.lock = lock;
        }

        @Override
        public Object call() throws Exception {
            JobInstance<?> jobInstance = execution.jobInstance;
            if (!lock.tryLock()) {
                // Apparently the job is already running on a different node, so we can skip it here
                longRunningJobInstances.remove(jobInstance.getId());
                return null;
            }
            execution.thread = Thread.currentThread();
            MutableScheduleContext scheduleContext = execution.scheduleContext;
            int deferCount = execution.deferCount;
            MutableJobInstanceProcessingContext jobProcessingContext = execution.jobProcessingContext;
            try {
                Object lastProcessed = jobInstanceProcessor.process(jobInstance, jobProcessingContext);
                TransactionSupport transactionSupport = jobContext.getTransactionSupport();
                // Flush the completion status
                transactionSupport.transactional(jobContext, COMPLETION_TX_TIMEOUT, false, () -> {
                    try {
                        jobProcessingContext.setLastProcessed(lastProcessed);
                        scheduleContext.setLastCompletionTime(clock.millis());

                        if (jobInstance.getState() == JobInstanceState.NEW) {
                            Instant nextSchedule = jobInstance.nextSchedule(jobContext, scheduleContext);
                            // This is essential for limited time or fixed time schedules. When these are done, they always return a nextSchedule equal to getLastScheduledExecutionTime()
                            if (nextSchedule.toEpochMilli() != scheduleContext.getLastScheduleTime()) {
                                // This is a recurring job that needs rescheduling
                                if (jobInstance.getDeferCount() == deferCount) {
                                    jobInstance.onChunkSuccess(jobProcessingContext);
                                    jobContext.forEachJobInstanceListeners(new JobInstanceChunkSuccessListenerConsumer(jobInstance, jobProcessingContext));
                                }
                                jobInstance.setScheduleTime(nextSchedule);
                                updateEarliestKnownSchedule(jobInstance.getScheduleTime().toEpochMilli());
                                return null;
                            } else if (lastProcessed != null) {
                                // Chunk processing
                                if (jobInstance.getDeferCount() == deferCount) {
                                    jobInstance.onChunkSuccess(jobProcessingContext);
                                    jobContext.forEachJobInstanceListeners(new JobInstanceChunkSuccessListenerConsumer(jobInstance, jobProcessingContext));
                                }
                                updateEarliestKnownSchedule(jobInstance.getScheduleTime().toEpochMilli());
                                return null;
                            }
                        }

                        // We only mark the job as DONE when it was properly executed before and still has the NEW state
                        if (jobInstance.getState() == JobInstanceState.NEW) {
                            jobInstance.markDone(jobProcessingContext, lastProcessed);
                            jobContext.forEachJobInstanceListeners(new JobInstanceSuccessListenerConsumer(jobInstance, jobProcessingContext));
                        } else if (jobInstance.getState() == JobInstanceState.DONE) {
                            jobContext.forEachJobInstanceListeners(new JobInstanceSuccessListenerConsumer(jobInstance, jobProcessingContext));
                        }
                        return null;
                    } catch (Throwable t) {
                        jobInstance.markFailed(jobProcessingContext, t);
                        sneakyThrow(t);
                        return null;
                    } finally {
                        if (jobContext.isScheduleRefreshedOnly() && jobInstance.getState() != JobInstanceState.NEW) {
                            jobInstancesToSchedule.remove(jobInstance);
                        }
                        jobManager.updateJobInstance(jobInstance);
                    }
                }, t2 -> {
                    LOG.log(Level.SEVERE, "An error occurred in the long running job instance completion handler", t2);
                });
                return lastProcessed;
            } catch (Throwable t) {
                TransactionSupport transactionSupport = jobContext.getTransactionSupport();
                transactionSupport.transactional(jobContext, COMPLETION_TX_TIMEOUT, false, () -> {
                    if (t instanceof JobRateLimitException) {
                        JobRateLimitException e = (JobRateLimitException) t;
                        LOG.log(Level.FINEST, "Deferring job instance due to rate limit", e);
                        Instant rescheduleRateLimitTime;
                        if (e.getDeferMillis() != -1) {
                            rescheduleRateLimitTime = clock.instant().plus(e.getDeferMillis(), ChronoUnit.MILLIS);
                        } else {
                            rescheduleRateLimitTime = clock.instant().plus(rateLimitDeferSeconds, ChronoUnit.SECONDS);
                        }
                        jobInstance.setScheduleTime(rescheduleRateLimitTime);
                        updateEarliestKnownSchedule(jobInstance.getScheduleTime().toEpochMilli());
                    } else if (t instanceof JobTemporaryException) {
                        JobTemporaryException e = (JobTemporaryException) t;
                        LOG.log(Level.FINEST, "Deferring job instance due to temporary error", e);
                        if (e.getDeferMillis() != -1) {
                            jobInstance.setScheduleTime(clock.instant().plus(e.getDeferMillis(), ChronoUnit.MILLIS));
                        } else {
                            jobInstance.setScheduleTime(clock.instant().plus(temporaryErrorDeferSeconds, ChronoUnit.SECONDS));
                        }
                        updateEarliestKnownSchedule(jobInstance.getScheduleTime().toEpochMilli());
                    } else {
                        LOG.log(Level.SEVERE, "An error occurred in the job instance processor", t);
                        jobContext.forEachJobInstanceListeners(new JobInstanceErrorListenerConsumer(jobInstance, jobProcessingContext));
                        jobInstance.markFailed(jobProcessingContext, t);
                        jobManager.updateJobInstance(jobInstance);
                    }
                    return null;
                }, t2 -> {
                    LOG.log(Level.SEVERE, "An error occurred in the long running job instance error handler", t2);
                });
                CallableThrowable.doThrow(t);
                return null;
            } finally {
                longRunningJobInstances.remove(jobInstance.getId());
                execution.thread = null;
                lock.unlock();
            }
        }
    }

    static <T extends Throwable> void sneakyThrow(Throwable e) throws T {
        throw (T) e;
    }

    // We need this special Throwable wrapper for exceptions,
    // as scheduled callables that throw exceptions are handled differently in some containers(Wildfly)
    // Wildfly will just log out the error and throw a different exception rather than propagating the error correctly to the Future
    // Since we want the error, we have to wrap it in a Throwable which could normally not be thrown, to trick the container
    private static class CallableThrowable extends Throwable {
        public CallableThrowable(Throwable cause) {
            super(cause);
        }

        private static <T extends Throwable> void doThrow(Throwable e) throws T {
            throw (T) new CallableThrowable(e);
        }
    }

    private static long getWaitTime(final long maximum, final long base, final long attempt) {
        final long expWait = ((long) Math.pow(2, attempt)) * base;
        return expWait <= 0 ? maximum : Math.min(maximum, expWait);
    }

    private static class JobInstanceChunkSuccessListenerConsumer implements Consumer<JobInstanceListener> {
        private final JobInstance<?> jobInstance;
        private final MutableJobInstanceProcessingContext jobProcessingContext;

        public JobInstanceChunkSuccessListenerConsumer(JobInstance<?> jobInstance, MutableJobInstanceProcessingContext jobProcessingContext) {
            this.jobInstance = jobInstance;
            this.jobProcessingContext = jobProcessingContext;
        }

        @Override
        public void accept(JobInstanceListener listener) {
            listener.onJobInstanceChunkSuccess(jobInstance, jobProcessingContext);
        }
    }

    private static class JobInstanceSuccessListenerConsumer implements Consumer<JobInstanceListener> {
        private final JobInstance<?> jobInstance;
        private final MutableJobInstanceProcessingContext jobProcessingContext;

        public JobInstanceSuccessListenerConsumer(JobInstance<?> jobInstance, MutableJobInstanceProcessingContext jobProcessingContext) {
            this.jobInstance = jobInstance;
            this.jobProcessingContext = jobProcessingContext;
        }

        @Override
        public void accept(JobInstanceListener listener) {
            listener.onJobInstanceSuccess(jobInstance, jobProcessingContext);
        }
    }

    private static class JobInstanceErrorListenerConsumer implements Consumer<JobInstanceListener> {
        private final JobInstance<?> jobInstance;
        private final MutableJobInstanceProcessingContext jobProcessingContext;

        public JobInstanceErrorListenerConsumer(JobInstance<?> jobInstance, MutableJobInstanceProcessingContext jobProcessingContext) {
            this.jobInstance = jobInstance;
            this.jobProcessingContext = jobProcessingContext;
        }

        @Override
        public void accept(JobInstanceListener listener) {
            listener.onJobInstanceError(jobInstance, jobProcessingContext);
        }
    }

    private static class SyncJobInstanceProcessorFuture implements Future<Object> {

        private final JobInstanceProcessor jobInstanceProcessor;
        private final JobInstance<?> jobInstance;
        private final JobInstanceProcessingContext<?> processingContext;
        private boolean done;
        private Object result;
        private Exception exception;

        public SyncJobInstanceProcessorFuture(JobInstanceProcessor jobInstanceProcessor, JobInstance<?> jobInstance, JobInstanceProcessingContext<?> processingContext) {
            this.jobInstanceProcessor = jobInstanceProcessor;
            this.jobInstance = jobInstance;
            this.processingContext = processingContext;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object get() throws InterruptedException, ExecutionException {
            if (done) {
                if (exception == null) {
                    return result;
                } else {
                    throw new ExecutionException(exception);
                }
            }

            done = true;
            try {
                jobInstance.setLastExecutionTime(Instant.now());
                return result = jobInstanceProcessor.process(jobInstance, processingContext);
            } catch (Exception e) {
                throw new ExecutionException(exception = e);
            }
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }

    private static class JobInstanceExecution {
        private final JobInstance<?> jobInstance;
        private final int deferCount;
        private final MutableScheduleContext scheduleContext;
        private final MutableJobInstanceProcessingContext jobProcessingContext;
        private volatile Thread thread;
        private Future<Object> future;

        public JobInstanceExecution(JobInstance<?> jobInstance, int deferCount, MutableScheduleContext scheduleContext, MutableJobInstanceProcessingContext jobProcessingContext, Future<Object> future) {
            this.jobInstance = jobInstance;
            this.deferCount = deferCount;
            this.scheduleContext = scheduleContext;
            this.jobProcessingContext = jobProcessingContext;
            this.future = future;
        }
    }

    private static class MutableScheduleContext implements ScheduleContext {

        private long lastScheduleTime;
        private long lastExecutionTime;
        private long lastCompletionTime;

        @Override
        public long getLastScheduleTime() {
            return lastScheduleTime;
        }

        public void setLastScheduleTime(long lastScheduleTime) {
            this.lastScheduleTime = lastScheduleTime;
        }

        @Override
        public long getLastExecutionTime() {
            return lastExecutionTime;
        }

        public void setLastExecutionTime(long lastExecutionTime) {
            this.lastExecutionTime = lastExecutionTime;
        }

        @Override
        public long getLastCompletionTime() {
            return lastCompletionTime;
        }

        public void setLastCompletionTime(long lastCompletionTime) {
            this.lastCompletionTime = lastCompletionTime;
        }
    }

    private static class MutableJobInstanceProcessingContext implements JobInstanceProcessingContext<Object> {

        private final JobContext jobContext;
        private final PartitionKey partitionKey;
        private final int processCount;
        private int partitionId;
        private int partitionCount;
        private Object lastProcessed;

        public MutableJobInstanceProcessingContext(JobContext jobContext, PartitionKey partitionKey, int processCount) {
            this.jobContext = jobContext;
            this.partitionKey = partitionKey;
            this.processCount = processCount;
        }

        @Override
        public JobContext getJobContext() {
            return jobContext;
        }

        @Override
        public PartitionKey getPartitionKey() {
            return partitionKey;
        }

        @Override
        public int getProcessCount() {
            return processCount;
        }

        @Override
        public int getPartitionId() {
            return partitionId;
        }

        public void setPartitionId(int partitionId) {
            this.partitionId = partitionId;
        }

        @Override
        public int getPartitionCount() {
            return partitionCount;
        }

        public void setPartitionCount(int partitionCount) {
            this.partitionCount = partitionCount;
        }

        @Override
        public Object getLastProcessed() {
            return lastProcessed;
        }

        public void setLastProcessed(Object lastProcessed) {
            this.lastProcessed = lastProcessed;
        }
    }
}
