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

import com.blazebit.persistence.view.IdMapping;

import java.io.Serializable;

/**
 * An entity view holding the id and implementing equals and hashCode based on that.
 *
 * @param <ID> The entity id type or entity view type for the id type
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface IdHolderView<ID> extends Serializable {

    /**
     * Returns the id of the entity.
     *
     * @return the id of the entity
     */
    @IdMapping
    public ID getId();

}
