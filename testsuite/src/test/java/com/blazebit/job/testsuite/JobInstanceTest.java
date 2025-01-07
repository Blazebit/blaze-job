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
package com.blazebit.job.testsuite;

import com.blazebit.actor.ActorContext;
import com.blazebit.actor.spi.ClusterNodeInfo;
import com.blazebit.actor.spi.ClusterStateListener;
import com.blazebit.actor.spi.ClusterStateManager;
import com.blazebit.actor.spi.LockService;
import com.blazebit.actor.spi.StateReturningEvent;
import com.blazebit.job.JobContext;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobInstanceState;
import com.blazebit.job.JobRateLimitException;
import com.blazebit.job.JobTemporaryException;
import com.blazebit.job.memory.model.JobConfiguration;
import com.blazebit.job.memory.model.TimeFrame;
import com.blazebit.job.spi.JobInstanceProcessorFactory;
import com.blazebit.job.spi.PartitionKeyProvider;
import com.blazebit.job.spi.TransactionSupport;
import org.junit.Test;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JobInstanceTest extends AbstractJobTest {

    private BlockingQueue<Object> sink;

    public JobInstanceTest() {
        this.sink = new ArrayBlockingQueue<>(1024);
    }

    @Test
    public void testJobInstanceSchedule() throws Exception {
        // GIVEN
        this.jobContext = builder()
                .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                    sink.add(jobInstance);
                    return null;
                })))
                .createContext();

        // WHEN
        jobContext.getJobManager().addJobInstance(new SimpleJobInstance());

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(1, sink.size());
    }

    @Test
    public void testFailSchedulerJobInstance() throws Exception {
        // GIVEN
        this.jobContext = builder().createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance() {
            @Override
            public JobConfiguration getJobConfiguration() {
                throw new RuntimeException();
            }
        };

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.FAILED, jobInstance.getState());
    }

    @Test
    public void testFailJobInstance() throws Exception {
        // GIVEN
        this.jobContext = builder()
                .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                    throw new RuntimeException();
                })))
                .createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.FAILED, jobInstance.getState());
    }

    @Test
    public void testFailRateLimitJobInstance() throws Exception {
        // GIVEN
        Clock clock = Clock.fixed(Instant.parse("2018-01-01T00:00:00.00Z"), ZoneOffset.UTC);
        // We wait for 3x setScheduleTime invocations. Constructor, setter and via rate limiting
        this.jobContext = builder(3).withService(Clock.class, clock)
                .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                    throw new JobRateLimitException(1000L);
                })))
                .createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance() {
            @Override
            public void setScheduleTime(Instant scheduleTime) {
                super.setScheduleTime(scheduleTime);
                latch.countDown();
            }
        };
        jobInstance.setCreationTime(clock.instant());
        jobInstance.setScheduleTime(jobInstance.getCreationTime());

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.NEW, jobInstance.getState());
        assertEquals(Instant.parse("2018-01-01T00:00:00.00Z").plusSeconds(1L), jobInstance.getScheduleTime());
    }

    @Test
    public void testFailTemporaryJobInstance() throws Exception {
        // GIVEN
        Clock clock = Clock.fixed(Instant.parse("2018-01-01T00:00:00.00Z"), ZoneOffset.UTC);
        // We wait for 3x setScheduleTime invocations. Constructor, setter and via temporary exception handling
        this.jobContext = builder(3).withService(Clock.class, clock)
                .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                    throw new JobTemporaryException(1000L);
                })))
                .createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance() {
            @Override
            public void setScheduleTime(Instant scheduleTime) {
                super.setScheduleTime(scheduleTime);
                latch.countDown();
            }
        };
        jobInstance.setCreationTime(clock.instant());
        jobInstance.setScheduleTime(jobInstance.getCreationTime());

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.NEW, jobInstance.getState());
        assertEquals(Instant.parse("2018-01-01T00:00:00.00Z").plusSeconds(1L), jobInstance.getScheduleTime());
    }

    @Test
    public void testFailJobInstanceTransaction() throws Exception {
        // GIVEN
        CountDownLatch txLatch = new CountDownLatch(1);
        this.jobContext = builder()
                .withTransactionSupport(new TransactionSupport() {
                    @Override
                    public <T> T transactional(JobContext context, long transactionTimeoutMillis, boolean joinIfPossible, Callable<T> callable, Consumer<Throwable> exceptionHandler) {
                        if (txLatch.getCount() == 0) {
                            try {
                                return callable.call();
                            } catch (Exception e) {
                                exceptionHandler.accept(e);
                                return null;
                            }
                        }
                        txLatch.countDown();
                        return null;
                    }

                    @Override
                    public void registerPostCommitListener(Runnable o) {
                        o.run();
                    }
                })
                .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                    sink.add(jobInstance);
                    return null;
                })))
                .createContext();

        // WHEN
        jobContext.getJobManager().addJobInstance(new SimpleJobInstance());

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(1, sink.size());
        assertEquals(0, txLatch.getCount());
    }

    @Test
    public void testDeadline() throws Exception {
        // GIVEN
        this.jobContext = builder().createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.getJobConfiguration().setDeadline(jobInstance.getCreationTime());

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DEADLINE_REACHED, jobInstance.getState());
    }

    @Test
    public void testExecutionTimeFrames() throws Exception {
        // GIVEN
        this.jobContext = builder().createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        TimeFrame timeFrame = new TimeFrame();
        timeFrame.setEndYear(Year.of(2018));
        jobInstance.getJobConfiguration().getExecutionTimeFrames().add(timeFrame);

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DROPPED, jobInstance.getState());
    }

    @Test(timeout = 5000L)
    public void testDefer() throws Exception {
        // GIVEN
        Clock clock = Clock.fixed(Instant.parse("2018-01-01T00:00:00.00Z"), ZoneOffset.UTC);
        this.jobContext = builder().withService(Clock.class, clock).createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance() {
            @Override
            public void markDeferred(JobInstanceProcessingContext<?> jobProcessingContext, Instant newScheduleTime) {
                super.markDeferred(jobProcessingContext, newScheduleTime);
                latch.countDown();
            }
        };
        jobInstance.setCreationTime(clock.instant());
        jobInstance.setScheduleTime(jobInstance.getCreationTime());
        TimeFrame timeFrame = new TimeFrame();
        timeFrame.setStartYear(Year.of(2019));
        jobInstance.getJobConfiguration().setMaximumDeferCount(1);
        jobInstance.getJobConfiguration().getExecutionTimeFrames().add(timeFrame);

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop();
        assertEquals(JobInstanceState.NEW, jobInstance.getState());
        assertEquals(1, jobInstance.getDeferCount());
        assertEquals(Instant.parse("2019-01-01T00:00:00.00Z"), jobInstance.getScheduleTime());
    }

    @Test(timeout = 5000L)
    public void testDeferDrop() throws Exception {
        // GIVEN
        Clock clock = Clock.fixed(Instant.parse("2018-01-01T00:00:00.00Z"), ZoneOffset.UTC);
        this.jobContext = builder().withService(Clock.class, clock).createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setCreationTime(clock.instant());
        jobInstance.setScheduleTime(jobInstance.getCreationTime());
        TimeFrame timeFrame = new TimeFrame();
        timeFrame.setStartYear(Year.of(2019));
        jobInstance.getJobConfiguration().setMaximumDeferCount(0);
        jobInstance.getJobConfiguration().getExecutionTimeFrames().add(timeFrame);

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop();
        assertEquals(JobInstanceState.DROPPED, jobInstance.getState());
        assertEquals(1, jobInstance.getDeferCount());
        assertEquals(Instant.parse("2019-01-01T00:00:00.00Z"), jobInstance.getScheduleTime());
    }

    @Test
    public void testRefreshJobInstanceSchedulesSpecific() throws Exception {
        // GIVEN
        this.jobContext = builder().createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setState(JobInstanceState.DONE);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        jobInstance.setState(JobInstanceState.NEW);
        jobContext.refreshJobInstanceSchedules(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
    }

    @Test
    public void testRefreshJobInstanceSchedulesRescan() throws Exception {
        // GIVEN
        this.jobContext = builder().createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setState(JobInstanceState.DONE);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        jobInstance.setState(JobInstanceState.NEW);
        jobContext.refreshJobInstanceSchedules(0L);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
    }

    @Test
    public void testRefreshJobInstanceSchedulesGeneral() throws Exception {
        // GIVEN
        this.jobContext = builder().createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setState(JobInstanceState.DONE);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        jobInstance.setState(JobInstanceState.NEW);
        jobContext.refreshJobInstanceSchedules(jobInstance.getScheduleTime().toEpochMilli());

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
    }

    @Test
    public void testRefreshJobInstanceSchedulesPartition() throws Exception {
        // GIVEN
        JobContext.Builder builder = builder();
        PartitionKeyProvider partitionKeyProvider = builder.getPartitionKeyProviderFactory().createPartitionKeyProvider(null, null);
        this.jobContext = builder.createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setState(JobInstanceState.DONE);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        jobInstance.setState(JobInstanceState.NEW);
        jobContext.refreshJobInstanceSchedules(partitionKeyProvider.getDefaultJobInstancePartitionKeys().iterator().next(), jobInstance.getScheduleTime().toEpochMilli());

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
    }

    @Test
    public void testChunkingTest() throws Exception {
        // GIVEN
        this.jobContext = builder(2)
                .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                    sink.add(jobInstance);
                    return sink.size() == 1 ? true : null;
                })))
                .createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();

        // WHEN
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
        assertEquals(2, sink.size());
    }

    @Test
    public void testChangeClusterWhileIdle() throws Exception {
        // GIVEN
        MutableClusterStateManager clusterStateManager = new MutableClusterStateManager();
        this.jobContext = builder().withActorContextBuilder(ActorContext.builder().withClusterStateManager(clusterStateManager)).createContext();

        // WHEN
        clusterStateManager.setClusterSize(2);
        clusterStateManager.setClusterPosition(1);
        clusterStateManager.fireClusterStateChanged();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setState(JobInstanceState.NEW);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
    }

    @Test
    public void testChangeClusterWhileIdleWorkStealing() throws Exception {
        // GIVEN
        MutableClusterStateManager clusterStateManager = new MutableClusterStateManager();
        this.jobContext = builder().withActorContextBuilder(ActorContext.builder().withClusterStateManager(clusterStateManager)).createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance();
        jobInstance.setState(JobInstanceState.DONE);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        jobInstance.setState(JobInstanceState.NEW);
        clusterStateManager.setClusterSize(2);
        clusterStateManager.setClusterPosition(1);
        clusterStateManager.fireClusterStateChanged();

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.DONE, jobInstance.getState());
    }

    @Test
    public void testLongRunning() throws Exception {
        // GIVEN
        MutableClusterStateManager clusterStateManager = new MutableClusterStateManager();
        CountDownLatch processorEnterLatch = new CountDownLatch(1);
        CountDownLatch processorLatch = new CountDownLatch(1);
        this.jobContext = builder()
            .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                processorEnterLatch.countDown();
                try {
                    processorLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            })))
            .withActorContextBuilder(ActorContext.builder().withClusterStateManager(clusterStateManager)).createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance(true);
        jobInstance.setState(JobInstanceState.NEW);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        processorEnterLatch.await();
        int clusterPosition = jobContext.getClusterPosition(jobInstance);
        String trace = jobContext.getTrace(jobInstance);
        jobContext.cancel(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.FAILED, jobInstance.getState());
        assertEquals(0, clusterPosition);
        assertTrue("Unexpected trace:\n" + trace, trace.contains("java.util.concurrent.CountDownLatch"));
    }

    @Test
    public void testLongRunningTakeOver() throws Exception {
        // GIVEN
        MutableClusterStateManager clusterStateManager = new MutableClusterStateManager();
        CountDownLatch processorEnterLatch = new CountDownLatch(1);
        CountDownLatch processorLatch = new CountDownLatch(1);
        this.jobContext = builder()
            .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                processorEnterLatch.countDown();
                try {
                    processorLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            })))
            .withActorContextBuilder(ActorContext.builder().withClusterStateManager(clusterStateManager)).createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance(true);
        jobInstance.setState(JobInstanceState.RUNNING);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        clusterStateManager.fireClusterStateChanged();
        processorEnterLatch.await();
        int clusterPosition = jobContext.getClusterPosition(jobInstance);
        String trace = jobContext.getTrace(jobInstance);
        jobContext.cancel(jobInstance);

        // THEN
        await();
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.FAILED, jobInstance.getState());
        assertEquals(0, clusterPosition);
        assertTrue("Unexpected trace:\n" + trace, trace.contains("java.util.concurrent.CountDownLatch"));
    }

    @Test
    public void testLongRunningSkipAlreadyRunning() throws Exception {
        // GIVEN
        MutableClusterStateManager clusterStateManager = new MutableClusterStateManager();
        CountDownLatch processorEnterLatch = new CountDownLatch(1);
        this.jobContext = builder()
            .withJobInstanceProcessorFactory(JobInstanceProcessorFactory.of(((jobInstance, context) -> {
                processorEnterLatch.countDown();
                return null;
            })))
            .withActorContextBuilder(ActorContext.builder().withClusterStateManager(clusterStateManager)).createContext();
        SimpleJobInstance jobInstance = new SimpleJobInstance(true);
        jobInstance.setState(JobInstanceState.RUNNING);
        jobContext.getJobManager().addJobInstance(jobInstance);

        // WHEN
        clusterStateManager.eventFunction = event -> Collections.singletonMap(null, new SimpleFuture<>(new int[]{ 1 }));
        clusterStateManager.fireClusterStateChanged();

        // THEN
        jobContext.stop(1, TimeUnit.MINUTES);
        assertEquals(JobInstanceState.RUNNING, jobInstance.getState());
        assertEquals(1, processorEnterLatch.getCount());
    }

    private static class MutableClusterStateManager implements ClusterStateManager, ClusterNodeInfo, LockService {

        private final List<ClusterStateListener> clusterStateListeners = new CopyOnWriteArrayList<>();
        private final ConcurrentMap<String, Lock> locks = new ConcurrentHashMap<>();
        private final Map<Class<?>, List<Consumer<Serializable>>> listeners = new ConcurrentHashMap<>();
        private Function<StateReturningEvent<?>, Map<ClusterNodeInfo, Future<?>>> eventFunction = e -> Collections.emptyMap();
        private boolean isCoordinator = true;
        private long clusterVersion = 0L;
        private int clusterPosition = 0;
        private int clusterSize = 1;

        public void fireClusterStateChanged() {
            clusterStateListeners.forEach(l -> l.onClusterStateChanged(this));
        }

        @Override
        public ClusterNodeInfo getCurrentNodeInfo() {
            return this;
        }

        @Override
        public void registerListener(ClusterStateListener listener) {
            clusterStateListeners.add(listener);
            listener.onClusterStateChanged(this);
        }

        @Override
        public <T extends Serializable> void registerListener(Class<T> eventClass, java.util.function.Consumer<T> listener) {
            listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add((java.util.function.Consumer<Serializable>) listener);
        }

        @Override
        public Lock getLock(String name) {
            return locks.computeIfAbsent(name, k -> new ReentrantLock());
        }

        @Override
        public LockService getLockService() {
            return this;
        }

        @Override
        public boolean isStandalone() {
            return true;
        }

        @Override
        public void fireEventExcludeSelf(Serializable event, boolean await) {
            // Noop because there is no cluster
        }

        @Override
        public void fireEvent(Serializable event, boolean await) {
            java.util.function.Consumer<Class<?>> consumer = eventClass -> {
                List<java.util.function.Consumer<Serializable>> consumers = listeners.get(eventClass);
                if (consumers != null) {
                    consumers.forEach(c -> c.accept(event));
                }
            };
            Class<?> clazz = event.getClass();
            Set<Class<?>> visitedClasses = new HashSet<>();
            do {
                consumer.accept(clazz);
                visitInterfaces(consumer, clazz, visitedClasses);
                clazz = clazz.getSuperclass();
            } while (clazz != null);
        }

        @Override
        public <T> Map<ClusterNodeInfo, Future<T>> fireEvent(StateReturningEvent<T> event) {
            fireEvent((Serializable) event, false);
            T result = event.getResult();
            return Collections.singletonMap(this, new Future<T>() {
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
                    return true;
                }

                @Override
                public T get() throws InterruptedException, ExecutionException {
                    return result;
                }

                @Override
                public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                    return result;
                }
            });
        }

        @Override
        public <T> Map<ClusterNodeInfo, Future<T>> fireEventExcludeSelf(StateReturningEvent<T> event) {
            return (Map) eventFunction.apply(event);
        }

        public Function<StateReturningEvent<?>, Map<ClusterNodeInfo, Future<?>>> getEventFunction() {
            return eventFunction;
        }

        public void setEventFunction(Function<StateReturningEvent<?>, Map<ClusterNodeInfo, Future<?>>> eventFunction) {
            this.eventFunction = eventFunction;
        }

        private void visitInterfaces(java.util.function.Consumer<Class<?>> consumer, Class<?> clazz, Set<Class<?>> visitedClasses) {
            Class<?>[] interfaces = clazz.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                Class<?> interfaceClass = interfaces[i];
                if (visitedClasses.add(interfaceClass)) {
                    consumer.accept(interfaceClass);
                    visitInterfaces(consumer, interfaceClass, visitedClasses);
                }
            }
        }

        @Override
        public boolean isCoordinator() {
            return isCoordinator;
        }

        public void setCoordinator(boolean coordinator) {
            isCoordinator = coordinator;
        }

        @Override
        public long getClusterVersion() {
            return clusterVersion;
        }

        public void setClusterVersion(long clusterVersion) {
            this.clusterVersion = clusterVersion;
        }

        @Override
        public int getClusterPosition() {
            return clusterPosition;
        }

        public void setClusterPosition(int clusterPosition) {
            this.clusterPosition = clusterPosition;
        }

        @Override
        public int getClusterSize() {
            return clusterSize;
        }

        public void setClusterSize(int clusterSize) {
            this.clusterSize = clusterSize;
        }
    }

    private static class SimpleFuture<T> implements Future<T> {

        private final T result;

        public SimpleFuture(T result) {
            this.result = result;
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
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            return result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return result;
        }
    }
}
