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

package com.blazebit.job;

/**
 * The job instance states.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public enum JobInstanceState {

    /**
     * A new, yet to be executed job instance.
     */
    NEW,
    /**
     * A done job instance that doesn't need further processing.
     */
    DONE,
    /**
     * A failed job instance that doesn't need further processing.
     */
    FAILED,
    /**
     * A job instance that reached its deadline and doesn't need further processing.
     */
    DEADLINE_REACHED,
    /**
     * A job instance that reached its maximum defer count and doesn't need further processing.
     */
    DROPPED,
    /**
     * A job instance with that state is going to be remove during an update.
     */
    REMOVED,
    /**
     * A job instance with that state is a long running job that is currently running.
     */
    RUNNING;

}
