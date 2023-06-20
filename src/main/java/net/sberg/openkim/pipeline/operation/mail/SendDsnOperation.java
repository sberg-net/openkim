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

import net.sberg.openkim.common.EnumMailConnectionSecurity;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.*;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class SendDsnOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(SendDsnOperation.class);
    public static final String NAME = "SendDsn";

    public static final String ENV_ERROR_CONTEXT = "errorContext";
    public static final String ENV_ORIGIN_MSG = "originMessage";
    public static final String ENV_SENDER_CTX = "senderContext";
    public static final String ENV_SMTP_GATEWAY_SESSION = "smtpGatewaySession";
    public static final String ENV_SSL_CONTEXT = "sslContext";

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
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            MimeMessage mimeMessage = null;

            IErrorContext errorContext = (IErrorContext) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ERROR_CONTEXT);
            MimeMessage originMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ORIGIN_MSG);
            SmtpGatewaySession smtpGatewaySession = (SmtpGatewaySession) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SMTP_GATEWAY_SESSION);
            boolean senderContext = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SENDER_CTX);
            SSLContext sslContext = (SSLContext) defaultPipelineOperationContext.getEnvironmentValue(SendDsnOperation.NAME, ENV_SSL_CONTEXT);

            if (errorContext instanceof MailSignEncryptErrorContext) {

                List<EnumErrorCode> codes = ((MailSignEncryptErrorContext) errorContext).getErrorCodes();

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("Es sind Fehler beim Signieren und Verschlüsseln der Mail aufgetreten.\r\n");
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DsnHelper.createMessage(
                        originMessage,
                        smtpGatewaySession.getFromAddressStr(),
                        contentBuilder.toString(),
                        "",
                        "",
                        "failed",
                        originMessage.getSubject(),
                        konfiguration.getXkimCmVersion(),
                        konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(MailUtils.X_KIM_FEHLERMELDUNG, code.getId());
                }
            }
            else if (senderContext) {

                String senderAddress = null;
                List<EnumErrorCode> codes = null;

                if (errorContext instanceof MailaddressCertErrorContext) {
                    senderAddress = ((MailaddressCertErrorContext) errorContext).getFromSenderAddresses().get(0);
                    codes = ((MailaddressCertErrorContext) errorContext).getAddressErrors().get(senderAddress);
                } else if (errorContext instanceof MailaddressKimVersionErrorContext) {
                    senderAddress = ((MailaddressCertErrorContext) errorContext).getFromSenderAddresses().get(0);
                    codes = ((MailaddressCertErrorContext) errorContext).getAddressErrors().get(senderAddress);
                }

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder
                        .append("Die Mail konnte nicht versandt werden. Für den Sender ")
                        .append(senderAddress)
                        .append(" wurden beim Versand Probleme festgestellt.\r\n");
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DsnHelper.createMessage(
                        originMessage,
                        smtpGatewaySession.getFromAddressStr(),
                        contentBuilder.toString(),
                        "",
                        "",
                        "failed",
                        originMessage.getSubject(),
                        konfiguration.getXkimCmVersion(),
                        konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(MailUtils.X_KIM_FEHLERMELDUNG, code.getId());
                }
            } else {

                List<String> rcptAddresses = null;
                Map<String, List<EnumErrorCode>> addressErrors = null;

                if (errorContext instanceof MailaddressCertErrorContext) {
                    rcptAddresses = ((MailaddressCertErrorContext) errorContext).getRcptAddresses();
                    addressErrors = ((MailaddressCertErrorContext) errorContext).getAddressErrors();
                } else if (errorContext instanceof MailaddressKimVersionErrorContext) {
                    rcptAddresses = ((MailaddressKimVersionErrorContext) errorContext).getRcptAddresses();
                    addressErrors = ((MailaddressKimVersionErrorContext) errorContext).getAddressErrors();
                }

                StringBuilder contentBuilder = new StringBuilder();
                if (smtpGatewaySession.extractNoFailureCertRcpts().isEmpty() && !smtpGatewaySession.extractFailureCertRcpts().isEmpty()) {
                    contentBuilder.append("Die Mail konnte nicht versandt werden. Für alle Empfänger wurden beim Versand Probleme festgestellt:\r\n");
                }
                if (!smtpGatewaySession.extractNoFailureCertRcpts().isEmpty() && !smtpGatewaySession.extractFailureCertRcpts().isEmpty()) {
                    contentBuilder.append("Die Mail konnte versandt werden. Für einige Empfänger wurden beim Versand Probleme festgestellt:\r\n");
                }
                contentBuilder.append(String.join(",", rcptAddresses));
                contentBuilder.append("\r\n");

                List<EnumErrorCode> codes = new ArrayList<>();
                for (Iterator<String> iterator = rcptAddresses.iterator(); iterator.hasNext(); ) {
                    String address = iterator.next();
                    List<EnumErrorCode> addressCodes = addressErrors.get(address);
                    for (Iterator<EnumErrorCode> enumErrorCodeIterator = addressCodes.iterator(); enumErrorCodeIterator.hasNext(); ) {
                        EnumErrorCode code = enumErrorCodeIterator.next();
                        if (!codes.contains(code)) {
                            codes.add(code);
                        }
                    }
                }
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DsnHelper.createMessage(
                        originMessage,
                        smtpGatewaySession.getFromAddressStr(),
                        contentBuilder.toString(),
                        "",
                        "",
                        "failed",
                        originMessage.getSubject(),
                        konfiguration.getXkimCmVersion(),
                        konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(MailUtils.X_KIM_FEHLERMELDUNG, code.getId());
                }
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(byteArrayOutputStream);
            byteArrayOutputStream.close();

            AuthenticatingSMTPClient client = null;
            if (sslContext != null) {
                client = new AuthenticatingSMTPClient(true, sslContext);
            }
            else {
                client = new AuthenticatingSMTPClient("TLS", konfiguration.getSmtpGatewayConnectionSec().equals(EnumMailConnectionSecurity.SSLTLS));
            }
            client.connect(logger.getDefaultLoggerContext().getMailServerHost(), Integer.parseInt(logger.getDefaultLoggerContext().getMailServerPort()));
            client.login();
            boolean res = client.auth(
                    AuthenticatingSMTPClient.AUTH_METHOD.LOGIN,
                    logger.getDefaultLoggerContext().getMailServerUsername(),
                    logger.getDefaultLoggerContext().getMailServerPassword()
            );
            logger.logLine("dsn sending - smtp auth: " + res);
            if (res) {
                String content = byteArrayOutputStream.toString();
                String[] recs = new String[]{smtpGatewaySession.getFromAddressStr()};
                res = client.sendSimpleMessage(smtpGatewaySession.getFromAddressStr(), recs, content);
                logger.logLine("dsn sending - smtp sent: " + res);
            }

            client.quit();
            timeMetric.stopAndPublish();
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the SendDsnOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
