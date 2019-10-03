/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.telemetry.glean.private

import androidx.annotation.VisibleForTesting
import android.os.SystemClock
import com.sun.jna.StringArray
import mozilla.telemetry.glean.Glean
import mozilla.telemetry.glean.GleanTimerId
import mozilla.telemetry.glean.rust.getAndConsumeRustString
import mozilla.telemetry.glean.rust.LibGleanFFI
import mozilla.telemetry.glean.rust.toByte
import mozilla.telemetry.glean.Dispatchers
import mozilla.telemetry.glean.rust.toBoolean

/**
 * This implements the developer facing API for recording timing distribution metrics.
 *
 * Instances of this class type are automatically generated by the parsers at build time,
 * allowing developers to record values that were previously registered in the metrics.yaml file.
 */
class TimingDistributionMetricType internal constructor(
    private var handle: Long,
    private val disabled: Boolean,
    private val sendInPings: List<String>
) : HistogramBase {
    /**
     * The public constructor used by automatically generated metrics.
     */
    constructor(
        disabled: Boolean,
        category: String,
        lifetime: Lifetime,
        name: String,
        sendInPings: List<String>,
        timeUnit: TimeUnit = TimeUnit.Minute
    ) : this(handle = 0, disabled = disabled, sendInPings = sendInPings) {
        val ffiPingsList = StringArray(sendInPings.toTypedArray(), "utf-8")
        this.handle = LibGleanFFI.INSTANCE.glean_new_timing_distribution_metric(
            category = category,
            name = name,
            send_in_pings = ffiPingsList,
            send_in_pings_len = sendInPings.size,
            lifetime = lifetime.ordinal,
            disabled = disabled.toByte(),
            time_unit = timeUnit.ordinal
        )
    }

    /**
     * Destroy this metric.
     */
    protected fun finalize() {
        if (this.handle != 0L) {
            LibGleanFFI.INSTANCE.glean_destroy_timing_distribution_metric(this.handle)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun getElapsedTimeNanos(): Long {
        return SystemClock.elapsedRealtimeNanos()
    }

    /**
     * Start tracking time for the provided metric and [GleanTimerId]. This
     * records an error if it’s already tracking time (i.e. start was already
     * called with no corresponding [stopAndAccumulate]): in that case the original
     * start time will be preserved.
     *
     * @return The [GleanTimerId] object to associate with this timing.
     */
    fun start(): GleanTimerId? {
        if (disabled) {
            return null
        }

        // The Rust code for [stopAndAccumulate] runs async and we need to use the same clock for start and stop.
        // Therefore we take the time on the Kotlin side.
        val startTime = getElapsedTimeNanos()

        // No dispatcher, we need the return value
        return LibGleanFFI.INSTANCE.glean_timing_distribution_set_start(
                Glean.handle,
                this@TimingDistributionMetricType.handle,
                startTime)
    }

    /**
     * Stop tracking time for the provided metric and associated timer id. Add a
     * count to the corresponding bucket in the timing distribution.
     * This will record an error if no [start] was called.
     *
     * @param timerId The [GleanTimerId] to associate with this timing.  This allows
     * for concurrent timing of events associated with different ids to the
     * same timespan metric.
     */
    fun stopAndAccumulate(timerId: GleanTimerId?) {
        // [start] might return null.
        // Accepting that means users of this API don't need to do a null check.
        if (disabled || timerId == null) {
            return
        }

        // The Rust code runs async and might be delayed. We need the time as precisely as possible.
        // We also need the same clock for start and stop ([start] takes the time on the Kotlin side).
        val stopTime = getElapsedTimeNanos()

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timing_distribution_set_stop_and_accumulate(
                    Glean.handle,
                    this@TimingDistributionMetricType.handle,
                    timerId,
                    stopTime)
        }
    }

    /**
     * Abort a previous [start] call. No error is recorded if no [start] was called.
     *
     * @param timerId The [GleanTimerId] to associate with this timing. This allows
     * for concurrent timing of events associated with different ids to the
     * same timing distribution metric.
     */
    fun cancel(timerId: GleanTimerId?) {
        if (disabled || timerId == null) {
            return
        }

        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timing_distribution_cancel(this@TimingDistributionMetricType.handle, timerId)
        }
    }

    override fun accumulateSamples(samples: LongArray) {
        if (disabled) {
            return
        }

        // The reason we're using [Long](s) instead of [UInt](s) in Kotlin-land is
        // the lack of [UInt] (in stable form). The positive part of [Int] would not
        // be enough to represent the values coming in:
        // - [UInt.MAX_VALUE] is 4294967295
        // - [Int.MAX_VALUE] is 2147483647
        // - [Long.MAX_VALUE] is 9223372036854775807
        //
        // On the rust side, Long(s) are handled as i64 and then casted to u64.
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.launch {
            LibGleanFFI.INSTANCE.glean_timing_distribution_accumulate_samples(
                Glean.handle,
                this@TimingDistributionMetricType.handle,
                samples,
                samples.size
            )
        }
    }

    /**
     * Tests whether a value is stored for the metric for testing purposes only. This function will
     * attempt to await the last task (if any) writing to the the metric's storage engine before
     * returning a value.
     *
     * @param pingName represents the name of the ping to retrieve the metric for.
     *                 Defaults to the first value in `sendInPings`.
     * @return true if metric value exists, otherwise false
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmOverloads
    fun testHasValue(pingName: String = sendInPings.first()): Boolean {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.assertInTestingMode()

        return LibGleanFFI
            .INSTANCE.glean_timing_distribution_test_has_value(Glean.handle, this.handle, pingName)
            .toBoolean()
    }

    /**
     * Returns the stored value for testing purposes only. This function will attempt to await the
     * last task (if any) writing to the the metric's storage engine before returning a value.
     *
     * @param pingName represents the name of the ping to retrieve the metric for.
     *                 Defaults to the first value in `sendInPings`.
     * @return value of the stored metric
     * @throws [NullPointerException] if no value is stored
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmOverloads
    fun testGetValue(pingName: String = sendInPings.first()): DistributionData {
        @Suppress("EXPERIMENTAL_API_USAGE")
        Dispatchers.API.assertInTestingMode()

        if (!testHasValue(pingName)) {
            throw NullPointerException()
        }

        val ptr = LibGleanFFI.INSTANCE.glean_timing_distribution_test_get_value_as_json_string(
                Glean.handle,
                this.handle,
                pingName)!!

        return DistributionData.fromJsonString(ptr.getAndConsumeRustString())!!
    }
}
