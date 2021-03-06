/**
 * Copyright 2014 Groupon.com
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

package models.protocol.v1;

import com.arpnetworking.metrics.Metrics;
import com.arpnetworking.steno.Logger;
import com.arpnetworking.steno.LoggerFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import models.ConnectionContext;
import models.messages.Command;
import models.messages.MetricReport;
import models.messages.MetricsList;
import models.messages.MetricsListRequest;
import models.messages.NewMetric;
import models.protocol.MessagesProcessor;
import play.libs.Json;

import java.util.Map;
import java.util.Set;

/**
 * Processes metrics-based messages.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public class MetricMessagesProcessor implements MessagesProcessor {
    /**
     * Public constructor.
     *
     * @param connectionContext ConnectionContext where processing takes place
     */
    public MetricMessagesProcessor(final ConnectionContext connectionContext) {
        _connectionContext = connectionContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean handleMessage(final Object message) {
        if (message instanceof Command) {
            //TODO(barp): Map with a POJO mapper [MAI-184]
            final Command command = (Command) message;
            final ObjectNode commandNode = (ObjectNode) command.getCommand();
            final String commandString = commandNode.get("command").asText();
            switch (commandString) {
                case COMMAND_GET_METRICS:
                    _metrics.incrementCounter(GET_METRICS_COUNTER);
                    _connectionContext.getContext().parent().tell(new MetricsListRequest(), _connectionContext.getSelf());
                    break;
                case COMMAND_SUBSCRIBE_METRIC: {
                    _metrics.incrementCounter(SUBSCRIBE_COUNTER);
                    final String service = commandNode.get("service").asText();
                    final String metric = commandNode.get("metric").asText();
                    final String statistic = commandNode.get("statistic").asText();
                    subscribe(service, metric, statistic);
                    break;
                }
                case COMMAND_UNSUBSCRIBE_METRIC: {
                    _metrics.incrementCounter(UNSUBSCRIBE_COUNTER);
                    final String service = commandNode.get("service").asText();
                    final String metric = commandNode.get("metric").asText();
                    final String statistic = commandNode.get("statistic").asText();
                    unsubscribe(service, metric, statistic);
                    break;
                }
                default:
                    return false;
            }
        } else if (message instanceof NewMetric) {
            //TODO(barp): Map with a POJO mapper [MAI-184]
            _metrics.incrementCounter(NEW_METRIC_COUNTER);
            final NewMetric newMetric = (NewMetric) message;
            processNewMetric(newMetric);
        } else if (message instanceof MetricReport) {
            _metrics.incrementCounter(METRIC_REPORT_COUNTER);
            final MetricReport report = (MetricReport) message;
            processMetricReport(report);
        } else if (message instanceof MetricsList) {
            _metrics.incrementCounter(METRIC_LIST_COUNTER);
            final MetricsList metricsList = (MetricsList) message;
            processMetricsList(metricsList);
        } else {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeMetrics(final Metrics metrics) {
        _metrics = metrics;
        metrics.resetCounter(METRIC_LIST_COUNTER);
        metrics.resetCounter(METRIC_REPORT_COUNTER);
        metrics.resetCounter(NEW_METRIC_COUNTER);
        metrics.resetCounter(UNSUBSCRIBE_COUNTER);
        metrics.resetCounter(SUBSCRIBE_COUNTER);
        metrics.resetCounter(GET_METRICS_COUNTER);
    }

    private void processNewMetric(final NewMetric newMetric) {
        final ObjectNode n = Json.newObject();
        n.put("service", newMetric.getService());
        n.put("metric", newMetric.getMetric());
        n.put("statistic", newMetric.getStatistic());
        _connectionContext.sendCommand(COMMAND_NEW_METRIC, n);
    }

    private void processMetricReport(final MetricReport report) {
        final Map<String, Set<String>> metrics = _subscriptions.get(report.getService());
        if (metrics == null) {
            LOGGER.trace()
                    .setMessage("Not sending MetricReport")
                    .addData("reason", "service not found in subscriptions")
                    .addData("service", report.getService())
                    .log();
            return;
        }
        final Set<String> stats = metrics.get(report.getMetric());
        if (stats == null) {
            LOGGER.trace()
                    .setMessage("Not sending MetricReport")
                    .addData("reason", "metric not found in subscriptions")
                    .addData("metric", report.getMetric())
                    .log();
            return;
        }
        if (!stats.contains(report.getStatistic())) {
            LOGGER.trace()
                    .setMessage("Not sending MetricReport")
                    .addData("reason", "statistic not found in subscriptions")
                    .addData("statistic", report.getStatistic())
                    .log();
            return;
        }

        //TODO(barp): Map with a POJO mapper [MAI-184]
        final ObjectNode event = Json.newObject();
        event.put("server", report.getHost());
        event.put("service", report.getService());
        event.put("metric", report.getMetric());
        event.put("timestamp", report.getPeriodStart().getMillis());
        event.put("statistic", report.getStatistic());
        event.put("data", report.getValue());
        _connectionContext.sendCommand(COMMAND_REPORT_METRIC, event);
    }

    private void processMetricsList(final MetricsList metricsList) {
        //TODO(barp): Map with a POJO mapper [MAI-184]
        final ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
        final ArrayNode services = JsonNodeFactory.instance.arrayNode();
        for (final Map.Entry<String, Map<String, Set<String>>> service : metricsList.getMetrics().entrySet()) {
            final ObjectNode serviceObject = JsonNodeFactory.instance.objectNode();
            serviceObject.put("name", service.getKey());
            final ArrayNode metrics = JsonNodeFactory.instance.arrayNode();
            for (final Map.Entry<String, Set<String>> metric : service.getValue().entrySet()) {
                final ObjectNode metricObject = JsonNodeFactory.instance.objectNode();
                metricObject.put("name", metric.getKey());
                final ArrayNode stats = JsonNodeFactory.instance.arrayNode();
                for (final String statistic : metric.getValue()) {
                    final ObjectNode statsObject = JsonNodeFactory.instance.objectNode();
                    statsObject.put("name", statistic);
                    statsObject.set("children", JsonNodeFactory.instance.arrayNode());
                    stats.add(statsObject);
                }
                metricObject.set("children", stats);
                metrics.add(metricObject);
            }
            serviceObject.set("children", metrics);
            services.add(serviceObject);
        }
        dataNode.set("metrics", services);

        _connectionContext.sendCommand(COMMAND_METRICS_LIST, dataNode);
    }

    private void subscribe(final String service, final String metric, final String statistic) {
        if (!_subscriptions.containsKey(service)) {
            _subscriptions.put(service, Maps.<String, Set<String>>newHashMap());
        }

        final Map<String, Set<String>> metrics = _subscriptions.get(service);

        if (!metrics.containsKey(metric)) {
            metrics.put(metric, Sets.<String>newHashSet());
        }

        final Set<String> statistics = metrics.get(metric);

        if (!statistics.contains(statistic)) {
            statistics.add(statistic);
        }
    }

    private void unsubscribe(final String service, final String metric, final String statistic) {
        if (!_subscriptions.containsKey(service)) {
            return;
        }

        final Map<String, Set<String>> metrics = _subscriptions.get(service);
        if (!metrics.containsKey(metric)) {
            return;
        }

        final Set<String> statistics = metrics.get(metric);
        if (statistics.contains(statistic)) {
            statistics.remove(statistic);
        }
    }

    private final Map<String, Map<String, Set<String>>> _subscriptions = Maps.newHashMap();
    private final ConnectionContext _connectionContext;
    private Metrics _metrics;

    private static final String COMMAND_METRICS_LIST = "metricsList";
    private static final String COMMAND_REPORT_METRIC = "report";
    private static final String COMMAND_NEW_METRIC = "newMetric";
    private static final String COMMAND_SUBSCRIBE_METRIC = "subscribe";
    private static final String COMMAND_UNSUBSCRIBE_METRIC = "unsubscribe";
    private static final String COMMAND_GET_METRICS = "getMetrics";
    private static final String METRICS_PREFIX = "MessageProcessor/Metric/";
    private static final String METRIC_LIST_COUNTER = METRICS_PREFIX + "MetricsList";
    private static final String METRIC_REPORT_COUNTER = METRICS_PREFIX + "MetricReport";
    private static final String NEW_METRIC_COUNTER = METRICS_PREFIX + "NewMetric";
    private static final String UNSUBSCRIBE_COUNTER = METRICS_PREFIX + "Command/Unsubscribe";
    private static final String SUBSCRIBE_COUNTER = METRICS_PREFIX + "Command/Subscribe";
    private static final String GET_METRICS_COUNTER = METRICS_PREFIX + "Command/GetMetrics";
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricMessagesProcessor.class);
}
