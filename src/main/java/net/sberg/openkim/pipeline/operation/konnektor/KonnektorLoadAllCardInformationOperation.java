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
package net.sberg.openkim.pipeline.operation.konnektor;

import de.gematik.ws.conn.cardservice.v8.CardInfoType;
import de.gematik.ws.conn.cardservice.v8.GetPinStatusResponse;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.X509DataInfoListType;
import de.gematik.ws.conn.eventservice.v7.GetCardsResponse;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.EnumX509ErrorCode;
import net.sberg.openkim.common.x509.TelematikIdResult;
import net.sberg.openkim.common.x509.X509CertificateUtils;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.GetCardsOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.GetPinStatusOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.ReadCardCertificateOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.VerifyPinOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class KonnektorLoadAllCardInformationOperation implements IPipelineOperation {

    private static final Logger log = LoggerFactory.getLogger(KonnektorLoadAllCardInformationOperation.class);
    public static final String NAME = "KonnektorLoadAllCardInformation";

    @Autowired
    private GetCardsOperation getCardsOperation;
    @Autowired
    private ReadCardCertificateOperation readCardCertificateOperation;
    @Autowired
    private GetPinStatusOperation getPinStatusOperation;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {

        TimeMetric timeMetric = null;
        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            KonnektorServiceBean cardServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CardService, true);

            getCardsOperation.execute(
                defaultPipelineOperationContext,
                getCardsOperationContext -> {
                    try {
                        GetCardsResponse getCardsResponse = (GetCardsResponse) getCardsOperationContext.getEnvironmentValue(getCardsOperation.getName(), GetCardsOperation.ENV_GET_CARDS_RESPONSE);

                        if (!getCardsResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                            defaultPipelineOperationContext.setEnvironmentValue(getCardsOperation.getName(), ENV_EXCEPTION, new IllegalStateException("request getAllCards status not OK for the konnektor: " + konnektor.getIp()));
                        }
                        else {
                            logger.logLine(getCardsResponse.getCards().getCard().size() + " Karten geladen: " + konnektor.getIp());

                            List<KonnektorCard> cards = new ArrayList<>();
                            for (Iterator<CardInfoType> iterator = getCardsResponse.getCards().getCard().iterator(); iterator.hasNext(); ) {
                                CardInfoType cardInfoType = iterator.next();
                                KonnektorCard konnektorCard = new KonnektorCard();
                                konnektorCard.setKonnId(konnektorCard.getUuid());
                                konnektorCard.setWsId(cardServiceBean.getId());
                                konnektorCard.setVerifyPinOpId(IPipelineOperation.BUILTIN_VENDOR + "." + VerifyPinOperation.NAME);
                                konnektorCard.setCardTerminal(cardInfoType.getCtId());
                                konnektorCard.setCardSlot(cardInfoType.getSlotId().toString());
                                konnektorCard.setCardHandle(cardInfoType.getCardHandle());
                                konnektorCard.setCardType(cardInfoType.getCardType().value());
                                konnektorCard.setPinTyp(KonnektorWebserviceUtils.getPinType(konnektorCard.getCardType()));
                                konnektorCard.setIccsn(cardInfoType.getIccsn());
                                konnektorCard.setExpiredAt(StringUtils.convertToStr(cardInfoType.getCertificateExpirationDate()));

                                //request telematikid
                                if (KonnektorWebserviceUtils.interestingCardTypes.contains(cardInfoType.getCardType().value())) {
                                    defaultPipelineOperationContext.setEnvironmentValue(ReadCardCertificateOperation.NAME, ReadCardCertificateOperation.ENV_CARDHANDLE, cardInfoType.getCardHandle());
                                    defaultPipelineOperationContext.setEnvironmentValue(ReadCardCertificateOperation.NAME, ReadCardCertificateOperation.ENV_CERT_REFS, KonnektorWebserviceUtils.CERT_REF_ENC);
                                    readCardCertificateOperation.execute(
                                        defaultPipelineOperationContext,
                                        readCardCertificateOperationContext -> {
                                            try {
                                                ReadCardCertificateResponse readCardCertificateResponse = (ReadCardCertificateResponse) readCardCertificateOperationContext.getEnvironmentValue(readCardCertificateOperation.getName(), ReadCardCertificateOperation.ENV_READ_CARD_CERT_RESPONSE);
                                                if (!readCardCertificateResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                                                    defaultPipelineOperationContext.setEnvironmentValue(
                                                        readCardCertificateOperation.getName(),
                                                        ENV_EXCEPTION,
                                                        new IllegalStateException(
                                                            "request getCertificate status not OK for the konnektor: "
                                                                    + konnektor.getIp()
                                                                    + " and the cardHandle: "
                                                                    + cardInfoType.getCardHandle()
                                                                    + " and the certRef: "
                                                                    + KonnektorWebserviceUtils.CERT_REF_ENC
                                                        )
                                                    );
                                                }
                                                else {
                                                    TelematikIdResult telematikIdResult = new TelematikIdResult();
                                                    telematikIdResult.setIcssn(cardInfoType.getIccsn());
                                                    telematikIdResult = extractTelematikId(readCardCertificateResponse, telematikIdResult);
                                                    if (telematikIdResult.getErrorCode().equals(EnumX509ErrorCode.OK)) {
                                                        konnektorCard.setTelematikId(telematikIdResult.getTelematikId());
                                                    }
                                                }
                                            }
                                            catch (Exception e) {
                                                defaultPipelineOperationContext.setEnvironmentValue(readCardCertificateOperation.getName(), ENV_EXCEPTION, e);
                                            }
                                        },
                                        (context, e) -> {
                                            defaultPipelineOperationContext.setEnvironmentValue(readCardCertificateOperation.getName(), ENV_EXCEPTION, e);
                                        }
                                    );
                                }

                                //request pinstatus
                                if (KonnektorWebserviceUtils.interestingCardTypes.contains(cardInfoType.getCardType().value())) {
                                    defaultPipelineOperationContext.setEnvironmentValue(GetPinStatusOperation.NAME, GetPinStatusOperation.ENV_CARDHANDLE, cardInfoType.getCardHandle());
                                    defaultPipelineOperationContext.setEnvironmentValue(GetPinStatusOperation.NAME, GetPinStatusOperation.ENV_PINTYP, KonnektorWebserviceUtils.getPinType(cardInfoType.getCardType().value()));
                                    getPinStatusOperation.execute(
                                        defaultPipelineOperationContext,
                                        getPinStatusOperationContext -> {
                                            try {
                                                GetPinStatusResponse getPinStatusResponse = (GetPinStatusResponse) getPinStatusOperationContext.getEnvironmentValue(getPinStatusOperation.getName(), GetPinStatusOperation.ENV_PIN_STATUS_RESPONSE);
                                                if (!getPinStatusResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                                                    defaultPipelineOperationContext.setEnvironmentValue(
                                                        getPinStatusOperation.getName(),
                                                        ENV_EXCEPTION,
                                                        new IllegalStateException(
                                                            "request getPinStatus status not OK for the konnektor: "
                                                                + konnektor.getIp()
                                                                + " and the cardHandle: "
                                                                + cardInfoType.getCardHandle()
                                                                + " and the pinType: "
                                                                + KonnektorWebserviceUtils.getPinType(cardInfoType.getCardType().value())
                                                        )
                                                    );
                                                }
                                                else {
                                                    konnektorCard.setPinStatus(getPinStatusResponse.getPinStatus().value());
                                                }
                                            } catch (Exception e) {
                                                defaultPipelineOperationContext.setEnvironmentValue(getPinStatusOperation.getName(), ENV_EXCEPTION, e);
                                            }
                                       },
                                       (context, e) -> {
                                           defaultPipelineOperationContext.setEnvironmentValue(getPinStatusOperation.getName(), ENV_EXCEPTION, e);
                                       }
                                    );
                                }
                                cards.add(konnektorCard);
                            }

                            konnektor.getCards().clear();
                            konnektor.getCards().addAll(cards);
                        }
                    } catch (Exception e) {
                        defaultPipelineOperationContext.setEnvironmentValue(getCardsOperation.getName(), ENV_EXCEPTION, e);
                    }
                },
                (context, e) -> {
                    defaultPipelineOperationContext.setEnvironmentValue(getCardsOperation.getName(), ENV_EXCEPTION, e);
                }
            );

            timeMetric.stopAndPublish();

            if (hasError(defaultPipelineOperationContext, new String[] {NAME,getCardsOperation.getName(), readCardCertificateOperation.getName(),getPinStatusOperation.getName()})) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
            }
            else {
                okConsumer.accept(defaultPipelineOperationContext);
            }

        } catch (Exception e) {
            log.error("error on executing the KonnektorLoadAllCardInformationOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
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
