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

import com.google.common.base.Stopwatch;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.TimeMetric;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DefaultTimeMetric implements TimeMetric {
    static class DefaultExecutionResult implements ExecutionResult {
        private final Duration elasped;

        DefaultExecutionResult(Duration elasped) {
            this.elasped = elasped;
        }

        @Override
        public Duration elasped() {
            return elasped;
        }

        @Override
        public ExecutionResult logWhenExceedP99(Duration thresholdInNanoSeconds) {
            return this;
        }
    }

    private final String name;
    private final Stopwatch stopwatch;

    private DefaultLogger logger;

    private DefaultTimeMetric(String name) {
        this.name = name;
        this.stopwatch = Stopwatch.createStarted();
    }

    public DefaultTimeMetric(String name, DefaultLogger logger) {
        this(name);
        this.logger = logger;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ExecutionResult stopAndPublish() {
        long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        logger.handleDepth(-1);

        String text = MessageFormat.format("Time spent in {0}: {1} ms.", name, String.valueOf(elapsed));

        if (logger.getDefaultLoggerContext().isHtmlMode()) {
            logger.logLine("<span style = \"font-weight: bold;\">" + text + "</span>");
        } else {
            logger.logLine(text);
        }

        return new DefaultExecutionResult(Duration.ofNanos(elapsed));
    }

}
