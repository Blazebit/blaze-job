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
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;

/**
 * A utility class for implementing time frame methods.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
final class TimeFrameUtils {

    private TimeFrameUtils() {
    }

    /**
     * Adds days to the given offset date time to satisfy the week day constraint.
     *
     * @param offsetDateTime The offset date time
     * @param weekDay        The week day that the offset date time should have or <code>null</code>
     * @return an offset date time at the given week day
     */
    static OffsetDateTime adjustWeekDay(OffsetDateTime offsetDateTime, DayOfWeek weekDay) {
        if (weekDay != null) {
            int weekDayDifference = offsetDateTime.getDayOfWeek().getValue() - weekDay.getValue();

            if (weekDayDifference < 0) {
                // The current weekday is before the target weekday, so just shift days
                offsetDateTime = offsetDateTime.plusDays(Math.abs(weekDayDifference));
            } else if (weekDayDifference > 0) {
                // The current weekday is after the target weekday, so shift days to the next week
                offsetDateTime = offsetDateTime.plusDays(7 - weekDayDifference);
            }
        }
        return offsetDateTime;
    }

    /**
     * Adds days, months and years to the given offset date time to satisfy the month and week day constraints.
     * If an end year is given and the constraints can't be satisfied for that year, <code>null</code> is returned to signal that the constraints are unsatisfiable.
     *
     * @param offsetDateTime The offset date time
     * @param endYear        The optional end year
     * @param startMonth     The lower bound for the month the offset date time may have or <code>null</code>
     * @param endMonth       The upper bound for the month the offset date time may have or <code>null</code>
     * @param weekDay        The week day that the offset date time should have or <code>null</code>
     * @return an offset date time within the given month range and at the given week day or <code>null</code>
     */
    static OffsetDateTime adjustMonth(OffsetDateTime offsetDateTime, Year endYear, Month startMonth, Month endMonth, DayOfWeek weekDay) {
        if (startMonth == null) {
            if (endMonth != null) {
                throw new IllegalArgumentException("Either both, start and end month must be given, or none!");
            }
            offsetDateTime = TimeFrameUtils.adjustWeekDay(offsetDateTime, weekDay);
            if (endYear != null && offsetDateTime.getYear() > endYear.getValue()) {
                // Unsatisfiable
                return null;
            }
        } else {
            OffsetDateTime lastOffsetDateTime;
            do {
                lastOffsetDateTime = offsetDateTime;
                if (startMonth.getValue() > offsetDateTime.getMonthValue()) {
                    // Start month is in the future, so let's use that
                    offsetDateTime = offsetDateTime.withMonth(startMonth.getValue()).withDayOfMonth(1);
                    offsetDateTime = TimeFrameUtils.adjustWeekDay(offsetDateTime, weekDay);
                } else if (endMonth == null || endMonth.getValue() <= offsetDateTime.getMonthValue()) {
                    // End month is in the future, so the current month is ok
                    offsetDateTime = TimeFrameUtils.adjustWeekDay(offsetDateTime, weekDay);
                } else {
                    // End month is in the past, so we need to set start month and adjust the day
                    offsetDateTime = offsetDateTime.withYear(offsetDateTime.getYear() + 1).withMonth(startMonth.getValue()).withDayOfMonth(1);
                    offsetDateTime = TimeFrameUtils.adjustWeekDay(offsetDateTime, weekDay);
                }
                if (endYear != null && offsetDateTime.getYear() > endYear.getValue()) {
                    // Unsatisfiable
                    return null;
                }
                // We stop adjusting when the date time stops changing and fits the constraints
            } while (offsetDateTime != lastOffsetDateTime);
        }
        return offsetDateTime;
    }
}
