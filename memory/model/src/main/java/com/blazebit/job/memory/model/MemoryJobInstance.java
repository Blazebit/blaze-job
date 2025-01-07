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

package com.blazebit.job.memory.model;

import com.blazebit.job.JobInstance;

/**
 * A memory job instance with a getter and setter for a version field.
 *
 * @param <ID> The job instance id type
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface MemoryJobInstance<ID> extends JobInstance<ID> {

    /**
     * Sets the given id.
     *
     * @param id The id
     */
    void setId(ID id);

    /**
     * Returns the version.
     *
     * @return the version
     */
    long getVersion();

    /**
     * Sets the given version.
     *
     * @param version The version
     */
    void setVersion(long version);

}
