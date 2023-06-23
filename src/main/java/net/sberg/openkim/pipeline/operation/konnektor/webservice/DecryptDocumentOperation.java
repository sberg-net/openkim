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
package net.sberg.openkim.pipeline.operation.konnektor.webservice;

import de.gematik.ws.conn.connectorcommon.v5.DocumentType;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.encryptionservice.v6.DecryptDocument;
import de.gematik.ws.conn.encryptionservice.v6.DecryptDocumentResponse;
import de.gematik.ws.conn.encryptionservice.v6.KeyOnCardType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class DecryptDocumentOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(DecryptDocumentOperation.class);
    public static final String NAME = "DecryptDocument";

    public static final String ENV_DOCUMENT = "document";
    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_DECRYPT_DOCUMENT_RESPONSE = "decryptDocumentResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            DecryptDocumentResponse decryptDocumentResponse = (DecryptDocumentResponse) context.getEnvironmentValue(NAME, ENV_DECRYPT_DOCUMENT_RESPONSE);
            context.getLogger().logLine("Status = " + decryptDocumentResponse.getStatus().getResult());
            context.getLogger().logLine("Dokument = " + new String(decryptDocumentResponse.getDocument().getBase64Data().getValue()));
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EncryptionService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(NAME),
                    logger
            );

            byte[] document = (byte[])defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_DOCUMENT);

            DecryptDocument decryptDocument = new DecryptDocument();
            decryptDocument.setContext(contextType);

            if (!decryptDocument.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "encryption webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + decryptDocument.getClass().getPackageName()
                ));
            }
            else {
                KeyOnCardType keyOnCardType = new KeyOnCardType();
                keyOnCardType.setCardHandle((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE));
                decryptDocument.setPrivateKeyOnCard(keyOnCardType);

                Base64Data base64Data = new Base64Data();
                base64Data.setValue(document);
                DocumentType documentType = new DocumentType();
                documentType.setBase64Data(base64Data);
                decryptDocument.setDocument(documentType);

                timeMetric = metricFactory.timer(NAME);
                DecryptDocumentResponse decryptDocumentResponse = (DecryptDocumentResponse) webserviceConnector.getSoapResponse(decryptDocument);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_DECRYPT_DOCUMENT_RESPONSE, decryptDocumentResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the DecryptDocumentOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
