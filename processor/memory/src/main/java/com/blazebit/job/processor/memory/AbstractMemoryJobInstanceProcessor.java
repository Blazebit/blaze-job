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
package com.blazebit.job.processor.memory;

import com.blazebit.job.JobContext;
import com.blazebit.job.JobException;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobInstanceProcessingContext;
import com.blazebit.job.JobInstanceProcessor;

import java.util.concurrent.BlockingQueue;
import java.util.function.BiConsumer;

/**
 * An abstract job instance processor implementation that writes into a sink.
 *
 * @param <ID> The job instance cursor type
 * @param <T> The result type of the processing
 * @param <I> The job instance type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractMemoryJobInstanceProcessor<ID, T, I extends JobInstance<?>> implements JobInstanceProcessor<ID, I> {

    protected final BiConsumer<JobContext, T> sink;

    /**
     * Creates a new job instance processor that publishes results to the given sink.
     *
     * @param sink The sink to publish results to
     */
    protected AbstractMemoryJobInstanceProcessor(BlockingQueue<T> sink) {
        this(new BiConsumer<JobContext, T>() {
            @Override
            public void accept(JobContext context, T object) {
                try {
                    sink.put(object);
                } catch (InterruptedException e) {
                    throw new JobException(e);
                }
            }
        });
    }

    /**
     * Creates a new job instance processor that publishes results to the given sink.
     *
     * @param sink The sink to publish results to
     */
    protected AbstractMemoryJobInstanceProcessor(BiConsumer<JobContext, T> sink) {
        this.sink = sink;
    }

    @Override
    public ID process(I jobInstance, JobInstanceProcessingContext<ID> context) {
        JobContext jobContext = context.getJobContext();
        ID lastJobResultProcessed = context.getLastProcessed();
        for (int i = 0; i < context.getProcessCount(); i++) {
            T processingResult = processSingle(jobInstance, context, lastJobResultProcessed);
            if (processingResult == null) {
                break;
            }
            sink.accept(jobContext, processingResult);
            lastJobResultProcessed = getProcessingResultId(processingResult);
        }

        if (lastJobResultProcessed == context.getLastProcessed()) {
            lastJobResultProcessed = null;
        }

        return lastJobResultProcessed;
    }

    /**
     * Processes a single increment of the given job instance and returns the processing result.
     * Returns <code>null</code> if nothing can be processed.
     *
     * @param jobInstance The job instance
     * @param context The job instance processing context
     * @param lastJobResultProcessed The identifier of the result of the last increment processing
     * @return The job result
     */
    protected abstract T processSingle(I jobInstance, JobInstanceProcessingContext<ID> context, ID lastJobResultProcessed);

    /**
     * Returns the identifier of the processing result.
     *
     * @param processingResult The processing result
     * @return the identifier
     */
    protected ID getProcessingResultId(T processingResult) {
        return null;
    }

}
