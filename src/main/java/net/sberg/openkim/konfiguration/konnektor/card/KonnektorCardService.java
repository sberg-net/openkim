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

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservice.v8_1_2.GetPinStatusResponse;
import de.gematik.ws.conn.certificateservice.v6_0_1.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.X509DataInfoListType;
import de.gematik.ws.conn.eventservice.v7_2_0.GetCardsResponse;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.x509.EnumX509ErrorCode;
import net.sberg.openkim.common.x509.TelematikIdResult;
import net.sberg.openkim.common.x509.X509CertificateUtils;
import net.sberg.openkim.konfiguration.konnektor.EnumKonnektorServiceBeanType;
import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import net.sberg.openkim.konfiguration.konnektor.KonnektorServiceBean;
import net.sberg.openkim.konfiguration.konnektor.KonnektorWebserviceUtils;
import net.sberg.openkim.konfiguration.konnektor.webservice.CardService;
import net.sberg.openkim.konfiguration.konnektor.webservice.CertificateService;
import net.sberg.openkim.konfiguration.konnektor.webservice.EventService;
import net.sberg.openkim.log.DefaultLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class KonnektorCardService {

    private static final Logger log = LoggerFactory.getLogger(KonnektorCardService.class);

    @Autowired
    private EventService eventService;
    @Autowired
    private CardService cardService;
    @Autowired
    private CertificateService certificateService;

    public Konnektor loadAllCards(DefaultLogger logger) throws Exception {

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean cardServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardService, true);

            GetCardsResponse getCardsResponse = eventService.getAllCards(logger);

            if (!getCardsResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                throw new IllegalStateException("request getAllCards status not OK for the konnektor: " + konnektor.getIp());
            }

            logger.logLine(getCardsResponse.getCards().getCard().size() + " Karten geladen: " + konnektor.getIp());

            List<KonnektorCard> cards = new ArrayList<>();
            for (Iterator<CardInfoType> iterator = getCardsResponse.getCards().getCard().iterator(); iterator.hasNext(); ) {
                CardInfoType cardInfoType = iterator.next();
                KonnektorCard konnektorCard = new KonnektorCard();
                konnektorCard.setKonnId(konnektorCard.getUuid());
                konnektorCard.setWsId(cardServiceBean.getId());
                konnektorCard.setVerifyPinOpId(CardService.OP_VERIFY_PIN);
                konnektorCard.setCardTerminal(cardInfoType.getCtId());
                konnektorCard.setCardSlot(cardInfoType.getSlotId().toString());
                konnektorCard.setCardHandle(cardInfoType.getCardHandle());
                konnektorCard.setCardType(cardInfoType.getCardType().value());
                konnektorCard.setPinTyp(KonnektorWebserviceUtils.getPinType(konnektorCard.getCardType()));
                konnektorCard.setIccsn(cardInfoType.getIccsn());
                konnektorCard.setExpiredAt(StringUtils.convertToStr(cardInfoType.getCertificateExpirationDate()));

                //request telematikid
                if (KonnektorWebserviceUtils.interestingCardTypes.contains(cardInfoType.getCardType().value())) {
                    try {
                        ReadCardCertificateResponse readCardCertificateResponse = certificateService.getCertificate(
                            logger,
                            KonnektorWebserviceUtils.CERT_REF_ENC,
                            cardInfoType.getCardHandle()
                        );
                        if (!readCardCertificateResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                            throw new IllegalStateException(
                                "request getCertificate status not OK for the konnektor: "
                                + konnektor.getIp()
                                + " and the cardHandle: "
                                + cardInfoType.getCardHandle()
                                + " and the certRef: "
                                + KonnektorWebserviceUtils.CERT_REF_ENC
                            );
                        }
                        TelematikIdResult telematikIdResult = new TelematikIdResult();
                        telematikIdResult.setIcssn(cardInfoType.getIccsn());
                        telematikIdResult = extractTelematikId(readCardCertificateResponse, telematikIdResult);
                        if (telematikIdResult.getErrorCode().equals(EnumX509ErrorCode.OK)) {
                            konnektorCard.setTelematikId(telematikIdResult.getTelematikId());
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(
                            "request getCertificate gives an error for the konnektor: "
                            + konnektor.getIp()
                            + " and the cardHandle: "
                            + cardInfoType.getCardHandle()
                            + " and the certRef: "
                            + KonnektorWebserviceUtils.CERT_REF_ENC,
                            e
                        );
                    }
                }

                //request pinstatus
                if (KonnektorWebserviceUtils.interestingCardTypes.contains(cardInfoType.getCardType().value())) {
                    GetPinStatusResponse getPinStatusResponse = cardService.getPinStatus(
                        logger,
                        KonnektorWebserviceUtils.getPinType(cardInfoType.getCardType().value()),
                        cardInfoType.getCardHandle()
                    );

                    if (!getPinStatusResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                        throw new IllegalStateException(
                            "request getPinStatus status not OK for the konnektor: "
                            + konnektor.getIp()
                            + " and the cardHandle: "
                            + cardInfoType.getCardHandle()
                            + " and the pinType: "
                            + KonnektorWebserviceUtils.getPinType(cardInfoType.getCardType().value())
                        );
                    }

                    konnektorCard.setPinStatus(getPinStatusResponse.getPinStatus().value());
                }

                cards.add(konnektorCard);
            }

            konnektor.getCards().clear();
            konnektor.getCards().addAll(cards);
            return konnektor;
        } catch (Exception e) {
            log.error("error on loading all cards for the konnektor: " + konnektor.getIp(), e);
            throw e;
        }
    }

    private TelematikIdResult extractTelematikId(ReadCardCertificateResponse readCardCertificateResponse, TelematikIdResult telematikIdResult) {
        for (Iterator<X509DataInfoListType.X509DataInfo> iterator = readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().iterator(); iterator.hasNext(); ) {
            X509DataInfoListType.X509DataInfo x509DataInfo = iterator.next();
            telematikIdResult = X509CertificateUtils.extractTelematikId(x509DataInfo.getX509Data().getX509Certificate(), telematikIdResult);
        }
        return telematikIdResult;
    }
}
