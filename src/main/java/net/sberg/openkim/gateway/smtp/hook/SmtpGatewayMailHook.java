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

import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.ICommonConstants;
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
import net.sberg.openkim.pipeline.operation.konnektor.vzd.LoadVzdCertsOperation;
import net.sberg.openkim.pipeline.operation.mail.CheckSendingMailOperation;
import net.sberg.openkim.pipeline.operation.mail.SendDsnOperation;
import net.sberg.openkim.pipeline.operation.mail.SignEncryptMailOperation;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(smtpGatewaySession.getLogger());
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
            SignEncryptMailOperation signEncryptMailOperation = (SignEncryptMailOperation) pipelineService.getOperation(SignEncryptMailOperation.BUILTIN_VENDOR+"."+SignEncryptMailOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_ORIGIN_MIMEMESSAGE, originMimeMessage);
            defaultPipelineOperationContext.setEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_RECIPIENT_CERTS, recipientCerts);
            defaultPipelineOperationContext.setEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_FROM_SENDER_CERTS, fromSenderCerts);

            AtomicInteger failedCounter = new AtomicInteger();
            signEncryptMailOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("sign and encrypt mail finished");
                },
                (context, e) -> {
                    log.error("error on mail signing and encrypting", e);
                    failedCounter.incrementAndGet();
                }
            );

            if (failedCounter.get() == 0) {
                return (byte[])defaultPipelineOperationContext.getEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_RESULT_MSG_BYTES);
            }
            else {
                throw new IllegalStateException("error on mail signing and encrypting");
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

            String fromAddressStr = smtpGatewaySession.getFromAddressStr().toLowerCase();
            String msgContent = null;
            if (!logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.NO_TI)) {

                if (smtpGatewaySession.extractNoFailureCertRcpts().isEmpty() || !smtpGatewaySession.extractFailureCertRcpts().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), message, false);
                }

                //add sender certs -> check kim version
                List<X509CertificateResult> fromSenderCerts = new ArrayList<>();
                try {
                    //cert check 4 from address
                    List<String> fromSenderAddress = new ArrayList<>();
                    fromSenderAddress.add(fromAddressStr);

                    smtpGatewaySession.log("load certs for from: " + fromAddressStr);

                    LoadVzdCertsOperation loadVzdCertsOperation = (LoadVzdCertsOperation) pipelineService.getOperation(LoadVzdCertsOperation.BUILTIN_VENDOR+"."+LoadVzdCertsOperation.NAME);
                    DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
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
                DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
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
