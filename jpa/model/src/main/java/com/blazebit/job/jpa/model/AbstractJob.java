/*
 * Copyright 2018 - 2022 Blazebit.
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

import com.blazebit.job.Job;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.Instant;

/**
 * An abstract mapped superclass implementing the {@link Job} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
@MappedSuperclass
@Table(name = "job")
public abstract class AbstractJob extends BaseEntity<Long> implements Job {

    private static final long serialVersionUID = 1L;

    private String name;
    private JobConfiguration jobConfiguration = new JobConfiguration();
    /**
     * The time at which the job was created
     */
    private Instant creationTime;

    /**
     * Creates an empty job.
     */
    protected AbstractJob() {
    }

    /**
     * Creates a job with the given id.
     *
     * @param id The job id
     */
    protected AbstractJob(Long id) {
        super(id);
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Sets the given name.
     *
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    @Embedded
    public JobConfiguration getJobConfiguration() {
        return jobConfiguration;
    }

    /**
     * Sets the given job configuration.
     *
     * @param jobConfiguration The job configuration
     */
    public void setJobConfiguration(JobConfiguration jobConfiguration) {
        this.jobConfiguration = jobConfiguration;
    }

    @Override
    @Column(nullable = false)
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Sets the given creation time.
     *
     * @param creationTime The creation time
     */
    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * A {@link PrePersist} method that sets the creation time if necessary.
     */
    @PrePersist
    protected void onPersist() {
        if (this.creationTime == null) {
            this.creationTime = Instant.now();
        }
    }
}
