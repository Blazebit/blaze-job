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

package com.blazebit.job.impl;

import com.blazebit.actor.spi.StateReturningEvent;

import java.io.Serializable;

/**
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JobSchedulerStatusEvent implements StateReturningEvent<int[]> {

    private final Serializable[] jobInstanceIds;
    private int[] clusterPositions;

    public JobSchedulerStatusEvent(Serializable[] jobInstanceIds) {
        this.jobInstanceIds = jobInstanceIds;
    }

    public Serializable[] getJobInstanceIds() {
        return jobInstanceIds;
    }

    @Override
    public int[] getResult() {
        return clusterPositions;
    }

    public void setClusterPositions(int[] clusterPositions) {
        this.clusterPositions = clusterPositions;
    }
}
