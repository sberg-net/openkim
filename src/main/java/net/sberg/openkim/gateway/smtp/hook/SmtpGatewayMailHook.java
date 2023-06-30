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
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.IErrorContext;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.LoadVzdCertsOperation;
import net.sberg.openkim.pipeline.operation.mail.CheckSendingMailOperation;
import net.sberg.openkim.pipeline.operation.mail.SendDsnOperation;
import net.sberg.openkim.pipeline.operation.mail.SignEncryptMailOperation;
import net.sberg.openkim.pipeline.operation.mail.kas.KasOutgoingMailOperation;
import org.apache.commons.net.smtp.SMTPReply;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SmtpGatewayMailHook implements MessageHook {

    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayMailHook.class);

    private PipelineService pipelineService;

    private SmtpGatewayMailHook() {
    }

    public SmtpGatewayMailHook(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    private boolean checkMailAddresses(SmtpGatewaySession smtpGatewaySession, Map<String, X509CertificateResult> certMap, List<String> mailAddresses, boolean senderAddresses, boolean rcptAddresses) {
        try {
            LoadVzdCertsOperation loadVzdCertsOperation = (LoadVzdCertsOperation) pipelineService.getOperation(LoadVzdCertsOperation.BUILTIN_VENDOR+"."+LoadVzdCertsOperation.NAME);
            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(smtpGatewaySession.getLogger());
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_ADDRESSES, mailAddresses);
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_SEARCH_BASE, smtpGatewaySession.getLogger().getDefaultLoggerContext().getKonnektor().getVzdSearchBase());
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_LOAD_SENDER_ADRESSES, senderAddresses);
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_LOAD_RCPT_ADRESSES, rcptAddresses);

            loadVzdCertsOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("loading certs for mailAddresses finished: "+mailAddresses.stream().collect(Collectors.joining(",")));
                },
                (context, e) -> {
                    log.error("error on loading certs for mailAddresses: "+mailAddresses.stream().collect(Collectors.joining(",")), e);
                }
            );

            List<X509CertificateResult> certs = (List)defaultPipelineOperationContext.getEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_CERTS);
            certs.stream().forEach(o -> certMap.put(o.getMailAddress(), o));

            return true;
        } catch (Exception e) {
            log.error("error on loading certs for: " + smtpGatewaySession.getSessionID() + " - " + mailAddresses.stream().collect(Collectors.joining(",")), e);
            smtpGatewaySession.log("error on loading certs for: " + smtpGatewaySession.getSessionID() + " - " + mailAddresses.stream().collect(Collectors.joining(",")));
            return false;
        }
    }

    private HookResult sendDsn(DefaultLogger logger, List<IErrorContext> errorContexts, MimeMessage originMessage, boolean senderContext) {
        try {
            Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();
            SendDsnOperation sendDsnOperation = (SendDsnOperation) pipelineService.getOperation(SendDsnOperation.BUILTIN_VENDOR+"."+SendDsnOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_ERROR_CONTEXTS, errorContexts);
            defaultPipelineOperationContext.setEnvironmentValue(SendDsnOperation.NAME, SendDsnOperation.ENV_ORIGIN_MSG, originMessage);
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

    private byte[] signEncrypt(DefaultLogger logger, MimeMessage originMimeMessage) throws Exception {
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {
            SignEncryptMailOperation signEncryptMailOperation = (SignEncryptMailOperation) pipelineService.getOperation(SignEncryptMailOperation.BUILTIN_VENDOR+"."+SignEncryptMailOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_ORIGIN_MIMEMESSAGE, originMimeMessage);

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

            String msgContent = null;
            List<IErrorContext> errorContexts = new ArrayList();
            if (!logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.NO_TI)) {

                //check sender
                List<String> senderAddresses = List.of(logger.getDefaultLoggerContext().getSenderAddress());
                if (!checkMailAddresses(smtpGatewaySession, logger.getDefaultLoggerContext().getSenderCerts(), senderAddresses, true, false)) {
                    smtpGatewaySession.getSmtpClient().rset();
                    smtpGatewaySession.log("mail hook ends - error");
                    return HookResult.DENY;
                }
                if (logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(logger.getDefaultLoggerContext().getSenderAddress())) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailaddressCertErrorContext()), message, true);
                }
                if (logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(logger.getDefaultLoggerContext().getSenderAddress())) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext()), message, true);
                }

                //check recipients
                if (!checkMailAddresses(smtpGatewaySession, logger.getDefaultLoggerContext().getRecipientCerts(), logger.getDefaultLoggerContext().getRecipientAddresses(), false, true)) {
                    smtpGatewaySession.getSmtpClient().rset();
                    smtpGatewaySession.log("mail hook ends - error");
                    return HookResult.DENY;
                }

                //check errors
                if (logger.getDefaultLoggerContext().extractNoFailureCertRcpts().isEmpty() && !logger.getDefaultLoggerContext().extractFailureCertRcpts().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailaddressCertErrorContext()), message, false);
                }
                if (logger.getDefaultLoggerContext().extractNoFailureKimVersionRcpts().isEmpty() && !logger.getDefaultLoggerContext().extractFailureKimVersionRcpts().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext()), message, false);
                }

                if (!logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isEmpty()) {
                    errorContexts.add(logger.getDefaultLoggerContext().getMailaddressCertErrorContext());
                }
                if (!logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isEmpty()) {
                    errorContexts.add(logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext());
                }

                //send rcpt to
                boolean successfulRcptTo = false;
                for (Iterator<String> iterator = logger.getDefaultLoggerContext().getRecipientCerts().keySet().iterator(); iterator.hasNext(); ) {
                    String rcptAddress = iterator.next();
                    if (logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(rcptAddress)) {
                        continue;
                    }
                    if (logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(rcptAddress)) {
                        continue;
                    }
                    int res = ((SmtpGatewaySession) session).getSmtpClient().rcpt("<" + rcptAddress + ">");
                    if (!SMTPReply.isPositiveCompletion(res)) {
                        logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext().add(rcptAddress, EnumErrorCode.CODE_X024);
                    }
                    else {
                        successfulRcptTo = true;
                    }
                }

                if (!successfulRcptTo) {
                    errorContexts.add(logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext());
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, errorContexts, message, false);
                }

                if (!logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext().isEmpty()) {
                    errorContexts.add(logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext());
                }

                //kas handling
                if (logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.FULLSTACK)) {
                    KasOutgoingMailOperation kasOutgoingMailOperation = (KasOutgoingMailOperation) pipelineService.getOperation(KasOutgoingMailOperation.BUILTIN_VENDOR+"."+KasOutgoingMailOperation.NAME);
                    DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
                    defaultPipelineOperationContext.setEnvironmentValue(KasOutgoingMailOperation.NAME, KasOutgoingMailOperation.ENV_MSG, message);
                    defaultPipelineOperationContext.setEnvironmentValue(KasOutgoingMailOperation.NAME, KasOutgoingMailOperation.ENV_SMTP_GATEWAY_SESSION, smtpGatewaySession);

                    kasOutgoingMailOperation.execute(
                        defaultPipelineOperationContext,
                        context -> {
                            log.info("handle kas finished");
                        },
                        (context, e) -> {
                            log.error("error on handling of kas", e);
                        }
                    );
                    if (logger.getDefaultLoggerContext().extractNoFailureKimVersionRcpts().isEmpty() && !logger.getDefaultLoggerContext().extractFailureKimVersionRcpts().isEmpty()) {
                        smtpGatewaySession.getSmtpClient().rset();
                        return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext()), message, false);
                    }
                    if (!logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isEmpty() && !errorContexts.contains(logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext())) {
                        errorContexts.add(logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext());
                    }

                    boolean valid = (boolean)defaultPipelineOperationContext.getEnvironmentValue(KasOutgoingMailOperation.NAME, KasOutgoingMailOperation.ENV_VALID_RESULT);
                    if (!valid) {
                        smtpGatewaySession.getSmtpClient().rset();
                        throw new IllegalStateException("error on handling of kas: "+smtpGatewaySession.getSessionID());
                    }
                    message = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(KasOutgoingMailOperation.NAME, KasOutgoingMailOperation.ENV_RESULT_MSG);
                }

                //send dsn for errors
                if (!errorContexts.isEmpty()) {
                    HookResult hookResult = sendDsn(logger, errorContexts, message, false);
                    if (hookResult.equals(HookResult.DENY)) {
                        return hookResult;
                    }
                }

                //check origin message
                CheckSendingMailOperation checkSendingMailOperation = (CheckSendingMailOperation) pipelineService.getOperation(CheckSendingMailOperation.BUILTIN_VENDOR+"."+CheckSendingMailOperation.NAME);
                DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
                defaultPipelineOperationContext.setEnvironmentValue(CheckSendingMailOperation.NAME, CheckSendingMailOperation.ENV_MSG, message);

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
                    return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailSignEncryptErrorContext()), message, false);
                }
                message = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(CheckSendingMailOperation.NAME, CheckSendingMailOperation.ENV_RESULT_MSG);

                //sign and encrpyt
                byte[] msgBytes = signEncrypt(
                    logger,
                    message
                );
                if (!logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().isEmpty()) {
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, List.of(logger.getDefaultLoggerContext().getMailSignEncryptErrorContext()), message, false);
                }

                msgContent = new String(msgBytes);
            }
            else {
                //send rcpt to
                boolean successfulRcptTo = false;
                for (Iterator<String> iterator = logger.getDefaultLoggerContext().getRecipientAddresses().iterator(); iterator.hasNext(); ) {
                    String rcptAddress = iterator.next();
                    int res = ((SmtpGatewaySession) session).getSmtpClient().rcpt("<" + rcptAddress + ">");
                    if (!SMTPReply.isPositiveCompletion(res)) {
                        logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext().add(rcptAddress, EnumErrorCode.CODE_X024);
                    }
                    else {
                        successfulRcptTo = true;
                    }
                }

                if (!successfulRcptTo) {
                    errorContexts.add(logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext());
                    smtpGatewaySession.getSmtpClient().rset();
                    return sendDsn(logger, errorContexts, message, false);
                }

                if (!logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext().isEmpty()) {
                    errorContexts.add(logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext());
                }

                //send dsn for errors
                if (!errorContexts.isEmpty()) {
                    HookResult hookResult = sendDsn(logger, errorContexts, message, false);
                    if (hookResult.equals(HookResult.DENY)) {
                        return hookResult;
                    }
                }

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
