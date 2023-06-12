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
package net.sberg.openkim.dashboard;

import net.sberg.openkim.fachdienst.Fachdienst;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.KonnektorServiceBean;
import net.sberg.openkim.konnektor.KonnektorCard;
import org.springframework.stereotype.Service;

import java.util.Iterator;

@Service
public class DashboardService {

    public Konnektor createResult(Konnektor konnektor) throws Exception {
        KonnektorMonitoringResult konnektorMonitoringResult = new KonnektorMonitoringResult();
        konnektorMonitoringResult.setIp(konnektor.getIp());
        konnektorMonitoringResult.setTiEnvironment(konnektor.getTiEnvironment());

        konnektorMonitoringResult.setKonnektorTime(konnektor.getKonnektorTime());
        konnektorMonitoringResult.setSystemTime(konnektor.getSystemTime());
        konnektorMonitoringResult.setDiffSystemKonnektorTime(konnektor.getDiffSystemKonnektorTime());

        konnektorMonitoringResult.setConnectedWithTI(konnektor.isConnectedWithTI());
        konnektorMonitoringResult.setConnectedWithSIS(konnektor.isConnectedWithSIS());

        konnektorMonitoringResult.setEccEncryptionAvailable(konnektor.isEccEncryptionAvailable());

        konnektorMonitoringResult.setVzdAlive(konnektor.getVzdLdapServerState().isAlive());
        konnektorMonitoringResult.setTlsPortAlive(konnektor.getTlsPortServerState().isAlive());

        //cards
        for (Iterator<KonnektorCard> iterator = konnektor.getCards().iterator(); iterator.hasNext(); ) {
            KonnektorCard konnektorCard = iterator.next();
            KonnektorMonitoringCardResult konnektorMonitoringCardResult = new KonnektorMonitoringCardResult();
            konnektorMonitoringCardResult.setCardTerminal(konnektorCard.getCardTerminal());
            konnektorMonitoringCardResult.setCardHandle(konnektorCard.getCardHandle());
            konnektorMonitoringCardResult.setCardSlot(konnektorCard.getCardSlot());
            konnektorMonitoringCardResult.setCardType(konnektorCard.getCardType());
            konnektorMonitoringCardResult.setIccsn(konnektorCard.getIccsn());
            konnektorMonitoringCardResult.setPinStatus(konnektorCard.getPinStatus());
            konnektorMonitoringCardResult.setPinTyp(konnektorCard.getPinTyp());
            konnektorMonitoringCardResult.setTelematikId(konnektorCard.getTelematikId());
            konnektorMonitoringResult.getCardResults().add(konnektorMonitoringCardResult);
        }

        //fachdienste
        for (Iterator<Fachdienst> iterator = konnektor.getFachdienste().iterator(); iterator.hasNext(); ) {
            Fachdienst fachdienst = iterator.next();
            KonnektorMonitoringFachdienstResult konnektorMonitoringFachdienstResult = new KonnektorMonitoringFachdienstResult();

            konnektorMonitoringFachdienstResult.setTyp(fachdienst.getTyp());
            konnektorMonitoringFachdienstResult.setErrorOnCreating(fachdienst.isErrorOnCreating());
            konnektorMonitoringFachdienstResult.setTimedOut(fachdienst.isTimedOut());

            konnektorMonitoringFachdienstResult.setSmtpDomain(fachdienst.getSmtpDomain());
            konnektorMonitoringFachdienstResult.setSmtpIpAddress(fachdienst.getSmtpIpAddress());

            konnektorMonitoringFachdienstResult.setPop3Domain(fachdienst.getPop3Domain());
            konnektorMonitoringFachdienstResult.setPop3IpAddress(fachdienst.getPop3IpAddress());

            konnektorMonitoringFachdienstResult.setAccmgrPort(fachdienst.getAccmgrPort());
            konnektorMonitoringFachdienstResult.setAccmgrDomain(fachdienst.getAccmgrDomain());
            konnektorMonitoringFachdienstResult.setAccmgrContextPath(fachdienst.getAccmgrContextPath());
            konnektorMonitoringFachdienstResult.setAccmgrIpAddress(fachdienst.getAccmgrIpAddress());
            konnektorMonitoringFachdienstResult.setAccmgrInitialized(fachdienst.isAccmgrInitialized());

            konnektorMonitoringFachdienstResult.setKasDomain(fachdienst.getKasDomain());
            konnektorMonitoringFachdienstResult.setKasIpAddress(fachdienst.getKasIpAddress());
            konnektorMonitoringFachdienstResult.setKasContextPath(fachdienst.getKasContextPath());
            konnektorMonitoringFachdienstResult.setKasPort(fachdienst.getKasPort());
            konnektorMonitoringFachdienstResult.setKasInitialized(fachdienst.isKasInitialized());

            konnektorMonitoringResult.getFachdienstResults().add(konnektorMonitoringFachdienstResult);
        }

        if (konnektor.isKonnektorServiceBeansLoaded()) {
            for (Iterator<KonnektorServiceBean> iterator = konnektor.getKonnektorServiceBeans().iterator(); iterator.hasNext(); ) {
                KonnektorServiceBean konnektorServiceBean = iterator.next();
                KonnektorMonitoringWebserviceResult konnektorMonitoringWebserviceResult = new KonnektorMonitoringWebserviceResult();
                konnektorMonitoringWebserviceResult.setType(konnektorServiceBean.getEnumKonnektorServiceBeanType());
                konnektorMonitoringWebserviceResult.setEndpoint(konnektorServiceBean.getEndpointTls());
                konnektorMonitoringWebserviceResult.setAlive(konnektorServiceBean.isAlive());

                konnektorMonitoringResult.getWebserviceResults().add(konnektorMonitoringWebserviceResult);
            }
        }

        konnektor.setKonnektorMonitoringResult(konnektorMonitoringResult);

        return konnektor;
    }
}
