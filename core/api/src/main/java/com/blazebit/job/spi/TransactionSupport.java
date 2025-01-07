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
package com.blazebit.job.spi;

import com.blazebit.job.JobContext;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Interface implemented by the job implementation provider.
 *
 * Implementations are instantiated via {@link java.util.ServiceLoader}.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface TransactionSupport {

    /**
     * The no-op transaction support that has no transactional support. This is the default.
     */
    TransactionSupport NOOP = new TransactionSupport() {
        @Override
        public <T> T transactional(JobContext context, long transactionTimeoutMillis, boolean joinIfPossible, Callable<T> callable, Consumer<Throwable> exceptionHandler) {
            try {
                return callable.call();
            } catch (Throwable t) {
                exceptionHandler.accept(t);
                return null;
            }
        }

        @Override
        public void registerPostCommitListener(Runnable o) {
            o.run();
        }
    };

    /**
     * Runs the given callable in a transaction with the given transaction timeout.
     * If desired, will join an already running transaction, otherwise, will suspend an already running transaction and start a new one.
     * If an exception happens in the callable, the exception handler is invoked.
     *
     * @param context The job context
     * @param transactionTimeoutMillis The transaction timeout
     * @param joinIfPossible Whether to join an existing transaction or create a new one
     * @param callable The callable to execute within the transaction
     * @param exceptionHandler The exception handler to invoke if an exception occurs
     * @param <T> The result type of the callable
     * @return the result of the callable
     */
    <T> T transactional(JobContext context, long transactionTimeoutMillis, boolean joinIfPossible, Callable<T> callable, Consumer<Throwable> exceptionHandler);

    /**
     * Registers the given runnable to run after a commit.
     *
     * @param o The runnable to run after a commit
     */
    void registerPostCommitListener(Runnable o);
}
