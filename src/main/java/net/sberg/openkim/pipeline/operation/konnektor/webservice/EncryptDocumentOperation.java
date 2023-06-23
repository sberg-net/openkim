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
import de.gematik.ws.conn.encryptionservice.v6.EncryptDocument;
import de.gematik.ws.conn.encryptionservice.v6.EncryptDocumentResponse;
import de.gematik.ws.conn.encryptionservice.v6.KeyOnCardType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class EncryptDocumentOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(EncryptDocumentOperation.class);
    public static final String NAME = "EncryptDocument";

    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_DOCUMENT = "document";
    public static final String ENV_ENCRYPT_DOCUMENT_RESPONSE = "encryptDocumentResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) context.getEnvironmentValue(NAME, ENV_ENCRYPT_DOCUMENT_RESPONSE);
            context.getLogger().logLine("Status = " + encryptDocumentResponse.getStatus().getResult());
            context.getLogger().logLine("Dokument Verschlüsselt Plain = " + new String(encryptDocumentResponse.getDocument().getBase64Data().getValue()));
            context.getLogger().logLine("Dokument Verschlüsselt Base64 = " + Base64.encodeBase64String(encryptDocumentResponse.getDocument().getBase64Data().getValue()));
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

            EncryptDocument encryptDocument = new EncryptDocument();
            encryptDocument.setContext(contextType);

            if (!encryptDocument.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "encryption webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + encryptDocument.getClass().getPackageName()
                ));
            }
            else {
                EncryptDocument.RecipientKeys recipientKeys = new EncryptDocument.RecipientKeys();
                KeyOnCardType keyOnCardType = new KeyOnCardType();
                keyOnCardType.setCardHandle((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE));
                recipientKeys.setCertificateOnCard(keyOnCardType);
                encryptDocument.setRecipientKeys(recipientKeys);

                Base64Data base64Data = new Base64Data();
                base64Data.setValue(Base64.encodeBase64(((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_DOCUMENT)).getBytes()));
                DocumentType documentType = new DocumentType();
                documentType.setBase64Data(base64Data);
                encryptDocument.setDocument(documentType);

                EncryptDocument.OptionalInputs optionalInputs = new EncryptDocument.OptionalInputs();
                optionalInputs.setEncryptionType("urn:ietf:rfc:5652");
                encryptDocument.setOptionalInputs(optionalInputs);

                timeMetric = metricFactory.timer(NAME);
                EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) webserviceConnector.getSoapResponse(encryptDocument);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_ENCRYPT_DOCUMENT_RESPONSE, encryptDocumentResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the EncryptDocumentOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
