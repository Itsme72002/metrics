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

package com.arpnetworking.tsdaggregator.parsers;

import com.arpnetworking.tsdaggregator.model.Metric;
import com.arpnetworking.tsdaggregator.model.MetricType;
import com.arpnetworking.tsdaggregator.model.Record;
import com.arpnetworking.tsdcore.model.Quantity;
import com.arpnetworking.tsdcore.parsers.exceptions.ParsingException;
import com.google.common.io.Resources;

import org.hamcrest.collection.IsMapContaining;
import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Tests for the 2c version of the query log format.
 *
 * @author Brandon Arp (barp at groupon dot com)
 */
public class QueryLogParserV2cTest {

    @Test
    public void testParse() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testParse.json");

        Assert.assertNotNull(record);
        final Map<String, ? extends Metric> map = record.getMetrics();
        Assert.assertEquals(5, map.size());

        final Metric bestForTimer = map.get("/incentive/bestfor");
        List<Quantity> vals = bestForTimer.getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(2070d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(1844d, vals.get(1).getValue(), 0.001);
        Assert.assertEquals(MetricType.TIMER, bestForTimer.getType());

        final Metric counter1Var = map.get("counter1");
        vals = counter1Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(7d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter1Var.getType());

        final Metric counter2Var = map.get("counter2");
        vals = counter2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter2Var.getType());

        final Metric gauge1Var = map.get("gauge1");
        vals = gauge1Var.getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(2d, vals.get(1).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge1Var.getType());

        final Metric gauge2Var = map.get("gauge2");
        vals = gauge2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(15d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge2Var.getType());

        Assert.assertEquals(new DateTime((long) (1347527687.686 * 1000d), ISOChronology.getInstanceUTC()), record.getTime());
    }

    @Test
    public void testAnnotations() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testAnnotations.json");

        Assert.assertNotNull(record);
        Assert.assertEquals(2, record.getAnnotations().size());
        Assert.assertThat(record.getAnnotations(), IsMapContaining.hasEntry("method", "POST"));
        Assert.assertThat(record.getAnnotations(), IsMapContaining.hasEntry("request_id", "c5251254-8f7c-4c21-95da-270eb66e100b"));
    }

    @Test
    public void testMissingFinalTimestampFallback() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testMissingFinalTimestampFallback.json");

        Assert.assertNotNull(record);
        Assert.assertEquals(new DateTime((long) (1347527680.486 * 1000d), ISOChronology.getInstanceUTC()), record.getTime());
    }

    @Test
    public void testBadTimestampFallback() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testBadTimestampFallback.json");

        Assert.assertNotNull(record);
        Assert.assertEquals(new DateTime((long) (1347527680.486 * 1000d), ISOChronology.getInstanceUTC()), record.getTime());
    }

    @Test(expected = ParsingException.class)
    public void testBothTimestampsBad() throws ParsingException, IOException {
        parseRecord("QueryLogParserV2cTest.testBothTimestampsBad.json");
    }

    @Test(expected = ParsingException.class)
    public void testBadCounters() throws ParsingException, IOException {
        parseRecord("QueryLogParserV2cTest.testBadCounters.json");
    }

    @Test(expected = ParsingException.class)
    public void testBadAnnotations() throws ParsingException, IOException {
        parseRecord("QueryLogParserV2cTest.testBadAnnotations.json");
    }

    @Test
    public void testBadValues() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testBadValues.json");

        Assert.assertNotNull(record);
        final Map<String, ? extends Metric> map = record.getMetrics();
        Assert.assertEquals(5, map.size());

        final Metric bestForTimer = map.get("/incentive/bestfor");
        List<Quantity> vals = bestForTimer.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(2070d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.TIMER, bestForTimer.getType());

        final Metric counter1Var = map.get("counter1");
        vals = counter1Var.getValues();
        Assert.assertEquals(0, vals.size());
        Assert.assertEquals(MetricType.COUNTER, counter1Var.getType());

        final Metric counter2Var = map.get("counter2");
        vals = counter2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter2Var.getType());

        final Metric gauge1Var = map.get("gauge1");
        vals = gauge1Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge1Var.getType());

        final Metric gauge2Var = map.get("gauge2");
        vals = gauge2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(15d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge2Var.getType());

        Assert.assertEquals(new DateTime((long) (1347527687.686 * 1000d), ISOChronology.getInstanceUTC()), record.getTime());
    }

    @Test
    public void testMissingCounters() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testMissingCounters.json");

        Assert.assertNotNull(record);
        final Map<String, ? extends Metric> map = record.getMetrics();
        Assert.assertEquals(3, map.size());

        final Metric bestForTimer = map.get("/incentive/bestfor");
        List<Quantity> vals = bestForTimer.getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(2070d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(1844d, vals.get(1).getValue(), 0.001);
        Assert.assertEquals(MetricType.TIMER, bestForTimer.getType());

        final Metric gauge1Var = map.get("gauge1");
        vals = gauge1Var.getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(2d, vals.get(1).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge1Var.getType());

        final Metric gauge2Var = map.get("gauge2");
        vals = gauge2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(15d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge2Var.getType());

        Assert.assertEquals(new DateTime((long) (1347527687.686 * 1000d), ISOChronology.getInstanceUTC()), record.getTime());
    }

    @Test
    public void testMissingTimers() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testMissingTimers.json");

        Assert.assertNotNull(record);
        final Map<String, ? extends Metric> map = record.getMetrics();
        Assert.assertEquals(4, map.size());

        final Metric counter1Var = map.get("counter1");
        List<Quantity> vals = counter1Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(7d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter1Var.getType());

        final Metric counter2Var = map.get("counter2");
        vals = counter2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter2Var.getType());

        final Metric gauge1Var = map.get("gauge1");
        vals = gauge1Var.getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(2d, vals.get(1).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge1Var.getType());

        final Metric gauge2Var = map.get("gauge2");
        vals = gauge2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(15d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.GAUGE, gauge2Var.getType());

        final DateTime timestamp = record.getTime();
        Assert.assertEquals(new DateTime((long) (1347527687.686 * 1000d), ISOChronology.getInstanceUTC()), timestamp);
    }

    @Test
    public void testMissingGauges() throws ParsingException, IOException {
        final Record record = parseRecord("QueryLogParserV2cTest.testMissingGauges.json");

        Assert.assertNotNull(record);
        final Map<String, ? extends Metric> map = record.getMetrics();
        Assert.assertEquals(3, map.size());

        final Metric bestForTimer = map.get("/incentive/bestfor");
        List<Quantity> vals = bestForTimer.getValues();
        Assert.assertEquals(2, vals.size());
        Assert.assertEquals(2070d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(1844d, vals.get(1).getValue(), 0.001);
        Assert.assertEquals(MetricType.TIMER, bestForTimer.getType());

        final Metric counter1Var = map.get("counter1");
        vals = counter1Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(7d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter1Var.getType());

        final Metric counter2Var = map.get("counter2");
        vals = counter2Var.getValues();
        Assert.assertEquals(1, vals.size());
        Assert.assertEquals(1d, vals.get(0).getValue(), 0.001);
        Assert.assertEquals(MetricType.COUNTER, counter2Var.getType());

        Assert.assertEquals(new DateTime((long) (1347527687.686 * 1000d), ISOChronology.getInstanceUTC()), record.getTime());
    }

    @Test(expected = ParsingException.class)
    public void testMissingAnnotations() throws ParsingException, IOException {
        parseRecord("QueryLogParserV2cTest.testMissingAnnotations.json");
    }

    private static Record parseRecord(final String fileName) throws ParsingException, IOException {
        return new QueryLogParser().parse(Resources.toByteArray(Resources.getResource(
                QueryLogParserV2cTest.class, fileName)));
    }
}
