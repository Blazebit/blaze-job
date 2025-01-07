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

import com.blazebit.apt.service.ServiceProvider;
import com.blazebit.job.ConfigurationSource;
import com.blazebit.job.JobInstance;
import com.blazebit.job.JobTrigger;
import com.blazebit.job.PartitionKey;
import com.blazebit.job.spi.PartitionKeyProvider;
import com.blazebit.job.spi.PartitionKeyProviderFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A factory as well as {@link PartitionKeyProvider} implementation providing static partition keys.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@ServiceProvider(PartitionKeyProviderFactory.class)
public class MemoryPartitionKeyProvider implements PartitionKeyProvider, PartitionKeyProviderFactory {

    private static final Collection<PartitionKey> NON_TRIGGER = Collections.singletonList(new PartitionKey() {
        @Override
        public String getName() {
            return "jobInstance";
        }

        @Override
        public boolean matches(JobInstance<?> jobInstance) {
            return !(jobInstance instanceof JobTrigger);
        }

        @Override
        public String toString() {
            return getName();
        }
    });
    private static final Collection<PartitionKey> TRIGGER_ONLY = Collections.singletonList(new PartitionKey() {

        @Override
        public String getName() {
            return "jobTrigger";
        }

        @Override
        public Set<Class<? extends JobInstance<?>>> getJobInstanceTypes() {
            return Collections.singleton(JobTrigger.class);
        }

        @Override
        public boolean matches(JobInstance<?> jobInstance) {
            return jobInstance instanceof JobTrigger;
        }

        @Override
        public String toString() {
            return getName();
        }
    });

    @Override
    public PartitionKeyProvider createPartitionKeyProvider(com.blazebit.job.ServiceProvider serviceProvider, ConfigurationSource configurationSource) {
        return this;
    }

    @Override
    public Collection<PartitionKey> getDefaultTriggerPartitionKeys() {
        return TRIGGER_ONLY;
    }

    @Override
    public Collection<PartitionKey> getDefaultJobInstancePartitionKeys() {
        return NON_TRIGGER;
    }
}
