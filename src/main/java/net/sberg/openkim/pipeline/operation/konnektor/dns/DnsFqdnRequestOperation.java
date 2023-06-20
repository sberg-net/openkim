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
import org.xbill.DNS.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class DnsFqdnRequestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(DnsFqdnRequestOperation.class);
    public static final String NAME = "DnsFqdnRequest";

    public static final String ENV_DNS_RESULT = "dnsResult";
    public static final String ENV_DOMAIN = "domain";
    public static final String ENV_PTR_DOMAIN_SUFFIX = "ptrDomainSuffix";

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
            String ptrDomainSuffix = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_PTR_DOMAIN_SUFFIX);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);
            logger.logLine("fqdnRequest for: " + konnektor.getIp() + " - " + domain);

            List<DnsResult> srvResults = new ArrayList<>();

            DnsResultContainer resultContainer = DnsUtils.request(logger, domain, Type.string(Type.PTR));
            if (resultContainer.isError()) {
                timeMetric.stopAndPublish();
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_DNS_RESULT, resultContainer);
                okConsumer.accept(defaultPipelineOperationContext);
            }
            else {
                List<DnsResult> ptrResults = resultContainer.getResult();

                for (Iterator<DnsResult> iterator = ptrResults.iterator(); iterator.hasNext(); ) {
                    DnsResult ptrResult = iterator.next();
                    if (ptrResult.getAddress().toLowerCase().endsWith(ptrDomainSuffix.toLowerCase())) {
                        resultContainer = DnsUtils.request(logger, ptrResult.getAddress(), Type.string(Type.SRV));
                        if (resultContainer.isError()) {
                            break;
                        }
                        srvResults.addAll(resultContainer.getResult());
                    }
                }

                timeMetric.stopAndPublish();

                if (!resultContainer.isError()) {
                    resultContainer = new DnsResultContainer();
                    resultContainer.setResult(srvResults);
                }
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_DNS_RESULT, resultContainer);

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the DnsFqdnRequestOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
