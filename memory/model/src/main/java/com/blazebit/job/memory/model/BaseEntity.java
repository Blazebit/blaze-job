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

import java.io.Serializable;

/**
 * An abstract implementation holding the id and implementing equals and hashCode based on that.
 *
 * @param <ID> The entity id type
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class BaseEntity<ID> implements Serializable {
    private static final long serialVersionUID = 1L;

    private ID id;

    /**
     * Creates an empty base entity.
     */
    protected BaseEntity() {
    }

    /**
     * Creates a base entity with the given id.
     *
     * @param id The base entity id
     */
    protected BaseEntity(ID id) {
        this.id = id;
    }

    /**
     * Returns the id of the entity.
     *
     * @return the id of the entity
     */
    public ID getId() {
        return id;
    }

    /**
     * Sets the id of the entity.
     *
     * @param id The id
     */
    public void setId(ID id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BaseEntity<?> other = (BaseEntity<?>) obj;
        // null does not equal null in case of ids!
        if (id == null || other.id == null) {
            return false;
        }
        return id.equals(other.id);
    }
}
