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
package net.sberg.openkim.konfiguration.konnektor.webservice;

import de.gematik.ws.conn.cardterminalservice.v1_1_0.RequestCard;
import de.gematik.ws.conn.cardterminalservice.v1_1_0.RequestCardResponse;
import de.gematik.ws.conn.cardterminalservice.v1_1_0.Slot;
import de.gematik.ws.conn.connectorcontext.ContextType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Service
public class CardTerminalService {

    private static final Logger log = LoggerFactory.getLogger(CardTerminalService.class);

    public String execute(
        DefaultLogger logger,
        CardTerminalWebserviceBean cardTerminalWebserviceBean
    ) throws Exception {
        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardTerminalService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("RequestCard"),
                logger
            );

            RequestCard requestCard = new RequestCard();
            if (!requestCard.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                    "cardterminal webservice not valid "
                    + cardTerminalWebserviceBean.getKonnId()
                    + " - "
                    + cardTerminalWebserviceBean.getWsId()
                    + " - packagename not equal "
                    + packageName
                    + " - "
                    + requestCard.getClass().getPackageName()
                );
            }

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
            requestCard.setContext(contextType);

            Slot slot = new Slot();
            slot.setCtId(cardTerminalWebserviceBean.getCtId());
            slot.setSlotId(BigInteger.valueOf(cardTerminalWebserviceBean.getSlotId()));
            requestCard.setSlot(slot);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("CardTerminalService:requestCard:execute");
            RequestCardResponse requestCardResponse = (RequestCardResponse) webserviceConnector.getSoapResponse(requestCard);
            timeMetric.stopAndPublish();

            logger.logLine("Status = " + requestCardResponse.getStatus().getResult());
            logger.logLine("CardHandle = " + requestCardResponse.getCard().getCardHandle());
            logger.logLine("Kartenterminal = " + requestCardResponse.getCard().getCtId());
            logger.logLine("CardType = " + requestCardResponse.getCard().getCardType());
            return logger.getLogContentAsStr();
        } catch (Exception e) {
            log.error("error on executing the CardTerminalService for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
