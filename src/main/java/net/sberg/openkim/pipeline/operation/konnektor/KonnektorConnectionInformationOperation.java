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
package net.sberg.openkim.pipeline.operation.konnektor;

import de.gematik.ws.conn.connectorcommon.v5.Connector;
import de.gematik.ws.conn.eventservice.v7.GetResourceInformationResponse;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.KonnektorWebserviceUtils;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.GetResourceInformationOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class KonnektorConnectionInformationOperation implements IPipelineOperation {

    private static final Logger log = LoggerFactory.getLogger(KonnektorConnectionInformationOperation.class);
    public static final String NAME = "KonnektorConnectionInformation";

    @Autowired
    private GetResourceInformationOperation getResourceInformationOperation;

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

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            getResourceInformationOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    try {
                        GetResourceInformationResponse getResourceInformationResponse = (GetResourceInformationResponse) context.getEnvironmentValue(GetResourceInformationOperation.NAME, GetResourceInformationOperation.ENV_GET_RESOURCE_INFORMATION_RESPONSE);

                        if (!getResourceInformationResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                            defaultPipelineOperationContext.setEnvironmentValue(getResourceInformationOperation.getName(), ENV_EXCEPTION, new IllegalStateException("request getResourceInformations status not OK for the konnektor: " + konnektor.getIp()));
                        }
                        else {
                            Connector connector = getResourceInformationResponse.getConnector();
                            konnektor.setConnectedWithTI(connector.getVPNTIStatus().getConnectionStatus().equals("Online"));
                            konnektor.setConnectedWithSIS(connector.getVPNSISStatus().getConnectionStatus().equals("Online"));

                            logger.logLine(konnektor.getIp() + ": mit TI verbunden - " + connector.getVPNTIStatus().getConnectionStatus() + ", mit SIS verbunden - " + connector.getVPNSISStatus().getConnectionStatus());
                        }
                    } catch (Exception e) {
                        defaultPipelineOperationContext.setEnvironmentValue(getResourceInformationOperation.getName(), ENV_EXCEPTION, e);
                    }
                },
                (context, e) -> {
                    defaultPipelineOperationContext.setEnvironmentValue(getResourceInformationOperation.getName(), ENV_EXCEPTION, e);
                }
            );

            timeMetric.stopAndPublish();

            if (hasError(defaultPipelineOperationContext, new String[] {NAME,getResourceInformationOperation.getName()})) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
            }
            else {
                okConsumer.accept(defaultPipelineOperationContext);
            }

        } catch (Exception e) {
            log.error("error on executing the KonnektorConnectionInformationOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }

}
