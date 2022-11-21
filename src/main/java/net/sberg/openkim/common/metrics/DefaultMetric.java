/*
 * Copyright 2022 sberg it-systeme GmbH
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package net.sberg.openkim.common.metrics;

import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.Metric;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultMetric implements Metric {

    private final AtomicInteger value;
    private final String metricName;

    private DefaultLogger logger;

    private DefaultMetric(String metricName) {
        this.metricName = metricName;
        value = new AtomicInteger();
    }

    public DefaultMetric(String metricName, DefaultLogger logger) {
        this(metricName);
        this.logger = logger;
    }

    @Override
    public void increment() {
        value.incrementAndGet();
    }

    @Override
    public void decrement() {
        value.decrementAndGet();
    }

    @Override
    public void add(int i) {
        value.addAndGet(i);
    }

    @Override
    public void remove(int i) {
        value.addAndGet(-1 * i);
    }

    @Override
    public long getCount() {
        long counter = value.longValue();
        if (counter < 0) {
            logger.logLine(MessageFormat.format("counter value({0}) of the metric '{0}' should not be a negative number", String.valueOf(value), metricName));
            return 0;
        }

        return counter;
    }
}
