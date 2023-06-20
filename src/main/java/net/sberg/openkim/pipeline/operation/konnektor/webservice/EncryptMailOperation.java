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

import de.gematik.ws.conn.connectorcommon.DocumentType;
import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.encryptionservice.v6_1_1.EncryptDocument;
import de.gematik.ws.conn.encryptionservice.v6_1_1.EncryptDocumentResponse;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import oasis.names.tc.dss._1_0.core.schema.AnyType;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import oasis.names.tc.dss._1_0.core.schema.PropertiesType;
import oasis.names.tc.dss._1_0.core.schema.Property;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class EncryptMailOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(EncryptMailOperation.class);
    public static final String NAME = "EncryptMail";

    public static final String ENV_SIGNED_MAIL = "signedMail";
    public static final String ENV_VZD_CERTS = "vzdCerts";
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
                konnektorServiceBean.createSoapAction("EncryptDocument"),
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
                List<X509CertificateResult> x509CertificateResults = (List<X509CertificateResult>) defaultPipelineOperationContext.getEnvironmentValue(EncryptMailOperation.NAME, EncryptMailOperation.ENV_VZD_CERTS);
                byte[] cmsAttr = CMSUtils.buildCmsAttributeRecipientEmails(x509CertificateResults, konnektor);
                CMSAttribute cmsAttribute = new CMSAttribute();
                cmsAttribute.setContent(Base64.encodeBase64String(cmsAttr));

                //recipientsEmailsAttribute anytype
                AnyType recipientsEmailsAttribute = new AnyType();
                recipientsEmailsAttribute.getAny().add(cmsAttribute);

                //encrypt property
                Property encryptProperty = new Property();
                encryptProperty.setIdentifier("RecipientEmailsAttribute");
                encryptProperty.setValue(recipientsEmailsAttribute);

                //unprotectedProperties
                PropertiesType unprotectedProperties = new PropertiesType();
                unprotectedProperties.getProperty().add(encryptProperty);

                //optinalinputs
                EncryptDocument.OptionalInputs optionalInputs = new EncryptDocument.OptionalInputs();
                optionalInputs.setEncryptionType("urn:ietf:rfc:5652");
                optionalInputs.setUnprotectedProperties(unprotectedProperties);

                //document type
                Base64Data base64Data = new Base64Data();
                base64Data.setValue((byte[])defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SIGNED_MAIL));
                de.gematik.ws.conn.connectorcommon.DocumentType documentType = new DocumentType();
                documentType.setBase64Data(base64Data);

                encryptDocument.setDocument(documentType);
                encryptDocument.setOptionalInputs(optionalInputs);

                //recipient keys
                EncryptDocument.RecipientKeys recipientKeys = new EncryptDocument.RecipientKeys();
                for (Iterator<X509CertificateResult> iterator = x509CertificateResults.iterator(); iterator.hasNext(); ) {
                    X509CertificateResult x509CertificateResult = iterator.next();
                    List<byte[]> certs = konnektor.isEccEncryptionAvailable() ? x509CertificateResult.getCerts() : x509CertificateResult.getRsaCerts();
                    for (Iterator<byte[]> iterator1 = certs.iterator(); iterator1.hasNext(); ) {
                        byte[] cert = iterator1.next();
                        recipientKeys.getCertificate().add(cert);
                    }
                }
                encryptDocument.setRecipientKeys(recipientKeys);

                timeMetric = metricFactory.timer(NAME);
                EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) webserviceConnector.getSoapResponse(encryptDocument);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_ENCRYPT_DOCUMENT_RESPONSE, encryptDocumentResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the EncryptMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
