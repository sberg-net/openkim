/*
 * Copyright 2023 sberg it-systeme GmbH
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
package net.sberg.openkim.pipeline.operation.konnektor.dns;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class DnsRequestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(DnsRequestOperation.class);
    public static final String NAME = "DnsRequest";

    public static final String ENV_DNS_RESULT = "dnsResult";
    public static final String ENV_DOMAIN = "domain";
    public static final String ENV_RECORD_TYPE = "recordType";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {

        TimeMetric timeMetric = null;
        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            String domain = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_DOMAIN);
            String recordType = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_RECORD_TYPE);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);
            logger.logLine("dns request for: " + konnektor.getIp() + " - " + domain);

            DnsResultContainer dnsResultContainer = DnsUtils.request(logger, domain, recordType);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_DNS_RESULT, dnsResultContainer);
            timeMetric.stopAndPublish();

            okConsumer.accept(defaultPipelineOperationContext);
        } catch (Exception e) {
            log.error("error on executing the DnsRequestOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
