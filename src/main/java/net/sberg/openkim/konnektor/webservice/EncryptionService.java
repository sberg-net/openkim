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
package net.sberg.openkim.konnektor.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.ws.conn.connectorcommon.DocumentType;
import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.encryptionservice.v6_1_1.*;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.konnektor.vzd.VzdService;
import net.sberg.openkim.konnektor.webservice.jaxb.CMSAttribute;
import net.sberg.openkim.log.DefaultLogger;
import oasis.names.tc.dss._1_0.core.schema.AnyType;
import oasis.names.tc.dss._1_0.core.schema.Base64Data;
import oasis.names.tc.dss._1_0.core.schema.PropertiesType;
import oasis.names.tc.dss._1_0.core.schema.Property;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String OP_DECRYPT_DOCUMENT = "DecryptDocument";
    private static final String OP_ENCRYPT_DOCUMENT = "EncryptDocument";
    private static final String OP_ENCRYPT_MAIL = "EncryptMail";

    @Autowired
    private VzdService vzdService;

    public String execute(
        DefaultLogger logger,
        WebserviceBean webserviceBean,
        Map serviceBeanMap
    ) throws Exception {
        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EncryptionService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            if (webserviceBean.getOpId().equals(OP_DECRYPT_DOCUMENT)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(OP_DECRYPT_DOCUMENT),
                    logger
                );

                EncryptionDecryptDocumentWebserviceBean encryptionDecryptDocumentWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, EncryptionDecryptDocumentWebserviceBean.class);

                byte[] value = (encryptionDecryptDocumentWebserviceBean.getOriginDocument() != null && !encryptionDecryptDocumentWebserviceBean.getOriginDocument().trim().isEmpty()) ?
                    encryptionDecryptDocumentWebserviceBean.getOriginDocument().getBytes(StandardCharsets.UTF_8) :
                    Base64.decodeBase64(encryptionDecryptDocumentWebserviceBean.getBase64Document().getBytes(StandardCharsets.UTF_8));

                DecryptDocument decryptDocument = createDecryptDocument(
                    contextType,
                    konnektor,
                    encryptionDecryptDocumentWebserviceBean.getCardHandle(),
                    value
                );

                timeMetric = metricFactory.timer("EncryptionService:decryptDocument:execute");
                DecryptDocumentResponse decryptDocumentResponse = (DecryptDocumentResponse) webserviceConnector.getSoapResponse(decryptDocument);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + decryptDocumentResponse.getStatus().getResult());
                logger.logLine("Dokument = " + new String(decryptDocumentResponse.getDocument().getBase64Data().getValue()));

                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_ENCRYPT_DOCUMENT)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(OP_ENCRYPT_DOCUMENT),
                    logger
                );

                EncryptionEncryptDocumentWebserviceBean encryptionEncryptDocumentWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, EncryptionEncryptDocumentWebserviceBean.class);
                EncryptDocument encryptDocument = new EncryptDocument();
                encryptDocument.setContext(contextType);

                if (!encryptDocument.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "encryption webservice not valid "
                        + webserviceBean.getKonnId()
                        + " - "
                        + webserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + encryptDocument.getClass().getPackageName()
                    );
                }

                EncryptDocument.RecipientKeys recipientKeys = new EncryptDocument.RecipientKeys();
                KeyOnCardType keyOnCardType = new KeyOnCardType();
                keyOnCardType.setCardHandle(encryptionEncryptDocumentWebserviceBean.getCardHandle());
                recipientKeys.setCertificateOnCard(keyOnCardType);
                encryptDocument.setRecipientKeys(recipientKeys);

                Base64Data base64Data = new Base64Data();
                base64Data.setValue(Base64.encodeBase64(encryptionEncryptDocumentWebserviceBean.getDocument().getBytes()));
                DocumentType documentType = new DocumentType();
                documentType.setBase64Data(base64Data);
                encryptDocument.setDocument(documentType);

                EncryptDocument.OptionalInputs optionalInputs = new EncryptDocument.OptionalInputs();
                optionalInputs.setEncryptionType("urn:ietf:rfc:5652");
                encryptDocument.setOptionalInputs(optionalInputs);

                timeMetric = metricFactory.timer("EncryptionService:encryptDocument:execute");
                EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) webserviceConnector.getSoapResponse(encryptDocument);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + encryptDocumentResponse.getStatus().getResult());
                logger.logLine("Dokument Verschlüsselt Plain = " + new String(encryptDocumentResponse.getDocument().getBase64Data().getValue()));
                logger.logLine("Dokument Verschlüsselt Base64 = " + Base64.encodeBase64String(encryptDocumentResponse.getDocument().getBase64Data().getValue()));

                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_ENCRYPT_MAIL)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(OP_ENCRYPT_DOCUMENT),
                    logger
                );

                EncryptionEncryptMailWebserviceBean encryptionEncryptMailWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, EncryptionEncryptMailWebserviceBean.class);

                //certs
                List<X509CertificateResult> x509CertificateResults = vzdService.loadCerts(logger, Collections.singletonList(encryptionEncryptMailWebserviceBean.getTo()), false, true);

                EncryptDocument encryptDocument = createEncryptMailDocument(
                    contextType,
                    x509CertificateResults,
                    konnektor,
                    encryptionEncryptMailWebserviceBean.getSignedMail().getBytes()
                );

                timeMetric = metricFactory.timer("EncryptionService:encryptMail:execute");
                EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) webserviceConnector.getSoapResponse(encryptDocument);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + encryptDocumentResponse.getStatus().getResult());
                logger.logLine("Dokument Verschlüsselt Plain = " + new String(encryptDocumentResponse.getDocument().getBase64Data().getValue()));
                logger.logLine("Dokument Verschlüsselt Base64 = " + Base64.encodeBase64String(encryptDocumentResponse.getDocument().getBase64Data().getValue()));

                return logger.getLogContentAsStr();
            } else {
                throw new IllegalStateException("unknown opId for the EncryptionService and konnektor: " + konnektor.getIp());
            }
        } catch (Exception e) {
            log.error("error on executing the EncryptionService for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    private DecryptDocument createDecryptDocument(ContextType contextType, Konnektor konnektor, String cardHandle, byte[] documentBytes) throws Exception {

        KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EncryptionService, true);
        String packageName = konnektorServiceBean.createClassPackageName();

        DecryptDocument decryptDocument = new DecryptDocument();
        decryptDocument.setContext(contextType);

        if (!decryptDocument.getClass().getPackageName().equals(packageName)) {
            throw new IllegalStateException(
                "encryption webservice not valid "
                + konnektor.getUuid()
                + " - "
                + konnektorServiceBean.getId()
                + " - packagename not equal "
                + packageName
                + " - "
                + decryptDocument.getClass().getPackageName()
            );
        }

        KeyOnCardType keyOnCardType = new KeyOnCardType();
        keyOnCardType.setCardHandle(cardHandle);
        decryptDocument.setPrivateKeyOnCard(keyOnCardType);

        Base64Data base64Data = new Base64Data();
        base64Data.setValue(documentBytes);
        DocumentType documentType = new DocumentType();
        documentType.setBase64Data(base64Data);
        decryptDocument.setDocument(documentType);

        return decryptDocument;
    }

    private EncryptDocument createEncryptMailDocument(
        ContextType contextType,
        List<X509CertificateResult> x509CertificateResults,
        Konnektor konnektor,
        byte[] mail
    ) throws Exception {

        KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EncryptionService, true);
        String packageName = konnektorServiceBean.createClassPackageName();

        EncryptDocument encryptDocument = new EncryptDocument();
        encryptDocument.setContext(contextType);
        if (!encryptDocument.getClass().getPackageName().equals(packageName)) {
            throw new IllegalStateException(
                "encryption webservice not valid "
                + konnektor.getUuid()
                + " - "
                + konnektorServiceBean.getId()
                + " - packagename not equal "
                + packageName
                + " - "
                + encryptDocument.getClass().getPackageName()
            );
        }

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
        base64Data.setValue(mail);
        DocumentType documentType = new DocumentType();
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

        return encryptDocument;
    }

    public DecryptDocumentResponse decryptMail(
        DefaultLogger logger,
        String cardHandle,
        byte[] mail
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EncryptionService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("DecryptDocument"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DecryptDocument decryptDocument = createDecryptDocument(contextType, konnektor, cardHandle, mail);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("EncryptionService:decryptMail:decryptMail");
            DecryptDocumentResponse decryptDocumentResponse = (DecryptDocumentResponse) webserviceConnector.getSoapResponse(decryptDocument);
            timeMetric.stopAndPublish();

            return decryptDocumentResponse;
        } catch (Exception e) {
            log.error("error on executing the EncryptionService - decryptMail for the konnektor: " + konnektor.getUuid() + " - " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public EncryptDocumentResponse encryptMail(
        DefaultLogger logger,
        List<X509CertificateResult> x509CertificateResults,
        byte[] mail
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EncryptionService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("EncryptDocument"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            EncryptDocument encryptDocument = createEncryptMailDocument(
                contextType,
                x509CertificateResults,
                konnektor,
                mail
            );

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("EncryptionService:encryptMail:encryptMail");
            EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) webserviceConnector.getSoapResponse(encryptDocument);
            timeMetric.stopAndPublish();

            return encryptDocumentResponse;
        } catch (Exception e) {
            log.error("error on executing the EncryptionService - encryptMail for the konnektor: " + konnektor.getUuid() + " - " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
