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
package net.sberg.openkim.konfiguration.konnektor.card;

import de.gematik.ws.conn.connectorcommon.Connector;
import de.gematik.ws.conn.eventservice.v7_2_0.GetResourceInformationResponse;
import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import net.sberg.openkim.konfiguration.konnektor.KonnektorWebserviceUtils;
import net.sberg.openkim.konfiguration.konnektor.webservice.EventService;
import net.sberg.openkim.log.DefaultLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KonnektorConnectionInfoService {

    private static final Logger log = LoggerFactory.getLogger(KonnektorConnectionInfoService.class);

    @Autowired
    private EventService eventService;

    public Konnektor loadConnectionInfo(DefaultLogger logger) throws Exception {

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            GetResourceInformationResponse getResourceInformationResponse = eventService.getResourceInformation(logger);

            if (!getResourceInformationResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                throw new IllegalStateException("request getResourceInformations status not OK for the konnektor: " + konnektor.getIp());
            }

            Connector connector = getResourceInformationResponse.getConnector();
            konnektor.setConnectedWithTI(connector.getVPNTIStatus().getConnectionStatus().equals("Online"));
            konnektor.setConnectedWithSIS(connector.getVPNSISStatus().getConnectionStatus().equals("Online"));

            logger.logLine(konnektor.getIp() + ": mit TI verbunden - " + connector.getVPNTIStatus().getConnectionStatus() + ", mit SIS verbunden - " + connector.getVPNSISStatus().getConnectionStatus());

            return konnektor;
        } catch (Exception e) {
            log.error("error on loading all resource informations for the konnektor: " + konnektor.getIp());
            throw e;
        }
    }
}
