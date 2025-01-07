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

import java.util.Set;

/**
 * A {@link Set} implementation that delegates all calls.
 *
 * @param <T> The element type
 * @author Christian Beikov
 * @since 1.0.0
 */
public class DelegatingSet<T> extends DelegatingCollection<T> implements Set<T> {

    /**
     * Creates a new delegating set.
     *
     * @param delegate The delegate
     */
    public DelegatingSet(Set<T> delegate) {
        super(delegate);
    }
}
