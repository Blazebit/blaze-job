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
 * An exception thrown by jobs or the job runtime to signal that a rate limit happened.
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public class JobRateLimitException extends JobTemporaryException {

    /**
     * Creates a new exception.
     */
    public JobRateLimitException() {
    }

    /**
     * Creates a new exception.
     *
     * @param message The message
     */
    public JobRateLimitException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     *
     * @param cause The cause
     * @param message The message
     */
    public JobRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception.
     *
     * @param cause The cause
     */
    public JobRateLimitException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new exception.
     *
     * @param cause The cause
     * @param message The message
     * @param enableSuppression Whether to enable suppression
     * @param writableStackTrace Whether to create a writable stacktrace
     */
    public JobRateLimitException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Creates a new exception with the given defer time.
     *
     * @param deferMillis The minimum time to wait until to attempt the next execution
     */
    public JobRateLimitException(long deferMillis) {
        super(deferMillis);
    }

    /**
     * Creates a new exception with the given defer time.
     *
     * @param message The message
     * @param deferMillis The minimum time to wait until to attempt the next execution
     */
    public JobRateLimitException(String message, long deferMillis) {
        super(message, deferMillis);
    }

    /**
     * Creates a new exception with the given defer time.
     *
     * @param cause The cause
     * @param message The message
     * @param deferMillis The minimum time to wait until to attempt the next execution
     */
    public JobRateLimitException(String message, Throwable cause, long deferMillis) {
        super(message, cause, deferMillis);
    }

    /**
     * Creates a new exception with the given defer time.
     *
     * @param cause The cause
     * @param deferMillis The minimum time to wait until to attempt the next execution
     */
    public JobRateLimitException(Throwable cause, long deferMillis) {
        super(cause, deferMillis);
    }

    /**
     * Creates a new exception with the given defer time.
     *
     * @param cause The cause
     * @param message The message
     * @param enableSuppression Whether to enable suppression
     * @param writableStackTrace Whether to create a writable stacktrace
     * @param deferMillis The minimum time to wait until to attempt the next execution
     */
    public JobRateLimitException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, long deferMillis) {
        super(message, cause, enableSuppression, writableStackTrace, deferMillis);
    }
}
