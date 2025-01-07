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

import com.blazebit.job.Job;
import com.blazebit.persistence.view.PrePersist;

import java.time.Instant;

/**
 * An abstract entity view implementing the {@link Job} interface.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractJob implements Job, IdHolderView<Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Sets the given name.
     *
     * @param name The name
     */
    public abstract void setName(String name);

    /**
     * Sets the given creation time.
     *
     * @param creationTime The creation time
     */
    public abstract void setCreationTime(Instant creationTime);

    /**
     * A {@link PrePersist} method that sets the creation time if necessary.
     */
    @PrePersist
    protected void onPersist() {
        if (getCreationTime() == null) {
            setCreationTime(Instant.now());
        }
    }
}
