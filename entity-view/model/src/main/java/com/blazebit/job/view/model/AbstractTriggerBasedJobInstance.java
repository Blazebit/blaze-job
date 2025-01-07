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

package com.blazebit.job.view.model;

import com.blazebit.persistence.view.Mapping;

/**
 * A trigger based abstract entity view implementing the {@link com.blazebit.job.JobInstance} interface.
 *
 * @param <ID> The job instance id type
 * @param <T> The job trigger type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractTriggerBasedJobInstance<ID, T extends AbstractJobTrigger<? extends AbstractJob>> extends AbstractJobInstance<ID> {

    private static final long serialVersionUID = 1L;

    @Override
    @Mapping("trigger.jobConfiguration")
    public abstract JobConfigurationView getJobConfiguration();
}
