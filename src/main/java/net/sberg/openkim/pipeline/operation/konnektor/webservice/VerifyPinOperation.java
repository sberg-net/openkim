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

import de.gematik.ws.conn.cardservice.v8.VerifyPin;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import jakarta.xml.bind.JAXBElement;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
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
public class VerifyPinOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(VerifyPinOperation.class);
    public static final String NAME = "VerifyPin";

    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_PINTYP = "pinTyp";
    public static final String ENV_PIN_RESPONSE_TYPE = "pinResponseType";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            PinResponseType pinResponseType = (PinResponseType) context.getEnvironmentValue(NAME, ENV_PIN_RESPONSE_TYPE);
            context.getLogger().logLine("Status = " + pinResponseType.getStatus().getResult());
            context.getLogger().logLine("PinResult = " + pinResponseType.getPinResult());
            context.getLogger().logLine("LeftTries = " + pinResponseType.getLeftTries());
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

            VerifyPin verifyPin = new VerifyPin();
            if (!verifyPin.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "card webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + verifyPin.getClass().getPackageName()
                ));
            }
            else {
                ContextType contextType = new ContextType();
                contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
                contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
                contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
                verifyPin.setContext(contextType);

                verifyPin.setPinTyp((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_PINTYP));
                verifyPin.setCardHandle((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE));

                timeMetric = metricFactory.timer(NAME);
                PinResponseType pinResponseType = ((JAXBElement<PinResponseType>) webserviceConnector.getSoapResponse(verifyPin)).getValue();
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_PIN_RESPONSE_TYPE, pinResponseType);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the VerifyPinOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
