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

import de.gematik.ws.conn.cardterminalservice.v1.RequestCard;
import de.gematik.ws.conn.cardterminalservice.v1.RequestCardResponse;
import de.gematik.ws.conn.cardterminalservice.v1.Slot;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
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

import java.math.BigInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class RequestCardOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(RequestCardOperation.class);
    public static final String NAME = "RequestCard";

    public static final String ENV_CARD_TERMINAL_ID = "cardTerminalId";
    public static final String ENV_SLOT_ID = "slotId";
    public static final String ENV_REQ_CARD_RESPONSE = "requestCardResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            RequestCardResponse requestCardResponse = (RequestCardResponse) context.getEnvironmentValue(NAME, ENV_REQ_CARD_RESPONSE);
            context.getLogger().logLine("Status = " + requestCardResponse.getStatus().getResult());
            context.getLogger().logLine("CardHandle = " + requestCardResponse.getCard().getCardHandle());
            context.getLogger().logLine("Kartenterminal = " + requestCardResponse.getCard().getCtId());
            context.getLogger().logLine("CardType = " + requestCardResponse.getCard().getCardType());
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardTerminalService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(NAME),
                    logger
            );

            RequestCard requestCard = new RequestCard();
            if (!requestCard.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "cardterminal webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + requestCard.getClass().getPackageName()
                ));
            }
            else {
                ContextType contextType = new ContextType();
                contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
                contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
                contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
                requestCard.setContext(contextType);

                Slot slot = new Slot();
                slot.setCtId((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARD_TERMINAL_ID));
                slot.setSlotId(BigInteger.valueOf((Integer) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SLOT_ID)));
                requestCard.setSlot(slot);

                timeMetric = metricFactory.timer(NAME);
                RequestCardResponse requestCardResponse = (RequestCardResponse) webserviceConnector.getSoapResponse(requestCard);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_REQ_CARD_RESPONSE, requestCardResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the RequestCardOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
