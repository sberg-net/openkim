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

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.eventservice.v7.GetCards;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import net.sberg.openkim.common.StringUtils;
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
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class GetCardsOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(GetCardsOperation.class);
    public static final String NAME = "GetCards";

    public static final String ENV_CARDTYPE = "cardType";
    public static final String ENV_CARD_TERMINAL_ID = "cardTerminalId";
    public static final String ENV_SLOT_ID = "slotId";
    public static final String ENV_GET_CARDS_RESPONSE = "getCardsResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            try {
                GetCardsResponse getCardsResponse = (GetCardsResponse) context.getEnvironmentValue(NAME, ENV_GET_CARDS_RESPONSE);
                context.getLogger().logLine("Status = " + getCardsResponse.getStatus().getResult());
                for (Iterator<CardInfoType> iterator = getCardsResponse.getCards().getCard().iterator(); iterator.hasNext(); ) {
                    context.getLogger().logLine("***********************************");
                    CardInfoType cardInfoType = iterator.next();
                    context.getLogger().logLine("Kartenterminal = " + cardInfoType.getCtId());
                    context.getLogger().logLine("Slot = " + cardInfoType.getSlotId());
                    context.getLogger().logLine("CardHandle = " + cardInfoType.getCardHandle());
                    context.getLogger().logLine("Kvnr = " + cardInfoType.getKvnr());
                    context.getLogger().logLine("CardType = " + cardInfoType.getCardType());
                    context.getLogger().logLine("CardHolderName = " + cardInfoType.getCardHolderName());
                    context.getLogger().logLine("Iccsn = " + cardInfoType.getIccsn());
                    context.getLogger().logLine("InsertTime = " + StringUtils.convertToStr(cardInfoType.getInsertTime()));
                    context.getLogger().logLine("CertificateExpirationDate = " + StringUtils.convertToStr(cardInfoType.getCertificateExpirationDate()));
                }
            }
            catch (Exception e) {
                log.error("error on consuming GetCardsResponse", e);
            }
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EventService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(konnektor, packageName, konnektorServiceBean, konnektorServiceBean.createSoapAction(NAME), logger);

            GetCards getCards = new GetCards();
            getCards.setContext(contextType);
            String cardType = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDTYPE);
            String cardTerminalId = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARD_TERMINAL_ID);
            String slotId = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SLOT_ID);

            if (cardType != null && !cardType.trim().isEmpty()) {
                getCards.setCardType(CardTypeType.valueOf(cardType));
            }
            if (cardTerminalId != null && !cardTerminalId.trim().isEmpty()) {
                getCards.setCtId(cardTerminalId);
            }
            if (slotId != null && !slotId.trim().isEmpty()) {
                getCards.setSlotId(BigInteger.valueOf(Integer.parseInt(slotId)));
            }

            if (!getCards.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "event webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName + " - "
                        + getCards.getClass().getPackageName()
                ));
            }
            else {
                timeMetric = metricFactory.timer(NAME);
                GetCardsResponse getCardsResponse = (GetCardsResponse) webserviceConnector.getSoapResponse(getCards);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_GET_CARDS_RESPONSE, getCardsResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the GetCardsOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
