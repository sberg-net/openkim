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
package net.sberg.openkim.pipeline.operation.konnektor.vzd;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

@PipelineOperation
public class SearchVzdOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(SearchVzdOperation.class);
    public static final String NAME = "SearchVzd";

    public static final String ENV_VZD_SEARCH_BASE = "vzdSearchBase";
    public static final String ENV_VZD_SEARCH_VALUE = "vzdSearchValue";
    public static final String ENV_VZD_ONLY_SEARCH_MAIL_ATTR = "vzdOnlySearchMailAttr";
    public static final String ENV_VZD_RESULT_WITH_CERTIFICATES = "resultWithCertificates";
    public static final String ENV_VZD_RESULT = "vzdResult";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> consumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            String searchBase = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_VZD_SEARCH_BASE);
            String searchValue = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_VZD_SEARCH_VALUE);
            boolean onlySearchMailAttr = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_VZD_ONLY_SEARCH_MAIL_ATTR);
            boolean resultWithCertificates = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_VZD_RESULT_WITH_CERTIFICATES);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            List<VzdResult> result = VzdUtils.search(logger, searchBase, searchValue, onlySearchMailAttr, resultWithCertificates);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VZD_RESULT, result);
            timeMetric.stopAndPublish();

            consumer.accept(defaultPipelineOperationContext);
            return true;
        } catch (Exception e) {
            log.error("error on executing the LoadVzdCertsOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            return false;
        }
    }
}
