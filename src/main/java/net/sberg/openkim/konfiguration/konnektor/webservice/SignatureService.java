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
package net.sberg.openkim.konfiguration.konnektor.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.signatureservice.v7_5_5.*;
import de.gematik.ws.conn.signatureservice.v7_5_5.DocumentType;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignRequest;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konfiguration.konnektor.*;
import net.sberg.openkim.konfiguration.konnektor.vzd.VzdService;
import net.sberg.openkim.konfiguration.konnektor.webservice.jaxb.CMSAttribute;
import net.sberg.openkim.log.DefaultLogger;
import oasis.names.tc.dss._1_0.core.schema.*;
import oasis.names.tc.dss_x._1_0.profiles.verificationreport.schema_.ReturnVerificationReport;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class SignatureService {

    private static final Logger log = LoggerFactory.getLogger(SignatureService.class);

    private static final String OP_SIGN_MAIL = "SignMail";
    private static final String OP_SIGN_MAIL_TEXT = "SignMailText";
    private static final String OP_SIGN_VERIFY = "SignVerify";

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

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            if (webserviceBean.getOpId().equals(OP_SIGN_MAIL)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("SignDocument"),
                    logger
                );

                SignatureSignMailWebserviceBean signatureSignMailWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, SignatureSignMailWebserviceBean.class);

                //create mimemessage
                MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new java.util.Properties()));
                mimeMessage.setFrom(signatureSignMailWebserviceBean.getFrom());
                mimeMessage.setRecipients(Message.RecipientType.TO, signatureSignMailWebserviceBean.getTo());
                mimeMessage.setSubject(signatureSignMailWebserviceBean.getSubject());
                ((MimePart) mimeMessage).setText(signatureSignMailWebserviceBean.getBody());
                mimeMessage.saveChanges();

                SignDocument signDocument = createSignDocument(
                    contextType,
                    signatureSignMailWebserviceBean.getCardHandle(),
                    mimeMessage,
                    null,
                    null,
                    logger
                );

                timeMetric = metricFactory.timer("SignatureService:signDocument");
                SignDocumentResponse signDocumentResponse = (SignDocumentResponse) webserviceConnector.getSoapResponse(signDocument);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + signDocumentResponse.getSignResponse().get(0).getStatus().getResult());
                logger.logLine("Dokument = " + Base64.encodeBase64String(signDocumentResponse.getSignResponse().get(0).getSignatureObject().getBase64Signature().getValue()));

                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_SIGN_MAIL_TEXT)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("SignDocument"),
                    logger
                );

                SignatureSignMailTextWebserviceBean signatureSignMailTextWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, SignatureSignMailTextWebserviceBean.class);

                SignDocument signDocument = createSignDocument(
                    contextType,
                    signatureSignMailTextWebserviceBean.getCardHandle(),
                    null,
                    signatureSignMailTextWebserviceBean.getCompleteMail(),
                    signatureSignMailTextWebserviceBean.getMailAddress(),
                    logger
                );

                timeMetric = metricFactory.timer("SignatureService:signMail");
                SignDocumentResponse signDocumentResponse = (SignDocumentResponse) webserviceConnector.getSoapResponse(signDocument);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + signDocumentResponse.getSignResponse().get(0).getStatus().getResult());
                logger.logLine("Dokument = " + Base64.encodeBase64String(signDocumentResponse.getSignResponse().get(0).getSignatureObject().getBase64Signature().getValue()));

                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_SIGN_VERIFY)) {
                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("VerifyDocument"),
                    logger
                );
                SignatureSignVerifyWebserviceBean signatureSignVerifyWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, SignatureSignVerifyWebserviceBean.class);

                VerifyDocument verifyDocument = createVerifyDocument(konnektor, contextType, signatureSignVerifyWebserviceBean.getSignedDocument().getBytes(StandardCharsets.UTF_8), true);

                timeMetric = metricFactory.timer("SignatureService:verifyDocument");
                VerifyDocumentResponse verifyDocumentResponse = (VerifyDocumentResponse) webserviceConnector.getSoapResponse(verifyDocument);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + verifyDocumentResponse.getStatus().getResult());
                logger.logLine("Result = " + verifyDocumentResponse.getVerificationResult().getHighLevelResult());

                return logger.getLogContentAsStr();

            } else {
                throw new IllegalStateException("unknown opId for the SignatureService and konnektor: " + konnektor.getIp());
            }
        } catch (Exception e) {
            log.error("error on executing the SignatureService for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    private VerifyDocument createVerifyDocument(
        Konnektor konnektor,
        ContextType contextType,
        byte[] signedData,
        boolean signedDataAsBase64
    ) throws Exception {
        KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
        String packageName = konnektorServiceBean.createClassPackageName();

        VerifyDocument verifyDocument = new VerifyDocument();
        if (!verifyDocument.getClass().getPackageName().equals(packageName)) {
            throw new IllegalStateException(
                "signature webservice not valid "
                + konnektor.getIp()
                + " - "
                + konnektorServiceBean.getId()
                + " - packagename not equal "
                + packageName
                + " - "
                + verifyDocument.getClass().getPackageName()
            );
        }

        verifyDocument.setContext(contextType);

        SignatureObject signatureObject = new SignatureObject();
        Base64Signature base64Signature = new Base64Signature();
        base64Signature.setValue(signedDataAsBase64 ? Base64.decodeBase64(signedData) : signedData);
        base64Signature.setType("urn:ietf:rfc:5652");
        signatureObject.setBase64Signature(base64Signature);
        verifyDocument.setSignatureObject(signatureObject);

        VerifyDocument.OptionalInputs optionalInputs = new VerifyDocument.OptionalInputs();
        ReturnVerificationReport returnVerificationReport = new ReturnVerificationReport();
        returnVerificationReport.setExpandBinaryValues(false);
        returnVerificationReport.setIncludeCertificateValues(true);
        returnVerificationReport.setIncludeRevocationValues(true);
        returnVerificationReport.setIncludeVerifier(false);
        returnVerificationReport.setReportDetailLevel("urn:oasis:names:tc:dss:1.0:profiles:verificationreport:reportdetail:allDetails");
        optionalInputs.setReturnVerificationReport(returnVerificationReport);
        verifyDocument.setOptionalInputs(optionalInputs);

        verifyDocument.setIncludeRevocationInfo(false);

        return verifyDocument;
    }

    private SignDocument createSignDocument(
        List<X509CertificateResult> x509CertificateResults,
        ContextType contextType,
        String cardHandle,
        MimeMessage mimeMessage,
        String completeMail,
        DefaultLogger logger
    ) throws Exception {

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
        String packageName = konnektorServiceBean.createClassPackageName();

        SignDocument signDocument = new SignDocument();
        if (!signDocument.getClass().getPackageName().equals(packageName)) {
            throw new IllegalStateException(
                "signature webservice not valid "
                + konnektor.getIp()
                + " - "
                + konnektorServiceBean.getId()
                + " - packagename not equal "
                + packageName
                + " - "
                + signDocument.getClass().getPackageName()
            );
        }
        byte[] cmsAttr = CMSUtils.buildCmsAttributeRecipientEmails(x509CertificateResults, konnektor);
        CMSAttribute cmsAttribute = new CMSAttribute();
        cmsAttribute.setContent(Base64.encodeBase64String(cmsAttr));

        //recipientsEmailsAttribute anytype
        AnyType recipientsEmailsAttribute = new AnyType();
        recipientsEmailsAttribute.getAny().add(cmsAttribute);

        //sign property
        Property signProperty = new Property();
        signProperty.setIdentifier("RecipientEmailsAttribute");
        signProperty.setValue(recipientsEmailsAttribute);

        //signPropertiesType
        PropertiesType signPropertiesType = new PropertiesType();
        signPropertiesType.getProperty().add(signProperty);

        //signProperties
        Properties signProperties = new Properties();
        signProperties.setSignedProperties(signPropertiesType);

        //optionalinputs
        SignRequest.OptionalInputs optionalInputs = new SignRequest.OptionalInputs();
        optionalInputs.setProperties(signProperties);
        optionalInputs.setSignatureType("urn:ietf:rfc:5652");
        optionalInputs.setIncludeEContent(true);

        //base64 Data
        Base64Data base64Data = new Base64Data();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        if (mimeMessage != null) {
            byteArrayOutputStream.write("Content-Type: message/rfc822\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            mimeMessage.writeTo(byteArrayOutputStream);
        } else {
            byteArrayOutputStream.write(completeMail.getBytes(StandardCharsets.UTF_8));
        }
        base64Data.setValue(byteArrayOutputStream.toByteArray());
        byteArrayOutputStream.close();

        //document
        DocumentType documentType = new DocumentType();
        documentType.setBase64Data(base64Data);

        //sign request
        SignRequest signRequest = new SignRequest();
        signRequest.setOptionalInputs(optionalInputs);
        signRequest.setDocument(documentType);
        signRequest.setRequestID("KimSignRequest");
        signRequest.setIncludeRevocationInfo(false);

        //job number
        GetJobNumberResponse getJobNumberResponse = getJobNumber(logger);
        signDocument.setJobNumber(getJobNumberResponse.getJobNumber());
        signDocument.getSignRequest().add(signRequest);
        signDocument.setContext(contextType);
        signDocument.setCardHandle(cardHandle);
        signDocument.setTvMode("NONE");

        return signDocument;
    }

    private SignDocument createSignDocument(
        ContextType contextType,
        String cardHandle,
        MimeMessage mimeMessage,
        String completeMail,
        String toRecipient,
        DefaultLogger logger) throws Exception {

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
        String packageName = konnektorServiceBean.createClassPackageName();

        if (mimeMessage != null) {
            toRecipient = ((InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]).getAddress();
        }

        SignDocument signDocument = new SignDocument();
        if (!signDocument.getClass().getPackageName().equals(packageName)) {
            throw new IllegalStateException(
                "signature webservice not valid "
                + konnektor.getIp()
                + " - "
                + konnektorServiceBean.getId()
                + " - packagename not equal "
                + packageName
                + " - "
                + signDocument.getClass().getPackageName()
            );
        }

        //cms attribute
        List<X509CertificateResult> x509CertificateResults = vzdService.loadCerts(logger, Collections.singletonList(toRecipient), false, true);

        return createSignDocument(
            x509CertificateResults,
            contextType,
            cardHandle,
            mimeMessage,
            completeMail,
            logger
        );
    }

    private GetJobNumberResponse getJobNumber(DefaultLogger logger) throws Exception {

        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("GetJobNumber"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            GetJobNumber getJobNumber = new GetJobNumber();

            if (!getJobNumber.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                    "card webservice not valid "
                    + konnektor.getIp()
                    + " - "
                    + konnektorServiceBean.getId()
                    + " - packagename not equal "
                    + packageName
                    + " - "
                    + getJobNumber.getClass().getPackageName()
                );
            }

            getJobNumber.setContext(contextType);

            timeMetric = new DefaultMetricFactory(logger).timer("SignatureService:getJobNumber:getJobNumber");
            GetJobNumberResponse getJobNumberResponse = (GetJobNumberResponse) webserviceConnector.getSoapResponse(getJobNumber);
            timeMetric.stopAndPublish();
            return getJobNumberResponse;
        } catch (Exception e) {
            log.error("error on executing the SignatureService - getJobNumber for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public SignDocumentResponse sign(
        DefaultLogger logger,
        MimeMessage mimeMessage,
        String cardHandle,
        List<X509CertificateResult> x509CertificateResults
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("SignDocument"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            SignDocument signDocument = createSignDocument(
                x509CertificateResults,
                contextType,
                cardHandle,
                mimeMessage,
                null,
                logger
            );

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("SignatureService:signDocument:sign");
            SignDocumentResponse signDocumentResponse = (SignDocumentResponse) webserviceConnector.getSoapResponse(signDocument);
            timeMetric.stopAndPublish();

            return signDocumentResponse;
        } catch (Exception e) {
            log.error("error on executing the SignatureService - sign for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public VerifyDocumentResponse verify(
        DefaultLogger logger,
        byte[] signedData
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("VerifyDocument"),
                logger
            );

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            VerifyDocument verifyDocument = createVerifyDocument(
                konnektor,
                contextType,
                signedData,
                false
            );

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("SignatureService:verifyDocument:verify");
            VerifyDocumentResponse verifyDocumentResponse = (VerifyDocumentResponse) webserviceConnector.getSoapResponse(verifyDocument);
            timeMetric.stopAndPublish();

            return verifyDocumentResponse;
        } catch (Exception e) {
            log.error("error on executing the SignatureService - verify for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
