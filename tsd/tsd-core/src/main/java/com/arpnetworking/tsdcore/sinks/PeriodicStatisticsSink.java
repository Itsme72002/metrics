/**
 * Copyright 2014 Brandon Arp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.tsdcore.sinks;

import com.arpnetworking.logback.annotations.LogValue;
import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.metrics.Unit;
import com.arpnetworking.steno.LogValueMapFactory;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.arpnetworking.tsdcore.model.AggregatedData;
import com.arpnetworking.tsdcore.model.Condition;
import com.fasterxml.jackson.annotation.JacksonInject;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.sf.oval.constraint.Min;
import net.sf.oval.constraint.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAccumulator;

/**
 * Aggregates and periodically logs metrics about the aggregated data being
 * record; effectively, this is metrics about metrics. It's primary purpose is
 * to provide a quick sanity check on installations by generating metrics that
 * the aggregator can then consume (and use to generate more metrics). This
 * class is thread safe.
 *
 * TODO(vkoskela): Remove synchronized blocks [MAI-110]
 *
 * Details: The synchronization can be removed if the metrics client can
 * be configured to throw ISE when attempting to write to a closed instance.
 * This would allow a retry on the new instance; starvation would theoretically
 * be possible but practically should never happen.
 *
 * (+) The implementation of _age as an AtomicLong currently relies on the
 * locking provided by the synchronized block to perform it's check and set.
 * This can be replaced with a separate lock or a thread-safe accumulator
 * implementation.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public final class PeriodicStatisticsSink extends BaseSink {

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordAggregateData(final Collection<AggregatedData> data, final Collection<Condition> conditions) {
        LOGGER.debug()
                .setMessage("Writing aggregated data")
                .addData("sink", getName())
                .addData("dataSize", data.size())
                .addData("conditionsSize", conditions.size())
                .log();

        final long now = System.currentTimeMillis();
        _aggregatedData.addAndGet(data.size());
        for (final AggregatedData datum : data) {
            final String fqsn = new StringBuilder()
                    .append(datum.getFQDSN().getCluster()).append(".")
                    .append(datum.getHost()).append(".")
                    .append(datum.getFQDSN().getService()).append(".")
                    .append(datum.getFQDSN().getMetric()).append(".")
                    .append(datum.getFQDSN().getStatistic()).append(".")
                    .append(datum.getPeriod())
                    .toString();

            final String metricName = new StringBuilder()
                    .append(datum.getFQDSN().getService()).append(".")
                    .append(datum.getFQDSN().getMetric())
                    .toString();

            _uniqueMetrics.get().add(metricName);

            _uniqueStatistics.get().add(fqsn);

            _age.accumulate(now - datum.getPeriodStart().plus(datum.getPeriod()).getMillis());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            _executor.shutdown();
            _executor.awaitTermination(EXECUTOR_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.interrupted();
            throw Throwables.propagate(e);
        }
        flushMetrics(_metrics.get());
    }

    /**
     * Generate a Steno log compatible representation.
     *
     * @return Steno log compatible representation.
     */
    @LogValue
    @Override
    public Object toLogValue() {
        return LogValueMapFactory.of(
                "super", super.toLogValue(),
                "AggregatedData", _aggregatedData,
                "UniqueMetrics", _uniqueMetrics.get().size(),
                "UniqueStatistics", _uniqueStatistics.get().size());
    }

    private void flushMetrics(final Metrics metrics) {
        // Gather and reset state
        final Set<String> oldUniqueMetrics = _uniqueMetrics.getAndSet(
                createConcurrentSet(_uniqueMetrics.get()));
        final Set<String> oldUniqueStatistics = _uniqueStatistics.getAndSet(
                createConcurrentSet(_uniqueStatistics.get()));

        // Record statistics and close
        metrics.incrementCounter(_aggregatedDataName, _aggregatedData.getAndSet(0));
        metrics.incrementCounter(_uniqueMetricsName, oldUniqueMetrics.size());
        metrics.incrementCounter(_uniqueStatisticsName, oldUniqueStatistics.size());
        metrics.setGauge(_ageName, _age.getThenReset(), Unit.fromTimeUnit(TimeUnit.MILLISECONDS));
        metrics.close();
    }

    private Metrics createMetrics() {
        final Metrics metrics = _metricsFactory.create();
        metrics.resetCounter(_aggregatedDataName);
        metrics.resetCounter(_uniqueMetricsName);
        metrics.resetCounter(_uniqueStatisticsName);
        return metrics;
    }

    private Set<String> createConcurrentSet(final Set<String> existingSet) {
        final int initialCapacity = (int) (existingSet.size() / 0.75);
        return Sets.newSetFromMap(new ConcurrentHashMap<>(initialCapacity));
    }

    // NOTE: Package private for testing
    /* package private */PeriodicStatisticsSink(final Builder builder, final ScheduledExecutorService executor) {
        super(builder);

        // Initialize the metrics factory and metrics instance
        _metricsFactory = builder._metricsFactory;
        _aggregatedDataName = "Sinks/PeriodicStatisticsSink/" + getMetricSafeName() + "/AggregatedData";
        _uniqueMetricsName = "Sinks/PeriodicStatisticsSink/" + getMetricSafeName() + "/UniqueMetrics";
        _uniqueStatisticsName = "Sinks/PeriodicStatisticsSink/" + getMetricSafeName() + "/UniqueStatistics";
        _ageName = "Sinks/PeriodicStatisticsSink/" + getMetricSafeName() + "/Age";
        _metrics.set(createMetrics());

        // Write the metrics periodically
        _executor = executor;
        _executor.scheduleAtFixedRate(
                new MetricsLogger(),
                builder._intervalInMilliseconds,
                builder._intervalInMilliseconds,
                TimeUnit.MILLISECONDS);
    }


    private PeriodicStatisticsSink(final Builder builder) {
        this(builder, Executors.newSingleThreadScheduledExecutor());
    }

    private final MetricsFactory _metricsFactory;
    private final AtomicReference<Metrics> _metrics = new AtomicReference<>();

    private final LongAccumulator _age = new LongAccumulator(Math::max, 0);
    private final String _aggregatedDataName;
    private final String _uniqueMetricsName;
    private final String _uniqueStatisticsName;
    private final String _ageName;
    private final AtomicLong _aggregatedData = new AtomicLong(0);
    private final AtomicReference<Set<String>> _uniqueMetrics = new AtomicReference<>(
            Sets.newSetFromMap(Maps.<String, Boolean>newConcurrentMap()));
    private final AtomicReference<Set<String>> _uniqueStatistics = new AtomicReference<>(
            Sets.newSetFromMap(Maps.<String, Boolean>newConcurrentMap()));

    private final ScheduledExecutorService _executor;

    private static final Logger LOGGER = LoggerFactory.getLogger(PeriodicStatisticsSink.class);
    private static final int EXECUTOR_TIMEOUT_IN_SECONDS = 30;

    private final class MetricsLogger implements Runnable {

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            final Metrics oldMetrics = _metrics.getAndSet(createMetrics());
            flushMetrics(oldMetrics);
        }
    }

    /**
     * Implementation of builder pattern for <code>PeriodicStatisticsSink</code>.
     *
     * @author Ville Koskela (vkoskela at groupon dot com)
     */
    public static final class Builder extends BaseSink.Builder<Builder, PeriodicStatisticsSink> {

        /**
         * Public constructor.
         */
        public Builder() {
            super(PeriodicStatisticsSink.class);
        }

        /**
         * The interval in milliseconds between statistic flushes. Cannot be null;
         * minimum 1. Default is 1.
         *
         * @param value The interval in seconds between flushes.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setIntervalInMilliseconds(final Long value) {
            _intervalInMilliseconds = value;
            return this;
        }

        /**
         * Instance of <code>MetricsFactory</code>. Cannot be null. This field
         * may be injected automatically by Jackson/Guice if setup to do so.
         *
         * @param value Instance of <code>MetricsFactory</code>.
         * @return This instance of <code>Builder</code>.
         */
        public Builder setMetricsFactory(final MetricsFactory value) {
            _metricsFactory = value;
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Builder self() {
            return this;
        }

        @NotNull
        @Min(value = 1)
        private Long _intervalInMilliseconds = 500L;
        @JacksonInject
        @NotNull
        private MetricsFactory _metricsFactory;
    }
}
