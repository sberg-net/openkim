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
package net.sberg.openkim.pipeline.operation.konnektor.webservice;

import de.gematik.ws.conn.cardservice.v8_1_2.GetPinStatus;
import de.gematik.ws.conn.cardservice.v8_1_2.GetPinStatusResponse;
import de.gematik.ws.conn.connectorcontext.ContextType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class GetPinStatusOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(GetPinStatusOperation.class);
    public static final String NAME = "GetPinStatus";

    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_PINTYP = "pinTyp";
    public static final String ENV_PIN_STATUS_RESPONSE = "pinStatusResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            GetPinStatusResponse getPinStatusResponse = (GetPinStatusResponse) context.getEnvironmentValue(NAME, ENV_PIN_STATUS_RESPONSE);
            context.getLogger().logLine("Status = " + getPinStatusResponse.getStatus().getResult());
            context.getLogger().logLine("PinStatus = " + getPinStatusResponse.getPinStatus());
            context.getLogger().logLine("LeftTries = " + getPinStatusResponse.getLeftTries());
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(NAME),
                    logger
            );

            GetPinStatus getPinStatus = new GetPinStatus();
            if (!getPinStatus.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "card webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + getPinStatus.getClass().getPackageName()
                ));
            }
            else {
                ContextType contextType = new ContextType();
                contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
                contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
                contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
                getPinStatus.setContext(contextType);

                getPinStatus.setPinTyp((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_PINTYP));
                getPinStatus.setCardHandle((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE));

                timeMetric = metricFactory.timer(NAME);
                GetPinStatusResponse getPinStatusResponse = (GetPinStatusResponse) webserviceConnector.getSoapResponse(getPinStatus);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_PIN_STATUS_RESPONSE, getPinStatusResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the GetPinStatusOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
