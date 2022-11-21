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
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

public class DefaultMetricFactory implements MetricFactory {

    private DefaultLogger logger;

    @Override
    public Metric generate(String name) {
        return new DefaultMetric(name, logger);
    }

    @Override
    public TimeMetric timer(String name) {

        if (logger.getDefaultLoggerContext().isHtmlMode()) {
            logger.logLine("<span style = \"font-weight: bold;\">metric begin for: " + name + "</span>");
        } else {
            logger.logLine("metric begin for: " + name);
        }

        logger.handleDepth(1);
        return new DefaultTimeMetric(name, logger);
    }

    private DefaultMetricFactory() {
    }

    public DefaultMetricFactory(DefaultLogger logger) {
        this.logger = logger;
    }

    @Override
    public <T> Publisher<T> decoratePublisherWithTimerMetric(String name, Publisher<T> publisher) {
        return Flux.using(() -> timer(name),
            any -> publisher,
            TimeMetric::stopAndPublish);
    }

    @Override
    public <T> Publisher<T> decoratePublisherWithTimerMetricLogP99(String name, Publisher<T> publisher) {
        return Flux.using(() -> timer(name),
            any -> publisher,
            timer -> timer.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD));
    }
}
