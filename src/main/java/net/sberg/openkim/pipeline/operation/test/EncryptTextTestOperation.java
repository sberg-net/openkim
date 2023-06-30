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
package net.sberg.openkim.pipeline.operation.test;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.KonnektorCard;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.EncryptDocumentOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@PipelineOperation
@Component
public class EncryptTextTestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(EncryptTextTestOperation.class);
    public static final String NAME = "EncryptTextTest";

    public static final String ENV_ICSSN = "icssn";
    public static final String ENV_TEXT = "text";

    @Autowired
    private EncryptDocumentOperation encryptDocumentOperation;

    @Override
    public boolean isTestable() {
        return true;
    }

    @Override
    public String getHrText() {
        return "Verschlüsseln eines Textes";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            context.getLogger().logLine("Der Text wurde erfolgreich verschlüsselt");
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

            if (konnektor.getCards().isEmpty()) {
                logger.logLine("Keine Karten für den Konnektor geladen");
                throw new IllegalStateException("no cards available");
            }

            String icssn = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ICSSN);
            String text = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_TEXT);

            List filteredCards = konnektor.getCards().stream().filter(konnektorCard -> konnektorCard.getIccsn().equals(icssn)).collect(Collectors.toList());
            if (filteredCards.isEmpty()) {
                logger.logLine("Keine Karte mit der ICSSN "+icssn+" für den Konnektor geladen");
                throw new IllegalStateException("no card with icssn: "+icssn+" available");
            }
            KonnektorCard card = (KonnektorCard) filteredCards.get(0);

            defaultPipelineOperationContext.setEnvironmentValue(EncryptDocumentOperation.NAME, EncryptDocumentOperation.ENV_CARDHANDLE, card.getCardHandle());
            defaultPipelineOperationContext.setEnvironmentValue(EncryptDocumentOperation.NAME, EncryptDocumentOperation.ENV_DOCUMENT, text);

            encryptDocumentOperation.execute(
                defaultPipelineOperationContext,
                    encryptDocumentOperation.getDefaultOkConsumer(),
                    encryptDocumentOperation.getDefaultFailConsumer()
            );

            if (encryptDocumentOperation.hasError(defaultPipelineOperationContext, EncryptDocumentOperation.NAME)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("error on encrypting text"));
            }
            else {
                okConsumer.accept(defaultPipelineOperationContext);
            }

            timeMetric.stopAndPublish();
        } catch (Exception e) {
            log.error("error on executing the EncryptTextTestOperation", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
