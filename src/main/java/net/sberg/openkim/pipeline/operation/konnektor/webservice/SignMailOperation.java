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

import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.signatureservice.v7_5_5.DocumentType;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignRequest;
import de.gematik.ws.conn.signatureservice.v7_5_5.*;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.konnektor.webservice.jaxb.CMSAttribute;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.LoadVzdCertsOperation;
import oasis.names.tc.dss._1_0.core.schema.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@PipelineOperation
public class SignMailOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(SignMailOperation.class);
    public static final String NAME = "SignMail";

    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_MIMEMESSAGE_AS_STRING = "mimeMessageAsString";
    public static final String ENV_MAIL_FROM = "mailFrom";
    public static final String ENV_MAIL_TO = "mailTo";
    public static final String ENV_MAIL_SUBJECT = "mailSubject";
    public static final String ENV_MAIL_BODY = "mailBody";
    public static final String ENV_SIGN_DOCUMENT_RESPONSE = "signDocumentResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> consumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
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
                    konnektorServiceBean.createSoapAction("SignDocument"),
                    logger
            );

            String cardHandle = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE);
            String mimeMessageAsString = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MIMEMESSAGE_AS_STRING);
            MimeMessage mimeMessage = null;
            String mailFrom = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MAIL_FROM);
            String mailTo = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MAIL_TO);
            String mailSubject = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MAIL_SUBJECT);
            String mailBody = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MAIL_BODY);
            if (mimeMessageAsString != null && !mimeMessageAsString.trim().isEmpty()) {
                mimeMessage = new MimeMessage(Session.getInstance(new java.util.Properties()));
                mimeMessage.setFrom(mailFrom);
                mimeMessage.setRecipients(Message.RecipientType.TO, mailTo);
                mimeMessage.setSubject(mailSubject);
                ((MimePart) mimeMessage).setText(mailBody);
                mimeMessage.saveChanges();
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

            List<X509CertificateResult> x509CertificateResults = (List<X509CertificateResult>) defaultPipelineOperationContext.getEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_CERTS);
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
            if (mimeMessageAsString != null && !mimeMessageAsString.trim().isEmpty()) {
                byteArrayOutputStream.write(mimeMessageAsString.getBytes(StandardCharsets.UTF_8));
            }
            else {
                byteArrayOutputStream.write("Content-Type: message/rfc822\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                mimeMessage.writeTo(byteArrayOutputStream);
            }
            base64Data.setValue(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.close();

            //document
            de.gematik.ws.conn.signatureservice.v7_5_5.DocumentType documentType = new DocumentType();
            documentType.setBase64Data(base64Data);

            //sign request
            SignRequest signRequest = new SignRequest();
            signRequest.setOptionalInputs(optionalInputs);
            signRequest.setDocument(documentType);
            signRequest.setRequestID("KimSignRequest");
            signRequest.setIncludeRevocationInfo(false);

            //job number
            GetJobNumberResponse getJobNumberResponse = (GetJobNumberResponse) defaultPipelineOperationContext.getEnvironmentValue(GetJobNumberOperation.NAME, GetJobNumberOperation.ENV_GET_JOB_NUMBER_RESPONSE);
            signDocument.setJobNumber(getJobNumberResponse.getJobNumber());
            signDocument.getSignRequest().add(signRequest);
            signDocument.setContext(contextType);
            signDocument.setCardHandle(cardHandle);
            signDocument.setTvMode("NONE");

            timeMetric = metricFactory.timer(NAME);
            SignDocumentResponse signDocumentResponse = (SignDocumentResponse) webserviceConnector.getSoapResponse(signDocument);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_SIGN_DOCUMENT_RESPONSE, signDocumentResponse);

            timeMetric.stopAndPublish();

            consumer.accept(defaultPipelineOperationContext);
            return true;
        } catch (Exception e) {
            log.error("error on executing the SignMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            return false;
        }
    }
}
