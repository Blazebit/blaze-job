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

package com.blazebit.job.jpa.model;

import com.blazebit.job.JobConfiguration;
import com.blazebit.job.JobTrigger;

/**
 * A JPA job instance that was created through a job trigger.
 *
 * @param <ID> The job instance id type
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface JpaTriggerBasedJobInstance<ID> extends JpaJobInstance<ID> {

    @Override
    default JobConfiguration getJobConfiguration() {
        return getTrigger().getJobConfiguration();
    }

    /**
     * Returns the trigger that created this job instance.
     *
     * @return the trigger that created this job instance
     */
    JobTrigger getTrigger();

    /**
     * Sets the trigger that created this job instance.
     *
     * @param jobTrigger The trigger
     */
    void setTrigger(JobTrigger jobTrigger);

}
