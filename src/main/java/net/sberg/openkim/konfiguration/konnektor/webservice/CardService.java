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

import de.gematik.ws.conn.cardservice.v8_1_2.GetPinStatus;
import de.gematik.ws.conn.cardservice.v8_1_2.GetPinStatusResponse;
import de.gematik.ws.conn.cardservice.v8_1_2.VerifyPin;
import de.gematik.ws.conn.cardservicecommon.v2.PinResponseType;
import de.gematik.ws.conn.connectorcontext.ContextType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBElement;

@Service
public class CardService {

    private static final Logger log = LoggerFactory.getLogger(CardService.class);

    private static final String OP_GET_PINSTATUS = "GetPinStatus";
    public static final String OP_VERIFY_PIN = "VerifyPin";

    public String execute(
        DefaultLogger logger,
        CardWebserviceBean cardWebserviceBean
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            if (cardWebserviceBean.getOpId().equals(OP_GET_PINSTATUS)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("GetPinStatus"),
                    logger
                );

                GetPinStatus getPinStatus = new GetPinStatus();
                if (!getPinStatus.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "card webservice not valid "
                        + cardWebserviceBean.getKonnId()
                        + " - "
                        + cardWebserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + getPinStatus.getClass().getPackageName()
                    );
                }

                ContextType contextType = new ContextType();
                contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
                contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
                contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
                getPinStatus.setContext(contextType);

                getPinStatus.setPinTyp(cardWebserviceBean.getPinTyp());
                getPinStatus.setCardHandle(cardWebserviceBean.getCardHandle());

                timeMetric = metricFactory.timer("CardService:getPinStatus:execute");
                GetPinStatusResponse getPinStatusResponse = (GetPinStatusResponse) webserviceConnector.getSoapResponse(getPinStatus);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + getPinStatusResponse.getStatus().getResult());
                logger.logLine("PinStatus = " + getPinStatusResponse.getPinStatus());
                logger.logLine("LeftTries = " + getPinStatusResponse.getLeftTries());
                return logger.getLogContentAsStr();
            } else if (cardWebserviceBean.getOpId().equals(OP_VERIFY_PIN)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("VerifyPin"),
                    logger
                );

                VerifyPin verifyPin = new VerifyPin();
                if (!verifyPin.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "card webservice not valid "
                        + cardWebserviceBean.getKonnId()
                        + " - " + cardWebserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + verifyPin.getClass().getPackageName()
                    );
                }

                ContextType contextType = new ContextType();
                contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
                contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
                contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
                verifyPin.setContext(contextType);

                verifyPin.setPinTyp(cardWebserviceBean.getPinTyp());
                verifyPin.setCardHandle(cardWebserviceBean.getCardHandle());

                timeMetric = metricFactory.timer("CardService:verifyPin:execute");
                PinResponseType pinResponseType = ((JAXBElement<PinResponseType>) webserviceConnector.getSoapResponse(verifyPin)).getValue();
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + pinResponseType.getStatus().getResult());
                logger.logLine("PinResult = " + pinResponseType.getPinResult());
                logger.logLine("LeftTries = " + pinResponseType.getLeftTries());
                return logger.getLogContentAsStr();
            } else {
                throw new IllegalStateException("unknown opId for the CardService and konnektor: " + konnektor.getIp());
            }
        } catch (Exception e) {
            log.error("error on executing the CardService for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public GetPinStatusResponse getPinStatus(
        DefaultLogger logger,
        String pinType,
        String cardHandle) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("GetPinStatus"),
                logger
            );

            GetPinStatus getPinStatus = new GetPinStatus();
            if (!getPinStatus.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                    "card webservice not valid "
                    + konnektor.getUuid()
                    + " - "
                    + konnektorServiceBean.getId()
                    + " - packagename not equal "
                    + packageName
                    + " - "
                    + getPinStatus.getClass().getPackageName()
                );
            }

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());
            getPinStatus.setContext(contextType);

            getPinStatus.setPinTyp(pinType);
            getPinStatus.setCardHandle(cardHandle);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("CardService:getPinStatus:getPinStatus");
            GetPinStatusResponse getPinStatusResponse = (GetPinStatusResponse) webserviceConnector.getSoapResponse(getPinStatus);
            timeMetric.stopAndPublish();

            return getPinStatusResponse;
        } catch (Exception e) {
            log.error("error on loading the pinStatus for the konnektor: " + konnektor.getIp() + " and the cardHandle: " + cardHandle + " and the pinType: " + pinType);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
