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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.cardterminalinfo.v8.CardTerminalInfoType;
import de.gematik.ws.conn.connectorcommon.Connector;
import de.gematik.ws.conn.connectorcommon.ErrorState;
import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.eventservice.v7_2_0.*;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private static final String OP_GET_CARDS = "GetCards";
    private static final String OP_GET_CARD_TERMINALS = "GetCardTerminals";
    private static final String OP_GET_RESOURCE_INFORMATION = "GetResourceInformation";

    public String execute(
        DefaultLogger logger,
        WebserviceBean webserviceBean,
        Map serviceBeanMap
    ) throws Exception {
        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EventService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            if (webserviceBean.getOpId().equals(OP_GET_CARDS)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(konnektor, packageName, konnektorServiceBean, konnektorServiceBean.createSoapAction("GetCards"), logger);

                EventGetCardWebserviceBean eventGetCardWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, EventGetCardWebserviceBean.class);
                GetCards getCards = new GetCards();
                getCards.setContext(contextType);
                if (eventGetCardWebserviceBean.getCardType() != null && !eventGetCardWebserviceBean.getCardType().trim().isEmpty()) {
                    getCards.setCardType(CardTypeType.valueOf(eventGetCardWebserviceBean.getCardType()));
                }
                if (eventGetCardWebserviceBean.getCtId() != null && !eventGetCardWebserviceBean.getCtId().trim().isEmpty()) {
                    getCards.setCtId(eventGetCardWebserviceBean.getCtId());
                }
                if (eventGetCardWebserviceBean.getSlotId() != null && !eventGetCardWebserviceBean.getSlotId().trim().isEmpty()) {
                    getCards.setSlotId(BigInteger.valueOf(Integer.parseInt(eventGetCardWebserviceBean.getSlotId())));
                }

                if (!getCards.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "event webservice not valid "
                        + webserviceBean.getKonnId()
                        + " - "
                        + webserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName + " - "
                        + getCards.getClass().getPackageName()
                    );
                }

                timeMetric = metricFactory.timer("EventService:getCards");
                GetCardsResponse getCardsResponse = (GetCardsResponse) webserviceConnector.getSoapResponse(getCards);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + getCardsResponse.getStatus().getResult());
                for (Iterator<CardInfoType> iterator = getCardsResponse.getCards().getCard().iterator(); iterator.hasNext(); ) {
                    logger.logLine("***********************************");
                    CardInfoType cardInfoType = iterator.next();
                    logger.logLine("Kartenterminal = " + cardInfoType.getCtId());
                    logger.logLine("Slot = " + cardInfoType.getSlotId());
                    logger.logLine("CardHandle = " + cardInfoType.getCardHandle());
                    logger.logLine("Kvnr = " + cardInfoType.getKvnr());
                    logger.logLine("CardType = " + cardInfoType.getCardType());
                    logger.logLine("CardHolderName = " + cardInfoType.getCardHolderName());
                    logger.logLine("Iccsn = " + cardInfoType.getIccsn());
                    logger.logLine("InsertTime = " + StringUtils.convertToStr(cardInfoType.getInsertTime()));
                    logger.logLine("CertificateExpirationDate = " + StringUtils.convertToStr(cardInfoType.getCertificateExpirationDate()));
                }

                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_GET_CARD_TERMINALS)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("GetCardTerminals"),
                    logger
                );

                GetCardTerminals getCardTerminals = new GetCardTerminals();
                getCardTerminals.setContext(contextType);

                if (!getCardTerminals.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "event webservice not valid "
                        + webserviceBean.getKonnId()
                        + " - " + webserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName + " - "
                        + getCardTerminals.getClass().getPackageName()
                    );
                }

                timeMetric = metricFactory.timer("EventService:getCardTerminals");
                GetCardTerminalsResponse getCardTerminalsResponse = (GetCardTerminalsResponse) webserviceConnector.getSoapResponse(getCardTerminals);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + getCardTerminalsResponse.getStatus().getResult());
                for (Iterator<CardTerminalInfoType> iterator = getCardTerminalsResponse.getCardTerminals().getCardTerminal().iterator(); iterator.hasNext(); ) {
                    logger.logLine("***********************************");
                    CardTerminalInfoType cardTerminalInfoType = iterator.next();
                    logger.logLine("Kartenterminal = " + cardTerminalInfoType.getCtId());
                    logger.logLine("Name = " + cardTerminalInfoType.getName());
                    logger.logLine("Slotanzahl = " + cardTerminalInfoType.getSlots());
                    logger.logLine("Mac-Adresse = " + cardTerminalInfoType.getMacAddress());
                    logger.logLine("IP-Adresse = " + cardTerminalInfoType.getIPAddress().getIPV4Address());
                    logger.logLine("Produkt = " + cardTerminalInfoType.getProductInformation().getProductIdentification().getProductVendorID() + " " + cardTerminalInfoType.getProductInformation().getProductIdentification().getProductVersion().getCentral());
                    logger.logLine("Verbunden = " + cardTerminalInfoType.isConnected());
                    logger.logLine("Physikalisch = " + cardTerminalInfoType.isISPHYSICAL());
                }
                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_GET_RESOURCE_INFORMATION)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("GetResourceInformation"),
                    logger
                );

                EventGetResourceInformationWebserviceBean eventGetResourceInformationWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, EventGetResourceInformationWebserviceBean.class);
                GetResourceInformation getResourceInformation = new GetResourceInformation();
                getResourceInformation.setContext(contextType);
                if (eventGetResourceInformationWebserviceBean.getCardHandle() != null && !eventGetResourceInformationWebserviceBean.getCardHandle().trim().isEmpty()) {
                    getResourceInformation.setCardHandle(eventGetResourceInformationWebserviceBean.getCardHandle());
                }
                if (eventGetResourceInformationWebserviceBean.getIccsn() != null && !eventGetResourceInformationWebserviceBean.getIccsn().trim().isEmpty()) {
                    getResourceInformation.setIccsn(eventGetResourceInformationWebserviceBean.getIccsn());
                }
                if (eventGetResourceInformationWebserviceBean.getCtId() != null && !eventGetResourceInformationWebserviceBean.getCtId().trim().isEmpty()) {
                    getResourceInformation.setCtId(eventGetResourceInformationWebserviceBean.getCtId());
                }
                if (eventGetResourceInformationWebserviceBean.getSlotId() != null && !eventGetResourceInformationWebserviceBean.getSlotId().trim().isEmpty()) {
                    getResourceInformation.setSlotId(BigInteger.valueOf(Integer.parseInt(eventGetResourceInformationWebserviceBean.getSlotId())));
                }

                if (!getResourceInformation.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "event webservice not valid "
                        + webserviceBean.getKonnId()
                        + " - " + webserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName
                        + " - " + getResourceInformation.getClass().getPackageName()
                    );
                }

                timeMetric = metricFactory.timer("EventService:getResourceInformation");
                GetResourceInformationResponse getResourceInformationResponse = (GetResourceInformationResponse) webserviceConnector.getSoapResponse(getResourceInformation);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + getResourceInformationResponse.getStatus().getResult());

                if (getResourceInformationResponse.getCardTerminal() != null) {
                    logger.logLine("***********************************");
                    CardTerminalInfoType cardTerminalInfoType = getResourceInformationResponse.getCardTerminal();
                    logger.logLine("Kartenterminal = " + cardTerminalInfoType.getCtId());
                    logger.logLine("Name = " + cardTerminalInfoType.getName());
                    logger.logLine("Slotanzahl = " + cardTerminalInfoType.getSlots());
                    logger.logLine("Mac-Adresse = " + cardTerminalInfoType.getMacAddress());
                    logger.logLine("IP-Adresse = " + cardTerminalInfoType.getIPAddress().getIPV4Address());
                    logger.logLine("Produkt = " + cardTerminalInfoType.getProductInformation().getProductIdentification().getProductVendorID() + " " + cardTerminalInfoType.getProductInformation().getProductIdentification().getProductVersion().getCentral());
                    logger.logLine("Verbunden = " + cardTerminalInfoType.isConnected());
                    logger.logLine("Physikalisch = " + cardTerminalInfoType.isISPHYSICAL());
                }
                if (getResourceInformationResponse.getCard() != null) {
                    logger.logLine("***********************************");
                    CardInfoType cardInfoType = getResourceInformationResponse.getCard();
                    logger.logLine("Kartenterminal = " + cardInfoType.getCtId());
                    logger.logLine("Slot = " + cardInfoType.getSlotId());
                    logger.logLine("CardHandle = " + cardInfoType.getCardHandle());
                    logger.logLine("Kvnr = " + cardInfoType.getKvnr());
                    logger.logLine("CardType = " + cardInfoType.getCardType().value());
                    logger.logLine("CardHolderName = " + cardInfoType.getCardHolderName());
                    logger.logLine("Iccsn = " + cardInfoType.getIccsn());
                    logger.logLine("CardVersion = " + cardInfoType.getCardVersion());
                    logger.logLine("InsertTime = " + StringUtils.convertToStr(cardInfoType.getInsertTime()));
                    logger.logLine("CertificateExpirationDate = " + StringUtils.convertToStr(cardInfoType.getCertificateExpirationDate()));
                }
                if (getResourceInformationResponse.getConnector() != null) {
                    logger.logLine("***********************************");
                    Connector connector = getResourceInformationResponse.getConnector();
                    logger.logLine("States:");
                    for (Iterator<ErrorState> iterator = connector.getOperatingState().getErrorState().iterator(); iterator.hasNext(); ) {
                        ErrorState errorState = iterator.next();
                        logger.logLine("State = " + errorState.getErrorCondition() + " - " + errorState.getSeverity() + " - " + errorState.getType() + " - " + StringUtils.convertToStr(errorState.getValidFrom()));
                    }
                    logger.logLine("Verbunden mit TI = " + connector.getVPNTIStatus().getConnectionStatus() + " - " + StringUtils.convertToStr(connector.getVPNTIStatus().getTimestamp()));
                    logger.logLine("Verbunden mit SIS = " + connector.getVPNSISStatus().getConnectionStatus() + " - " + StringUtils.convertToStr(connector.getVPNSISStatus().getTimestamp()));
                }
                return logger.getLogContentAsStr();
            } else {
                throw new IllegalStateException("unknown opId for the EventService and konnektor: " + konnektor.getIp());
            }
        } catch (Exception e) {
            log.error("error on executing the EventService for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public GetCardsResponse getAllCards(DefaultLogger logger) throws Exception {
        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EventService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("GetCards"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            GetCards getCards = new GetCards();
            getCards.setContext(contextType);

            if (!getCards.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                    "event webservice not valid "
                    + konnektor.getUuid()
                    + " - "
                    + konnektorServiceBean.getId()
                    + " - packagename not equal "
                    + packageName
                    + " - "
                    + getCards.getClass().getPackageName());
            }

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("EventService:getCards:getAllCards");
            GetCardsResponse getCardsResponse = (GetCardsResponse) webserviceConnector.getSoapResponse(getCards);
            timeMetric.stopAndPublish();

            return getCardsResponse;
        } catch (Exception e) {
            log.error("error on loading all cards for the konnektor: " + konnektor.getIp());
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public GetResourceInformationResponse getResourceInformation(DefaultLogger logger) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EventService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("GetResourceInformation"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            GetResourceInformation getResourceInformation = new GetResourceInformation();
            getResourceInformation.setContext(contextType);

            if (!getResourceInformation.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                    "event webservice not valid "
                    + konnektor.getUuid()
                    + " - "
                    + konnektorServiceBean.getId()
                    + " - packagename not equal "
                    + packageName
                    + " - "
                    + getResourceInformation.getClass().getPackageName()
                );
            }

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("EventService:getResourceInformation:getResourceInformation");
            GetResourceInformationResponse getResourceInformationResponse = (GetResourceInformationResponse) webserviceConnector.getSoapResponse(getResourceInformation);
            timeMetric.stopAndPublish();

            return getResourceInformationResponse;
        } catch (Exception e) {
            log.error("error on loading all resource informations for the konnektor: " + konnektor.getIp());
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
