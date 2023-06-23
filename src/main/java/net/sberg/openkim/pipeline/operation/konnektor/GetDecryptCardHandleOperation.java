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

import de.gematik.ws.conn.cardservice.v8.PinStatusEnum;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.certificateservice.v6.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.X509DataInfoListType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.IssuerAndSerial;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.KonnektorCard;
import net.sberg.openkim.konnektor.KonnektorWebserviceUtils;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.ReadCardCertificateOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class GetDecryptCardHandleOperation implements IPipelineOperation {

    private static final Logger log = LoggerFactory.getLogger(GetDecryptCardHandleOperation.class);
    public static final String NAME = "GetDecryptCardHandleOperation";

    public static final String ENV_CONTENT_INFO = "contentInfo";
    public static final String ENV_USER_MAIL_ADDRESS = "userMailAddress";
    public static final String ENV_RESULT_CARD_HANDLE = "resultCardHandle";
    public static final String ENV_RESULT_CARD_HANDLE_FOUND = "resultCardHandleFound";

    @Autowired
    private ReadCardCertificateOperation readCardCertificateOperation;
    @Autowired
    private KonnektorLoadAllCardInformationOperation konnektorLoadAllCardInformationOperation;

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

            ContentInfo contentInfo = (ContentInfo) defaultPipelineOperationContext.getEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_CONTENT_INFO);
            String userMailAddress = (String) defaultPipelineOperationContext.getEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_USER_MAIL_ADDRESS);

            List<IssuerAndSerial> certIssuerAndSerialNumbers = CMSUtils.getCertIssuerAndSerialNumber(contentInfo, userMailAddress);
            if (certIssuerAndSerialNumbers.isEmpty()) {
                log.error("error on getDecryptCardHandle - CertIssuerAndSerialNumber in contentinfo not available for mailaddress: " + userMailAddress);
                logger.logLine("error on getDecryptCardHandle - CertIssuerAndSerialNumber in contentinfo not available for mailaddress: " + userMailAddress);
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X022);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X022 + " - " + EnumErrorCode.CODE_X022.getHrText());
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("error on getDecryptCardHandle - CertIssuerAndSerialNumber in contentinfo not available for mailaddress: " + userMailAddress));
            }
            else {
                //load all cards
                log.info("load all cards - start");
                logger.logLine("load all cards - start");

                konnektorLoadAllCardInformationOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("load all cards - finished");
                        logger.logLine("load all cards - finished");
                    },
                    (context, e) -> {
                        defaultPipelineOperationContext.setEnvironmentValue(konnektorLoadAllCardInformationOperation.getName(), ENV_EXCEPTION, e);
                    }
                );

                List<KonnektorCard> cards = konnektor.getCards();
                KonnektorCard selectedCard = null;
                for (Iterator<KonnektorCard> iterator = cards.iterator(); iterator.hasNext(); ) {
                    KonnektorCard konnektorCard = iterator.next();

                    AtomicInteger candidate = new AtomicInteger();
                    //Verschlüsselungs-Zertifikate laden für die Karte
                    if (KonnektorWebserviceUtils.interestingCardTypes.contains(konnektorCard.getCardType())) {
                        defaultPipelineOperationContext.setEnvironmentValue(readCardCertificateOperation.getName(), ReadCardCertificateOperation.ENV_CARDHANDLE, konnektorCard.getCardHandle());
                        defaultPipelineOperationContext.setEnvironmentValue(readCardCertificateOperation.getName(), ReadCardCertificateOperation.ENV_CERT_REFS, List.of(KonnektorWebserviceUtils.CERT_REF_ENC));
                        readCardCertificateOperation.execute(
                                defaultPipelineOperationContext,
                                context1 -> {
                                    try {
                                        ReadCardCertificateResponse readCardCertificateResponse = (ReadCardCertificateResponse) defaultPipelineOperationContext.getEnvironmentValue(readCardCertificateOperation.getName(), ReadCardCertificateOperation.ENV_READ_CARD_CERT_RESPONSE);
                                        if (!readCardCertificateResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                                            defaultPipelineOperationContext.setEnvironmentValue(
                                                readCardCertificateOperation.getName(),
                                                ENV_EXCEPTION,
                                                new IllegalStateException(
                                                    "request getCertificate status not OK for the konnektor: "
                                                    + konnektor.getIp()
                                                    + " and the cardHandle: "
                                                    + konnektorCard.getCardHandle()
                                                    + " and the certRef: "
                                                    + KonnektorWebserviceUtils.CERT_REF_ENC
                                                )
                                            );
                                        } else {
                                            for (Iterator<X509DataInfoListType.X509DataInfo> konnektorCardIterator = readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().iterator(); konnektorCardIterator.hasNext(); ) {
                                                X509DataInfoListType.X509DataInfo x509DataInfo = konnektorCardIterator.next();
                                                IssuerAndSerial issuerAndSerial = new IssuerAndSerial();
                                                issuerAndSerial.setSerialNumber(x509DataInfo.getX509Data().getX509IssuerSerial().getX509SerialNumber());
                                                issuerAndSerial.setIssuer(x509DataInfo.getX509Data().getX509IssuerSerial().getX509IssuerName());
                                                if (certIssuerAndSerialNumbers.contains(issuerAndSerial)) {
                                                    candidate.incrementAndGet();
                                                    break;
                                                }
                                            }
                                            if (candidate.get() == 0) {
                                                logger.logLine("card is a bad candidate: " + konnektorCard.getCardHandle());
                                            } else {
                                                logger.logLine("card is a goot candidate: " + konnektorCard.getCardHandle());
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());
                                        defaultPipelineOperationContext.setEnvironmentValue(ReadCardCertificateOperation.NAME, ENV_EXCEPTION, e);
                                    }
                                },
                                (context1, e) -> {
                                    defaultPipelineOperationContext.setEnvironmentValue(ReadCardCertificateOperation.NAME, ENV_EXCEPTION, e);
                                }
                        );

                        if (candidate.get() == 0) {
                            continue;
                        }

                        log.info("analyze card: " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType() + " - " + konnektorCard.getPinStatus() + " - " + konnektorCard.getTelematikId());
                        logger.logLine("analyze card: " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType() + " - " + konnektorCard.getPinStatus() + " - " + konnektorCard.getTelematikId());

                        if (!konnektorCard.getCardType().equals(CardTypeType.SMC_B.value())) {
                            log.info("konnektor card is not a smcb: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType());
                            logger.logLine("konnektor card is not a smcb: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType());
                            continue;
                        }
                        if (konnektorCard.getPinStatus().equals(PinStatusEnum.BLOCKED.value())) {
                            log.info("konnektor card is blocked for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                            logger.logLine("konnektor card is blocked for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                            continue;
                        }
                        if (konnektorCard.getPinStatus().equals(PinStatusEnum.VERIFIABLE.value())) {
                            log.info("konnektor card is verifiable for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                            logger.logLine("konnektor card is verifiable for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                            continue;
                        }
                        if (konnektorCard.getPinStatus().equals(PinStatusEnum.VERIFIED.value())) {
                            selectedCard = konnektorCard;
                        }
                    }
                }

                timeMetric.stopAndPublish();

                defaultPipelineOperationContext.setEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_RESULT_CARD_HANDLE_FOUND, selectedCard != null);
                defaultPipelineOperationContext.setEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_RESULT_CARD_HANDLE, selectedCard);

                if (selectedCard == null) {
                    logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4009);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_4009 + " - " + EnumErrorCode.CODE_4009.getHrText());
                    logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X021);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_X021 + " - " + EnumErrorCode.CODE_X021.getHrText());

                    failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("nor card found"));
                }
                else {
                    if (hasError(defaultPipelineOperationContext, new String[] {NAME,konnektorLoadAllCardInformationOperation.getName(),readCardCertificateOperation.getName()})) {
                        failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
                    }
                    else {
                        okConsumer.accept(defaultPipelineOperationContext);
                    }
                }
            }
        } catch (Exception e) {
            log.error("error on executing the GetDecryptCardHandleOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X021);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X021 + " - " + EnumErrorCode.CODE_X021.getHrText());
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }

}
