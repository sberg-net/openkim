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
package net.sberg.openkim.gateway.smtp;

import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konfiguration.EnumGatewayTIMode;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.log.error.MailaddressCertErrorContext;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSessionImpl;

import java.io.File;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class SmtpGatewaySession extends SMTPSessionImpl {

    private static final DateTimeFormatter dtFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private AuthenticatingSMTPClient smtpClient;
    private EnumSmtpGatewayState gatewayState = EnumSmtpGatewayState.UNKNOWN;

    private final DefaultLogger logger;
    private String id;

    private String fromAddressStr;
    private final List<X509CertificateResult> recipientCerts = new ArrayList<>();

    public SmtpGatewaySession(ProtocolTransport transport, SMTPConfiguration config) {
        super(transport, config);

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        LogService logService = ((SmtpGatewayConfiguration) getConfiguration()).getLogService();
        Konfiguration konfiguration = ((SmtpGatewayConfiguration) getConfiguration()).getKonfiguration();

        Konnektor konnektor = null;
        if (konfiguration.getKonnektoren().size() > 0) {
            konnektor = konfiguration.getKonnektoren().get(0);
        }

        if (konnektor != null) {
            logger = logService.createLogger(
                defaultLoggerContext.buildKonnektor(konnektor).buildKonfiguration(konfiguration).buildHtmlMode(true).buildWriteInFile(konfiguration.isWriteSmtpCmdLogFile()).buildFileName(MessageFormat.format(ICommonConstants.SMTP_LOG_FILENAME, getSessionID()))
            );
        } else {
            logger = logService.createLogger(
                defaultLoggerContext.buildKonfiguration(konfiguration).buildHtmlMode(true).buildWriteInFile(konfiguration.isWriteSmtpCmdLogFile()).buildFileName(MessageFormat.format(ICommonConstants.SMTP_LOG_FILENAME, getSessionID()))
            );
        }

        if (!new File(ICommonConstants.SMTP_LOG_DIR).exists()) {
            new File(ICommonConstants.SMTP_LOG_DIR).mkdirs();
        }
    }

    /* **********************+ getter, setter **********************************************/
    public List<X509CertificateResult> getRecipientCerts() {
        return recipientCerts;
    }

    @Override
    public String getSessionID() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public int getSmtpClientIdleTimeoutInSeconds() {
        return ((SmtpGatewayConfiguration) getConfiguration()).getKonfiguration().getSmtpClientIdleTimeoutInSeconds();
    }

    public EnumGatewayTIMode getGatewayTIMode() {
        return ((SmtpGatewayConfiguration) getConfiguration()).getKonfiguration().getGatewayTIMode();
    }

    public AuthenticatingSMTPClient getSmtpClient() {
        return smtpClient;
    }

    public void setSmtpClient(AuthenticatingSMTPClient smtpClient) {
        this.smtpClient = smtpClient;
    }

    public EnumSmtpGatewayState getGatewayState() {
        return gatewayState;
    }

    public void setGatewayState(EnumSmtpGatewayState gatewayState) {
        this.gatewayState = gatewayState;
    }

    public void setFromAddressStr(String fromAddressStr) {
        this.fromAddressStr = fromAddressStr;
    }

    public String getFromAddressStr() {
        return fromAddressStr;
    }
    /* **********************+ getter, setter **********************************************/

    /* ******************************************* extract methods ***********************/
    public List<String> extractFailureCertRcpts() {
        MailaddressCertErrorContext mailaddressCertErrorContext = logger.getDefaultLoggerContext().getMailaddressCertErrorContext();
        return new ArrayList<>(mailaddressCertErrorContext.getRcptAddresses());
    }

    public List<X509CertificateResult> extractNoFailureCertRcpts() {
        List<X509CertificateResult> res = new ArrayList<>();
        List<String> failureRcpts = extractFailureCertRcpts();

        for (Iterator<X509CertificateResult> iterator = getRecipientCerts().iterator(); iterator.hasNext(); ) {
            X509CertificateResult x509CertificateResult = iterator.next();
            if (!failureRcpts.contains(x509CertificateResult.getMailAddress())) {
                res.add(x509CertificateResult);
            }
        }
        return res;
    }

    public List<String> extractFailureKimVersionRcpts() {
        MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext();
        return new ArrayList<>(mailaddressKimVersionErrorContext.getRcptAddresses());
    }

    public List<X509CertificateResult> extractNoFailureKimVersionRcpts() {
        List<X509CertificateResult> res = new ArrayList<>();
        List<String> failureRcpts = extractFailureKimVersionRcpts();

        for (Iterator<X509CertificateResult> iterator = getRecipientCerts().iterator(); iterator.hasNext(); ) {
            X509CertificateResult x509CertificateResult = iterator.next();
            if (!failureRcpts.contains(x509CertificateResult.getMailAddress())) {
                res.add(x509CertificateResult);
            }
        }
        return res;
    }
    /* ******************************************* extract methods ***********************/

    public void log(String content) {
        StringBuilder logContent = new StringBuilder();
        logContent.append(dtFormatter.format(LocalDateTime.now()));
        if (((SmtpGatewayConfiguration) getConfiguration()).getKonfiguration().isLogPersonalInformations()
            && logger.getDefaultLoggerContext().getMailServerUsername() != null
        ) {
            logContent.append(" ");
            logContent.append(logger.getDefaultLoggerContext().getMailServerUsername());
        }
        if (logger.getDefaultLoggerContext().getMailServerHost() != null) {
            logContent.append(" ");
            logContent.append(logger.getDefaultLoggerContext().getMailServerHost());
        }
        if (logger.getDefaultLoggerContext().getMailServerPort() != null) {
            logContent.append(" ");
            logContent.append(logger.getDefaultLoggerContext().getMailServerPort());
        }
        logContent.append(" ");
        logContent.append(content);
        logger.logLine(logContent.toString());
    }

    public DefaultLogger getLogger() {
        return logger;
    }

    public void cleanup() {
        ((SmtpGatewayConfiguration) getConfiguration()).getLogService().removeLogger(logger.getId());
    }
}
