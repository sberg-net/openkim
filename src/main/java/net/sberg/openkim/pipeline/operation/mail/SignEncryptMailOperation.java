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
package net.sberg.openkim.pipeline.operation.mail;

import de.gematik.ws.conn.connectorcommon.DocumentType;
import de.gematik.ws.conn.encryptionservice.v6_1_1.EncryptDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignResponse;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.GetSignCardHandleOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.EncryptMailOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.SignMailOperation;
import oasis.names.tc.dss._1_0.core.schema.SignatureObject;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.BodyPart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class SignEncryptMailOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(SignEncryptMailOperation.class);
    public static final String NAME = "SignEncryptMail";

    public static final String ENV_ORIGIN_MIMEMESSAGE = "originMimeMessage";
    public static final String ENV_RECIPIENT_CERTS = "recipientCerts";
    public static final String ENV_FROM_SENDER_CERTS = "fromSenderCerts";
    public static final String ENV_RESULT_MSG_BYTES = "resultMsgBytes";

    private CheckEncryptedMailFormatOperation checkEncryptedMailFormatOperation;
    private GetSignCardHandleOperation getSignCardHandleOperation;
    private SignMailOperation signMailOperation;
    private EncryptMailOperation encryptMailOperation;
    private ComposeEncryptedMailOperation composeEncryptedMailOperation;

    @Override
    public void initialize(PipelineService pipelineService) throws Exception {
        checkEncryptedMailFormatOperation = (CheckEncryptedMailFormatOperation) pipelineService.getOperation(BUILTIN_VENDOR+"."+CheckEncryptedMailFormatOperation.NAME);
        getSignCardHandleOperation = (GetSignCardHandleOperation) pipelineService.getOperation(BUILTIN_VENDOR + "." + GetSignCardHandleOperation.NAME);
        signMailOperation = (SignMailOperation) pipelineService.getOperation(BUILTIN_VENDOR + "." + SignMailOperation.NAME);
        encryptMailOperation = (EncryptMailOperation) pipelineService.getOperation(BUILTIN_VENDOR + "." + EncryptMailOperation.NAME);
        composeEncryptedMailOperation = (ComposeEncryptedMailOperation) pipelineService.getOperation(BUILTIN_VENDOR + "." + ComposeEncryptedMailOperation.NAME);
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

            MimeMessage originMimeMessage = (MimeMessage)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ORIGIN_MIMEMESSAGE);
            List<X509CertificateResult> recipientCerts = (List<X509CertificateResult>)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_RECIPIENT_CERTS);
            List<X509CertificateResult> fromSenderCerts = (List<X509CertificateResult>)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_FROM_SENDER_CERTS);

            //is a mimebodypart encrypted?
            boolean encrypted = false;
            if (originMimeMessage.isMimeType("multipart/mixed")
                &&
                originMimeMessage.getContent() instanceof MimeMultipart
                &&
                ((MimeMultipart) originMimeMessage.getContent()).getCount() == 2
            ) {
                MimeMultipart mimeMultipart = (MimeMultipart) originMimeMessage.getContent();
                BodyPart bodyPart = null;
                if (mimeMultipart.getBodyPart(0).isMimeType("message/rfc822")) {
                    bodyPart = mimeMultipart.getBodyPart(0);
                }
                else if (mimeMultipart.getBodyPart(1).isMimeType("message/rfc822")) {
                    bodyPart = mimeMultipart.getBodyPart(1);
                }
                if (bodyPart != null) {
                    MimeMessage encryptedBodyPart = MailUtils.createMimeMessage(null, bodyPart.getInputStream(), true);

                    defaultPipelineOperationContext.setEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_MSG, encryptedBodyPart);
                    defaultPipelineOperationContext.setEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_DECRYPT_MODE, false);

                    checkEncryptedMailFormatOperation.execute(
                        defaultPipelineOperationContext,
                        context -> {
                            log.info("checking encrypted mail format finished");
                        },
                        (context, e) -> {
                            log.error("error on checking encrypted mail format", e);
                        }
                    );

                    boolean valid = (boolean)defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_VALID_RESULT);
                    if (valid && logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().isEmpty()) {
                        logger.logLine("bodypart is encrypted");
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        encryptedBodyPart.writeTo(byteArrayOutputStream);
                        byte[] result = byteArrayOutputStream.toByteArray();
                        byteArrayOutputStream.close();
                        defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG_BYTES, result);
                        encrypted = true;
                    }
                    else {
                        logger.logLine("bodypart is not encrypted");
                    }
                }
            }

            if (!encrypted) {
                //get card handle
                getSignCardHandleOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("loading of signing card handle finished");
                    },
                    (context, e) -> {
                        log.error("error on loading of signing card handle", e);
                    }
                );

                boolean cardHandleFound = (boolean) defaultPipelineOperationContext.getEnvironmentValue(GetSignCardHandleOperation.NAME, GetSignCardHandleOperation.ENV_RESULT_CARD_HANDLE_FOUND);
                if (!cardHandleFound) {
                    throw new IllegalStateException("card handle not found");
                }
                String cardSignHandle = (String) defaultPipelineOperationContext.getEnvironmentValue(GetSignCardHandleOperation.NAME, GetSignCardHandleOperation.ENV_RESULT_CARD_HANDLE);

                List<X509CertificateResult> recipientSenderCerts = new ArrayList<>(recipientCerts);
                boolean add = true;
                for (Iterator<X509CertificateResult> iterator = fromSenderCerts.iterator(); iterator.hasNext(); ) {
                    add = true;
                    X509CertificateResult fromSender = iterator.next();
                    for (Iterator<X509CertificateResult> iterator2 = recipientSenderCerts.iterator(); iterator2.hasNext(); ) {
                        X509CertificateResult x509CertificateResult = iterator2.next();
                        if (x509CertificateResult.getMailAddress().equals(fromSender.getMailAddress().toLowerCase())) {
                            add = false;
                            break;
                        }
                    }
                    if (add) {
                        recipientSenderCerts.add(fromSender);
                    }
                }

                //signing mail
                defaultPipelineOperationContext.setEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_CARDHANDLE, cardSignHandle);
                defaultPipelineOperationContext.setEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_MIMEMESSAGE, originMimeMessage);
                defaultPipelineOperationContext.setEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_VZD_CERTS, recipientSenderCerts);

                AtomicInteger failedCounter = new AtomicInteger();
                signMailOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("signing mail finished");
                    },
                    (context, e) -> {
                        log.error("error on signing mail", e);
                        failedCounter.incrementAndGet();
                    }
                );

                byte[] signedMsg = null;
                if (failedCounter.get() == 0) {
                    SignDocumentResponse signDocumentResponse = (SignDocumentResponse) defaultPipelineOperationContext.getEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_SIGN_DOCUMENT_RESPONSE);
                    if (signDocumentResponse.getSignResponse().isEmpty()) {
                        throw new IllegalStateException("empty sign response for the cardHandle: " + cardSignHandle);
                    }
                    SignResponse signResponse = signDocumentResponse.getSignResponse().get(0);
                    if (!signResponse.getStatus().getResult().equals("OK")) {
                        throw new IllegalStateException("sign response not ok for the cardHandle: " + cardSignHandle + " - " + signResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + signResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
                    }
                    SignatureObject signatureObject = signResponse.getSignatureObject();
                    if (signatureObject == null) {
                        throw new IllegalStateException("sign response signatureObject empty for the cardHandle: " + cardSignHandle);
                    }
                    if (signatureObject.getBase64Signature().getValue() == null) {
                        throw new IllegalStateException("sign response signatureObject empty for the cardHandle: " + cardSignHandle);
                    }

                    MimeBodyPart mimeBodyPartSignedMsg = new MimeBodyPart();
                    mimeBodyPartSignedMsg.setContent(signatureObject.getBase64Signature().getValue(), CMSUtils.SMIME_CONTENT_TYPE);
                    mimeBodyPartSignedMsg.setHeader("Content-Type", CMSUtils.SMIME_CONTENT_TYPE);
                    mimeBodyPartSignedMsg.setHeader("Content-Transfer-Encoding", "binary");
                    mimeBodyPartSignedMsg.setDisposition(CMSUtils.SMIME_DISPOSITION);

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    mimeBodyPartSignedMsg.writeTo(byteArrayOutputStream);
                    signedMsg = byteArrayOutputStream.toByteArray();
                    byteArrayOutputStream.close();

                    if (signedMsg == null) {
                        throw new IllegalStateException("error on signing mail");
                    }
                } else {
                    throw new IllegalStateException("error on signing mail");
                }

                //encrypting mail
                defaultPipelineOperationContext.setEnvironmentValue(EncryptMailOperation.NAME, EncryptMailOperation.ENV_SIGNED_MAIL, signedMsg);
                defaultPipelineOperationContext.setEnvironmentValue(EncryptMailOperation.NAME, EncryptMailOperation.ENV_VZD_CERTS, recipientSenderCerts);

                AtomicInteger encryptFailedCounter = new AtomicInteger();
                encryptMailOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("encrypting mail finished");
                    },
                    (context, e) -> {
                        log.error("error on encrypting mail", e);
                        encryptFailedCounter.incrementAndGet();
                    }
                );

                byte[] encryptedMsg = null;
                if (encryptFailedCounter.get() == 0) {
                    EncryptDocumentResponse encryptDocumentResponse = (EncryptDocumentResponse) defaultPipelineOperationContext.getEnvironmentValue(EncryptMailOperation.NAME, EncryptMailOperation.ENV_ENCRYPT_DOCUMENT_RESPONSE);
                    if (!encryptDocumentResponse.getStatus().getResult().equals("OK")) {
                        throw new IllegalStateException("encrypt response not ok for the konnektor: " + konnektor.getIp() + " - " + encryptDocumentResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + encryptDocumentResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
                    }
                    DocumentType documentType = encryptDocumentResponse.getDocument();
                    if (documentType == null) {
                        throw new IllegalStateException("encrypt response document empty for the konnektor: " + konnektor.getIp());
                    }
                    if (documentType.getBase64Data().getValue() == null) {
                        throw new IllegalStateException("encrypt response document empty for the konnektor: " + konnektor.getIp());
                    }

                    encryptedMsg = documentType.getBase64Data().getValue();
                    if (encryptedMsg == null) {
                        throw new IllegalStateException("error on encrypting mail");
                    }
                } else {
                    throw new IllegalStateException("error on encrypting mail");
                }

                //compose encrypting mail
                defaultPipelineOperationContext.setEnvironmentValue(ComposeEncryptedMailOperation.NAME, ComposeEncryptedMailOperation.ENV_ENCRYPTED_MSG, encryptedMsg);
                defaultPipelineOperationContext.setEnvironmentValue(ComposeEncryptedMailOperation.NAME, ComposeEncryptedMailOperation.ENV_RECIPIENT_CERTS, recipientSenderCerts);
                defaultPipelineOperationContext.setEnvironmentValue(ComposeEncryptedMailOperation.NAME, ComposeEncryptedMailOperation.ENV_ORIGIN_MSG, originMimeMessage);

                AtomicInteger composeEncryptedMsgFailedCounter = new AtomicInteger();
                composeEncryptedMailOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("composing mail finished");
                    },
                    (context, e) -> {
                        log.error("error on composing mail", e);
                        composeEncryptedMsgFailedCounter.incrementAndGet();
                    }
                );

                if (composeEncryptedMsgFailedCounter.get() == 0) {
                    byte[] result = (byte[]) defaultPipelineOperationContext.getEnvironmentValue(ComposeEncryptedMailOperation.NAME, ComposeEncryptedMailOperation.ENV_RESULT_MSG_BYTES);
                    defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG_BYTES, result);
                } else {
                    throw new IllegalStateException("error on composing mail");
                }
            }

            timeMetric.stopAndPublish();
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the SignEncryptMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
