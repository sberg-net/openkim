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
import net.sberg.openkim.mail.MailService;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.gateway.smtp.EnumSmtpGatewayState;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.kas.KasService;
import net.sberg.openkim.konnektor.vzd.VzdService;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.protocols.smtp.MailEnvelope;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SmtpGatewayMailHook implements MessageHook {

    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayMailHook.class);

    private KasService kasService;
    private MailService mailService;
    private VzdService vzdService;

    private SmtpGatewayMailHook() {
    }

    public SmtpGatewayMailHook(KasService kasService, MailService mailService, VzdService vzdService) {
        this.kasService = kasService;
        this.mailService = mailService;
        this.vzdService = vzdService;
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
                mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), false, message);
                return HookResult.DENYSOFT;
            }

            //add sender certs -> check kim version
            List<X509CertificateResult> fromSenderCerts = null;
            String fromAddressStr = smtpGatewaySession.getFromAddressStr();

            try {

                //cert check
                List<String> fromSenderAddress = new ArrayList<>();
                String senderAddressStr = null;
                if (message.getSender() != null) {
                    senderAddressStr = ((InternetAddress) message.getSender()).getAddress();
                    fromSenderAddress.add(senderAddressStr);
                }
                fromSenderAddress.add(fromAddressStr);

                smtpGatewaySession.log("load certs for from: " + fromAddressStr);
                fromSenderCerts = vzdService.loadCerts(logger, fromSenderAddress, true, false);
                smtpGatewaySession.log("load certs ending for sender: " + fromAddressStr);

                if (logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(fromAddressStr)
                    || (senderAddressStr != null && logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(senderAddressStr))
                ) {
                    mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), true, message);
                    return HookResult.DENYSOFT;
                }

                if (logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(fromAddressStr)
                    || (senderAddressStr != null && logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(senderAddressStr))
                ) {
                    mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext(), true, message);
                    return HookResult.DENYSOFT;
                }
            } catch (Exception e) {
                log.error("error on loading sender cert for: " + session.getSessionID() + " - " + fromAddressStr, e);
                smtpGatewaySession.log("error on loading sender cert for: " + session.getSessionID() + " - " + fromAddressStr);

                smtpGatewaySession.getSmtpClient().rset();

                if (logger.getDefaultLoggerContext().getMailaddressCertErrorContext().isError(fromAddressStr)) {
                    mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressCertErrorContext(), true, message);
                }
                if (logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext().isError(fromAddressStr)) {
                    mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext(), true, message);
                }

                return HookResult.DENYSOFT;
            }

            message = mailService.checkOriginMsg(logger, message, smtpGatewaySession.getRecipientCerts(), fromAddressStr, false);
            if (!logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().isEmpty()) {
                smtpGatewaySession.getSmtpClient().rset();
                mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailSignEncryptErrorContext(), false, message);
                return HookResult.DENYSOFT;
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

            byte[] signEncryptedBytes = mailService.signEncrypt(
                logger,
                message,
                smtpGatewaySession.getRecipientCerts(),
                fromSenderCerts,
                true
            );
            if (!logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().isEmpty()) {
                smtpGatewaySession.getSmtpClient().rset();
                mailService.sendDsn(smtpGatewaySession, logger.getDefaultLoggerContext().getMailSignEncryptErrorContext(), false, message);
                return HookResult.DENYSOFT;
            }

            String content = new String(signEncryptedBytes);

            if (smtpGatewaySession.getSmtpClient().sendShortMessageData(content)) {
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
