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

import de.gematik.ws.conn.cardservice.v8_1_2.PinStatusEnum;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.KonnektorCard;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class GetSignCardHandleOperation implements IPipelineOperation {

    private static final Logger log = LoggerFactory.getLogger(GetSignCardHandleOperation.class);
    public static final String NAME = "GetSignCardHandleOperation";

    public static final String ENV_RESULT_CARD_HANDLE = "resultCardHandle";
    public static final String ENV_RESULT_CARD_HANDLE_FOUND = "resultCardHandleFound";

    private KonnektorLoadAllCardInformationOperation konnektorLoadAllCardInformationOperation;

    public void setKonnektorLoadAllCardInformationOperation(KonnektorLoadAllCardInformationOperation konnektorLoadAllCardInformationOperation) {
        this.konnektorLoadAllCardInformationOperation = konnektorLoadAllCardInformationOperation;
    }

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

            //load all cards
            log.info("load all cards - start");
            logger.logLine("load all cards - start");

            konnektorLoadAllCardInformationOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("load all cards - finished");
                    logger.logLine("load all cards - finished");

                    List<KonnektorCard> cards = konnektor.getCards();
                    KonnektorCard selectedCard = null;
                    for (Iterator<KonnektorCard> iterator = cards.iterator(); iterator.hasNext(); ) {
                        KonnektorCard konnektorCard = iterator.next();

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
                            log.info("konnektor card is verified for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                            logger.logLine("konnektor card is verified for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                            break;
                        }
                    }

                    defaultPipelineOperationContext.setEnvironmentValue(GetSignCardHandleOperation.NAME, GetSignCardHandleOperation.ENV_RESULT_CARD_HANDLE_FOUND, selectedCard != null);
                    defaultPipelineOperationContext.setEnvironmentValue(GetSignCardHandleOperation.NAME, GetSignCardHandleOperation.ENV_RESULT_CARD_HANDLE, selectedCard);

                    if (selectedCard == null) {
                        logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X010);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_X010 + " - " + EnumErrorCode.CODE_X010.getHrText());
                    }
                },
                (context, e) -> {
                    defaultPipelineOperationContext.setEnvironmentValue(konnektorLoadAllCardInformationOperation.getName(), ENV_EXCEPTION, e);
                }
            );

            timeMetric.stopAndPublish();
            if (hasError(defaultPipelineOperationContext, new String[] {NAME,konnektorLoadAllCardInformationOperation.getName()})) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
            }
            else {
                okConsumer.accept(defaultPipelineOperationContext);
            }

        } catch (Exception e) {
            log.error("error on executing the GetSignCardHandleOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }

}
