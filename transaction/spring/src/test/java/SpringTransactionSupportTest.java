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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import com.blazebit.job.JobContext;
import com.blazebit.job.transaction.spring.SpringTransactionSupport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;

@RunWith(MockitoJUnitRunner.class)
public class SpringTransactionSupportTest {
    private static final long DEFAULT_TX_TIMEOUT = 10_000L;

    @Mock
    private PlatformTransactionManager tm;
    private SpringTransactionSupport springTransactionSupport;

    @Before
    public void setup() {
        springTransactionSupport = new SpringTransactionSupport(tm);
    }

    @Test
    public void transactional_checkExistingTransactionStatusWithoutCreatingNewTransaction() {
        // Given
        JobContext jobContext = Mockito.mock(JobContext.class);
        when(tm.getTransaction(any(TransactionDefinition.class))).thenAnswer(a -> new DefaultTransactionStatus(null, false, false, false, false, null));

        // When
        springTransactionSupport.transactional(jobContext, DEFAULT_TX_TIMEOUT, false, () -> {
            springTransactionSupport.transactional(jobContext, DEFAULT_TX_TIMEOUT, true, () -> null, (t) -> {});
            return null;
        }, (t) -> {});

        // Then
        Mockito.verify(tm, VerificationModeFactory.times(1)).getTransaction(argThat(new PropagationBehaviorArgumentMatcher(TransactionDefinition.PROPAGATION_REQUIRES_NEW)));
        Mockito.verify(tm, VerificationModeFactory.times(2)).getTransaction(argThat(new PropagationBehaviorArgumentMatcher(TransactionDefinition.PROPAGATION_REQUIRED)));
        Mockito.verify(tm, VerificationModeFactory.times(2)).commit(any(TransactionStatus.class));
        Mockito.verifyNoMoreInteractions(tm);
    }

    private static class PropagationBehaviorArgumentMatcher implements ArgumentMatcher<TransactionDefinition> {

        private final int propagationBehavior;

        private PropagationBehaviorArgumentMatcher(int propagationBehavior) {
            this.propagationBehavior = propagationBehavior;
        }

        @Override
        public boolean matches(TransactionDefinition transactionDefinition) {
            return propagationBehavior == transactionDefinition.getPropagationBehavior();
        }
    }
}
