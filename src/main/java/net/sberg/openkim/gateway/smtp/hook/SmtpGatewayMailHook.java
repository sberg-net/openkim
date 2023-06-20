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
package net.sberg.openkim.gateway.smtp.hook;

import de.gematik.ws.conn.connectorcommon.DocumentType;
import de.gematik.ws.conn.encryptionservice.v6_1_1.EncryptDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignResponse;
import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.gateway.smtp.EnumSmtpGatewayState;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.EnumGatewayTIMode;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.IErrorContext;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.konnektor.GetSignCardHandleOperation;
import net.sberg.openkim.pipeline.operation.konnektor.KonnektorLoadAllCardInformationOperation;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.LoadVzdCertsOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.EncryptMailOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.GetJobNumberOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.SignMailOperation;
import net.sberg.openkim.pipeline.operation.mail.*;
import oasis.names.tc.dss._1_0.core.schema.SignatureObject;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.BodyPart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class SmtpGatewayMailHook implements MessageHook {

    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayMailHook.class);

    private PipelineService pipelineService;

    private SmtpGatewayMailHook() {
    }

    public SmtpGatewayMailHook(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    private HookResult sendDsn(SmtpGatewaySession smtpGatewaySession, IErrorContext errorContext, MimeMessage originMessage, boolean senderContext) {
        try {
            Konfiguration konfiguration = smtpGatewaySession.getLogger().getDefaultLoggerContext().getKonfiguration();
            SendDsnOperation sendDsnOperation = (SendDsnOperation) pipelineService.getOperation(SendDsnOperation.BUILTIN_VENDOR+"."+SendDsnOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext();
            defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_ERROR_CONTEXT, errorContext);
            defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_ORIGIN_MSG, originMessage);
            defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_SMTP_GATEWAY_SESSION, smtpGatewaySession);
            defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_SENDER_CTX, senderContext);

            if (konfiguration.getGatewayTIMode().equals(EnumGatewayTIMode.FULLSTACK)) {
                String certfileName = ICommonConstants.BASE_DIR + File.separator + konfiguration.getFachdienstCertFilename();
                char[] passCharArray = konfiguration.getFachdienstCertAuthPwd().toCharArray();
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(new FileInputStream(certfileName), passCharArray);

                SSLContext sslContext = new SSLContextBuilder().loadKeyMaterial(keyStore, passCharArray).loadTrustMaterial(new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        return true;
                    }
                }).build();

                defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_SSL_CONTEXT, sslContext);
            }

            AtomicInteger failedCounter = new AtomicInteger();
            sendDsnOperation.execute(
                defaultPipelineOperationContext,
                defaultPipelineOperationContext1 -> {
                    log.info("dns message sended");
                },
                (defaultPipelineOperationContext1, e) -> {
                    log.error("error on sending dns", e);
                    failedCounter.incrementAndGet();
                }
            );

            if (failedCounter.get() == 0) {
                return HookResult.DENYSOFT;
            }
            return HookResult.DENY;
        }
        catch (Exception e) {
            return HookResult.DENY;
        }
    }

    private byte[] signEncrypt(
        DefaultLogger logger,
        MimeMessage originMimeMessage,
        List<X509CertificateResult> recipientCerts,
        List<X509CertificateResult> fromSenderCerts
    ) throws Exception {
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {
            //is a mimebodypart encrypted?
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

                    CheckEncryptedMailFormatOperation checkEncryptedMailFormatOperation = (CheckEncryptedMailFormatOperation) pipelineService.getOperation(CheckEncryptedMailFormatOperation.BUILTIN_VENDOR+"."+CheckEncryptedMailFormatOperation.NAME);
                    DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext();
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
                        return result;
                    }
                    else {
                        logger.logLine("bodypart is not encrypted");
                    }
                }
            }

            //get card handle
            GetSignCardHandleOperation getSignCardHandleOperation = (GetSignCardHandleOperation) pipelineService.getOperation(GetSignCardHandleOperation.BUILTIN_VENDOR+"."+GetSignCardHandleOperation.NAME);
            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext();
            KonnektorLoadAllCardInformationOperation konnektorLoadAllCardInformationOperation = (KonnektorLoadAllCardInformationOperation) pipelineService.getOperation(KonnektorLoadAllCardInformationOperation.BUILTIN_VENDOR+"."+KonnektorLoadAllCardInformationOperation.NAME);
            getSignCardHandleOperation.setKonnektorLoadAllCardInformationOperation(konnektorLoadAllCardInformationOperation);

            getSignCardHandleOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("loading of signing card handle finished");
                },
                (context, e) -> {
                    log.error("error on loading of signing card handle", e);
                }
            );

            boolean cardHandleFound = (boolean)defaultPipelineOperationContext.getEnvironmentValue(GetSignCardHandleOperation.NAME, GetSignCardHandleOperation.ENV_RESULT_CARD_HANDLE_FOUND);
            if (!cardHandleFound) {
                throw new IllegalStateException("card handle not found");
            }
            String cardSignHandle = (String)defaultPipelineOperationContext.getEnvironmentValue(GetSignCardHandleOperation.NAME, GetSignCardHandleOperation.ENV_RESULT_CARD_HANDLE);

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
            SignMailOperation signMailOperation = (SignMailOperation) pipelineService.getOperation(SignMailOperation.BUILTIN_VENDOR+"."+SignMailOperation.NAME);
            defaultPipelineOperationContext = new DefaultPipelineOperationContext();

            defaultPipelineOperationContext.setEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_CARDHANDLE, cardSignHandle);
            defaultPipelineOperationContext.setEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_MIMEMESSAGE, originMimeMessage);
            defaultPipelineOperationContext.setEnvironmentValue(SignMailOperation.NAME, SignMailOperation.ENV_VZD_CERTS, recipientSenderCerts);

            GetJobNumberOperation getJobNumberOperation = (GetJobNumberOperation) pipelineService.getOperation(GetJobNumberOperation.BUILTIN_VENDOR+"."+GetJobNumberOperation.NAME);
            signMailOperation.setGetJobNumberOperation(getJobNumberOperation);

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
            }
            else {
                throw new IllegalStateException("error on signing mail");
            }

            //encrypting mail
            EncryptMailOperation encryptMailOperation = (EncryptMailOperation) pipelineService.getOperation(EncryptMailOperation.BUILTIN_VENDOR+"."+EncryptMailOperation.NAME);
            defaultPipelineOperationContext = new DefaultPipelineOperationContext();

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
            }
            else {
                throw new IllegalStateException("error on encrypting mail");
            }

            //compose encrypting mail
            ComposeEncryptedMailOperation composeEncryptedMailOperation = (ComposeEncryptedMailOperation) pipelineService.getOperation(ComposeEncryptedMailOperation.BUILTIN_VENDOR+"."+ComposeEncryptedMailOperation.NAME);
            defaultPipelineOperationContext = new DefaultPipelineOperationContext();

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
                return result;
            }
            else {
                throw new IllegalStateException("error on composing mail");
            }
        } catch (Exception e) {
            log.error("error on mail signing and encrypting for the konnektor: " + konnektor.getIp(), e);
            throw e;
        }
    }

    @Override
    public HookResult onMessage(SMTPSession session, MailEnvelope mailEnvelope) {

        SmtpGatewaySession smtpGatewaySession = (SmtpGatewaySession) session;
        DefaultLogger logger = smtpGatewaySession.getLogger();

        try {
            smtpGatewaySession.log("mail hook begins");

            File tempMailFile = FileUtils.writeToFileDirectory((ByteArrayOutputStream) mailEnvelope.getMessageOutputStream(), "openkim", System.getProperty("java.io.tmpdir"));
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()), new FileInputStream(tempMailFile));
            tempMailFile.delete();

            if (smtpGatewaySession.extractNoFailureCertRcpts().isEmpty() || !smtpGatewaySession.extractFailureCertRcpts().isEmpty()) {
                smtpGatewaySession.getSmtpClient().rset();
                return sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), message, false);
            }

            String fromAddressStr = smtpGatewaySession.getFromAddressStr().toLowerCase();
            String msgContent = null;
            if (!logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.NO_TI)) {
                //add sender certs -> check kim version
                List<X509CertificateResult> fromSenderCerts = new ArrayList<>();
                try {
                    //cert check 4 from address
                    List<String> fromSenderAddress = new ArrayList<>();
                    fromSenderAddress.add(fromAddressStr);

                    smtpGatewaySession.log("load certs for from: " + fromAddressStr);

                    LoadVzdCertsOperation loadVzdCertsOperation = (LoadVzdCertsOperation) pipelineService.getOperation(LoadVzdCertsOperation.BUILTIN_VENDOR+"."+LoadVzdCertsOperation.NAME);
                    DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext();
                    defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_ADDRESSES, fromSenderAddress);
                    defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_SEARCH_BASE, logger.getDefaultLoggerContext().getKonnektor().getVzdSearchBase());
                    defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_LOAD_SENDER_ADRESSES, true);
                    defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_LOAD_RCPT_ADRESSES, false);

                    loadVzdCertsOperation.execute(
                        defaultPipelineOperationContext,
                        context -> {
                            log.info("loading certs for from-address finished");
                        },
                        (context, e) -> {
                            log.error("error on loading certs for from-address", e);
                        }
                    );
                    smtpGatewaySession.log("load certs ending for sender: " + fromAddressStr);

                    if (logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(fromAddressStr)) {
                        return sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), message, true);
                    }
                    if (logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(fromAddressStr)) {
                        return sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext(), message, true);
                    }

                    List certs = (List)defaultPipelineOperationContext.getEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_CERTS);
                    fromSenderCerts.addAll(certs);

                } catch (Exception e) {
                    log.error("error on loading sender cert for: " + session.getSessionID() + " - " + fromAddressStr, e);
                    smtpGatewaySession.log("error on loading sender cert for: " + session.getSessionID() + " - " + fromAddressStr);

                    smtpGatewaySession.getSmtpClient().rset();

                    if (logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(fromAddressStr)) {
                        sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), message, true);
                    }
                    if (logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(fromAddressStr)) {
                        sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext(), message, true);
                    }

                    return HookResult.DENYSOFT;
                }

                //check origin message
                CheckSendingMailOperation checkSendingMailOperation = (CheckSendingMailOperation) pipelineService.getOperation(CheckSendingMailOperation.BUILTIN_VENDOR+"."+CheckSendingMailOperation.NAME);
                DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext();
                defaultPipelineOperationContext.setEnvironmentValue(CheckSendingMailOperation.NAME, CheckSendingMailOperation.ENV_MSG, message);
                defaultPipelineOperationContext.setEnvironmentValue(CheckSendingMailOperation.NAME, CheckSendingMailOperation.ENV_RECIPIENT_CERTS, smtpGatewaySession.getRecipientCerts());
                defaultPipelineOperationContext.setEnvironmentValue(CheckSendingMailOperation.NAME, CheckSendingMailOperation.ENV_SENDER_ADDRESS, fromAddressStr);

                checkSendingMailOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("check sending mail finished");
                    },
                    (context, e) -> {
                        log.error("error on check sending mail", e);
                    }
                );

                boolean valid = (boolean)defaultPipelineOperationContext.getEnvironmentValue(CheckSendingMailOperation.NAME, CheckSendingMailOperation.ENV_VALID_RESULT);
                if (!valid || !logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailSignEncryptErrorContext(), message, false);
                }

                /*
                -> will be implemented later
                message = kasService.executeOutgoing(logger, message, smtpGatewaySession);
                if (smtpGatewaySession.extractNoFailureKimVersionRcpts().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext(), false, message);
                    return HookResult.DENYSOFT;
                }
                */

                byte[] msgBytes = signEncrypt(
                    logger,
                    message,
                    smtpGatewaySession.getRecipientCerts(),
                    fromSenderCerts
                );
                if (!logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailSignEncryptErrorContext(), message, false);
                }

                msgContent = new String(msgBytes);
            }
            else {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                message.writeTo(byteArrayOutputStream);
                byteArrayOutputStream.close();
                msgContent = byteArrayOutputStream.toString();
            }

            if (smtpGatewaySession.getSmtpClient().sendShortMessageData(msgContent)) {
                smtpGatewaySession.setGatewayState(EnumSmtpGatewayState.PROXY);
                smtpGatewaySession.log("mail hook ends");
                return HookResult.OK;
            } else {
                smtpGatewaySession.log("mail hook ends - error");
                return HookResult.DENY;
            }
        } catch (Exception e) {
            log.error("error on onMessage smtp gateway mail hook - " + session.getSessionID(), e);
            smtpGatewaySession.log("mail hook ends - error");
            return HookResult.DENY;
        }
    }
}
