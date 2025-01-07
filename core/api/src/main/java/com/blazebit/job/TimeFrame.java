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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * An abstraction for describing a time frame.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public interface TimeFrame {

    /**
     * Returns the earliest instant after the given instant that is part of any of the given time frames.
     *
     * @param timeFrames The time frames
     * @param time       The instant
     * @return the earliest instant
     */
    static Instant getNearestTimeFrameSchedule(Set<? extends TimeFrame> timeFrames, Instant time) {
        if (timeFrames == null || timeFrames.isEmpty()) {
            return time;
        }

        Instant earliestInstant = Instant.MAX;
        for (TimeFrame timeFrame : timeFrames) {
            Instant instant = timeFrame.getEarliestInstant(time);
            if (instant != null) {
                if (instant.equals(time)) {
                    // Special case. When we find a time frame that contains the given time, we just return that time
                    return time;
                }
                earliestInstant = earliestInstant.isBefore(instant) ? earliestInstant : instant;
            }
        }

        return earliestInstant;
    }

    /**
     * Returns whether the given instant is contained in any of the time frames.
     *
     * @param timeFrames The time frames
     * @param time       The instant
     * @return whether the given instant is contained in any of the time frames
     */
    static boolean isContained(Set<? extends TimeFrame> timeFrames, Instant time) {
        if (timeFrames != null) {
            for (TimeFrame timeFrame : timeFrames) {
                if (!timeFrame.contains(time)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns the year at which this time frame starts or <code>null</code> if it has no defined start.
     *
     * @return the year at which this time frame starts or <code>null</code>
     */
    public Year getStartYear();

    /**
     * Returns the year at which this time frame ends or <code>null</code> if it has no defined end.
     *
     * @return the year at which this time frame ends or <code>null</code>
     */
    public Year getEndYear();

    /**
     * Returns the month at which this time frame starts or <code>null</code> if it has no defined start.
     *
     * @return the month at which this time frame starts or <code>null</code>
     */
    public Month getStartMonth();

    /**
     * Returns the month at which this time frame ends or <code>null</code> if it has no defined end.
     *
     * @return the month at which this time frame ends or <code>null</code>
     */
    public Month getEndMonth();

    /**
     * Returns the day of week which is allowed for this time frame <code>null</code> if it has no defined day of week.
     *
     * @return the day of week which is allowed for this time frame <code>null</code>
     */
    public DayOfWeek getWeekDay();

    /**
     * Returns the time at which this time frame starts or <code>null</code> if it has no defined start.
     *
     * @return the time at which this time frame starts or <code>null</code>
     */
    public LocalTime getStartTime();

    /**
     * Returns the time at which this time frame ends or <code>null</code> if it has no defined end.
     *
     * @return the time at which this time frame ends or <code>null</code>
     */
    public LocalTime getEndTime();

    /**
     * Returns whether the given instant is contained in this time frame.
     *
     * @param time The instant
     * @return whether the given instant is contained in this time frame
     */
    default boolean contains(Instant time) {
        OffsetDateTime offsetDateTime = time.atOffset(ZoneOffset.UTC);
        if (getStartYear() != null || getEndYear() != null) {
            int year = offsetDateTime.getYear();
            if (getStartYear() != null && year < getStartYear().getValue() || getEndYear() != null && year > getEndYear().getValue()) {
                return false;
            }
        }

        if (getStartMonth() != null || getEndMonth() != null) {
            int month = offsetDateTime.getMonthValue();
            if (getStartMonth() != null && month < getStartMonth().getValue() || getEndMonth() != null && month > getEndMonth().getValue()) {
                return false;
            }
        }

        if (getWeekDay() != null && !getWeekDay().equals(offsetDateTime.getDayOfWeek())) {
            return false;
        }

        if (getStartTime() != null || getEndTime() != null) {
            LocalTime localTime = offsetDateTime.toLocalTime();
            if (getStartTime() != null && localTime.isBefore(getStartTime()) || getEndTime() != null && localTime.isAfter(getEndTime())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns {@link #getEarliestInstant(Instant)} with {@link Instant#now()}.
     *
     * @return the earliest instant
     */
    default Instant getEarliestInstant() {
        return getEarliestInstant(Instant.now());
    }

    /**
     * Returns the earliest instant after the given instant that is part of this time frame.
     *
     * @param fromInstant The reference instant
     * @return the earliest instant
     */
    default Instant getEarliestInstant(Instant fromInstant) {
        OffsetDateTime offsetDateTime = fromInstant.atOffset(ZoneOffset.UTC);
        if (getStartTime() != null) {
            if (getStartTime().isAfter(offsetDateTime.toLocalTime())) {
                // Start time is in the future, so let's use that
                offsetDateTime = offsetDateTime.with(getStartTime());
            } else if (getEndTime() == null || getEndTime().isBefore(offsetDateTime.toLocalTime())) {
                // End time is in the future, so the current time is ok
            } else {
                // End time is in the past, so we need to set start time and adjust the day
                offsetDateTime = offsetDateTime.withDayOfMonth(offsetDateTime.getDayOfMonth() + 1).with(getStartTime());
            }
        }

        if (getStartYear() == null) {
            // No start year means we can start at any time
            offsetDateTime = TimeFrameUtils.adjustWeekDay(offsetDateTime, getWeekDay());
            offsetDateTime = TimeFrameUtils.adjustMonth(offsetDateTime, getEndYear(), getStartMonth(), getEndMonth(), getWeekDay());
        } else {
            if (getStartYear().getValue() > offsetDateTime.getYear()) {
                // Start year is in the future, so let's use that
                offsetDateTime = offsetDateTime.withYear(getStartYear().getValue());
                offsetDateTime = TimeFrameUtils.adjustWeekDay(offsetDateTime, getWeekDay());
                offsetDateTime = TimeFrameUtils.adjustMonth(offsetDateTime, getEndYear(), getStartMonth(), getEndMonth(), getWeekDay());
            } else if (getEndYear() == null || getEndYear().getValue() <= offsetDateTime.getYear()) {
                // End year is in the future, so the current year is ok
                return offsetDateTime.toInstant();
            } else {
                // End year is in the past, so we are done here as the constraints aren't satisfiable
                throw new IllegalStateException("Unsatisfiable constraints!");
            }
        }

        if (offsetDateTime == null) {
            return null;
        }
        return offsetDateTime.toInstant();
    }
}
