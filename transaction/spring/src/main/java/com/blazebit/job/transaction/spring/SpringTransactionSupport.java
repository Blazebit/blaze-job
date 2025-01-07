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
package com.blazebit.job.transaction.spring;

import com.blazebit.job.JobContext;
import com.blazebit.job.spi.TransactionSupport;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A Spring-based implementation for {@link TransactionSupport}.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class SpringTransactionSupport implements TransactionSupport {

    private final ThreadLocal<TransactionStack> transactionStackThreadLocal = new ThreadLocal<>();
    private final PlatformTransactionManager tm;

    /**
     * Creates a new transaction support for the given transaction manager.
     *
     * @param tm The transaction manager
     */
    public SpringTransactionSupport(PlatformTransactionManager tm) {
        this.tm = tm;
    }

    @Override
    public <T> T transactional(JobContext context, long transactionTimeoutMillis, boolean joinIfPossible, Callable<T> callable, Consumer<Throwable> exceptionHandler) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(tm);
        transactionTemplate.setName(SpringTransactionSupport.class.getName());
        transactionTemplate.setTimeout((int) TimeUnit.MILLISECONDS.toSeconds(transactionTimeoutMillis));
        TransactionStack transactionStack = transactionStackThreadLocal.get();
        boolean root = false;
        if (transactionStack == null) {
            transactionStack = new TransactionStack();
            root = true;
            transactionStackThreadLocal.set(transactionStack);
        }
        if (joinIfPossible && allOk(transactionStack.transactionDefinitions)) {
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        } else {
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        int index = transactionStack.transactionDefinitions.size();
        transactionStack.transactionDefinitions.add(transactionTemplate);
        try {
            return transactionTemplate.execute(status -> {
                try {
                    return callable.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new ThrowableWrapper(t);
                }
            });
        } catch (Throwable t) {
            if (t instanceof ThrowableWrapper) {
                exceptionHandler.accept(t.getCause());
            } else {
                exceptionHandler.accept(t);
            }
            return null;
        } finally {
            if (root) {
                transactionStackThreadLocal.remove();
            } else {
                transactionStack.transactionDefinitions.remove(index);
            }
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.0.0
     */
    private static class ThrowableWrapper extends RuntimeException {
        private ThrowableWrapper(Throwable cause) {
            super(cause);
        }
    }

    /**
     * @author Christian Beikov
     * @since 1.0.0
     */
    private static class TransactionStack {
        private final List<TransactionDefinition> transactionDefinitions = new ArrayList<>();
    }

    private boolean allOk(List<TransactionDefinition> transactionDefinitions) {
        for (int i = 0; i < transactionDefinitions.size(); i++) {
            TransactionDefinition transactionDefinition = transactionDefinitions.get(i);
            // We need to be careful how we invoke Spring's PlatformTransactionManager here. Depending on the
            // propagation behavior of the passed TransactionDefinition, the TM may suspend an ongoing transaction
            // and start a new one which is not what we want here. In order to prevent that we must only pass
            // TransactionDefinition with propagation behavior PROPAGATION_REQUIRED as this won't suspend
            // existing transaction or start new ones.
            if (transactionDefinition.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRED) {
                TransactionTemplate transactionTemplate = new TransactionTemplate(tm, transactionDefinition);
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
                transactionDefinition = transactionTemplate;
            }
            if (tm.getTransaction(transactionDefinition).isRollbackOnly()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void registerPostCommitListener(Runnable o) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                o.run();
            }
        });
    }
}
