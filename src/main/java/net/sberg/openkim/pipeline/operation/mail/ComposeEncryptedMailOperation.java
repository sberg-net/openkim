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

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Address;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class ComposeEncryptedMailOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(ComposeEncryptedMailOperation.class);
    public static final String NAME = "ComposeEncryptedMail";

    public static final String ENV_ENCRYPTED_MSG = "encryptedMsg";
    public static final String ENV_RECIPIENT_CERTS = "recipientCerts";
    public static final String ENV_ORIGIN_MSG = "originMessage";
    public static final String ENV_RESULT_MSG_BYTES = "resultMessage";

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

            MimeMessage encryptedMsg = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ENCRYPTED_MSG);
            MimeMessage originMimeMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ORIGIN_MSG);
            List<X509CertificateResult> recipientCerts = (List<X509CertificateResult>) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_RECIPIENT_CERTS);

            //create result message
            //analyze origin header
            String date = (originMimeMessage.getHeader(MailUtils.DATE) != null && originMimeMessage.getHeader(MailUtils.DATE).length > 0)
                    ? originMimeMessage.getHeader(MailUtils.DATE)[0]
                    : null;
            String messageId = originMimeMessage.getMessageID();
            InternetAddress from = (originMimeMessage.getFrom() != null && originMimeMessage.getFrom().length > 0)
                    ? (InternetAddress) originMimeMessage.getFrom()[0]
                    : null;

            //reply to handling
            InternetAddress replyTo = null;
            String replyToStr = originMimeMessage.getHeader(MailUtils.REPLY_TO, ",");
            if (replyToStr != null) {
                try {
                    replyTo = InternetAddress.parseHeader(replyToStr, true)[0];
                } catch (Exception e) {
                    logger.logLine("error on parsing reply-to header: " + replyToStr);
                }
            }

            InternetAddress sender = (InternetAddress) originMimeMessage.getSender();
            String dienstkennung = (originMimeMessage.getHeader(MailUtils.X_KIM_DIENSTKENNUNG) != null && originMimeMessage.getHeader(MailUtils.X_KIM_DIENSTKENNUNG).length > 0)
                    ? originMimeMessage.getHeader(MailUtils.X_KIM_DIENSTKENNUNG)[0]
                    : MailUtils.X_KIM_DIENSTKENNUNG_KIM_MAIL;

            MimeMessage resultMsg = new MimeMessage(Session.getInstance(new Properties()));
            resultMsg.setContent(encryptedMsg, CMSUtils.SMIME_CONTENT_AUTH_ENVELOPED_TYPE);
            resultMsg.setDisposition(CMSUtils.SMIME_DISPOSITION);
            resultMsg.setHeader(MailUtils.DATE, date);
            if (from != null) {
                resultMsg.addFrom(new Address[]{from});
            }
            if (sender != null) {
                logger.logLine("set sender: " + sender);
                resultMsg.setSender(sender);
            }
            if (replyTo != null) {
                logger.logLine("set reply-to: " + replyTo.getAddress());
                resultMsg.setReplyTo(new Address[]{replyTo});
            }
            resultMsg.addHeader(MailUtils.X_KIM_DIENSTKENNUNG, dienstkennung);
            resultMsg = MailUtils.setRecipients(logger, recipientCerts, originMimeMessage, resultMsg, Message.RecipientType.TO);
            resultMsg = MailUtils.setRecipients(logger, recipientCerts, originMimeMessage, resultMsg, Message.RecipientType.CC);
            resultMsg = MailUtils.setRecipients(logger, recipientCerts, originMimeMessage, resultMsg, Message.RecipientType.BCC);
            resultMsg.addHeader(MailUtils.X_KOM_LE_VERSION, konfiguration.getXkimPtShortVersion().getOfficalVersion());

            resultMsg.setSubject(MailUtils.SUBJECT_KOM_LE_NACHRICHT);
            resultMsg.saveChanges();
            resultMsg.setHeader("Message-ID", messageId);

            //copy x-kim headers
            Enumeration<Header> headerEnum = originMimeMessage.getAllHeaders();
            while (headerEnum.hasMoreElements()) {
                Header header = headerEnum.nextElement();
                if (header.getName().toLowerCase().startsWith("X-Kim".toLowerCase())) {
                    resultMsg.setHeader(header.getName(), header.getValue());
                }
            }

            //Expires
            resultMsg.setHeader(MailUtils.EXPIRES, ZonedDateTime.now().plusDays(
                    logger.getDefaultLoggerContext().getAccountLimit() == null ? 90 : logger.getDefaultLoggerContext().getAccountLimit().getDataTimeToLive()
            ).format(MailUtils.RFC822_DATE_FORMAT));

            resultMsg.setHeader(MailUtils.X_KIM_CMVERSION, konfiguration.getXkimCmVersion());
            resultMsg.setHeader(MailUtils.X_KIM_PTVERSION, konfiguration.getXkimPtVersion());
            resultMsg.setHeader(MailUtils.X_KIM_KONVERSION, MessageFormat.format(
                    "<{0}><{1}><{2}><{3}><{4}>",
                    konnektor.getProductName(),
                    konnektor.getProductType(),
                    konnektor.getProductTypeVersion(),
                    konnektor.getHwVersion(),
                    konnektor.getFwVersion()
            ));

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            resultMsg.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG_BYTES, result);
            timeMetric.stopAndPublish();

            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the ComposeEncryptedMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X012);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X012 + " - " + EnumErrorCode.CODE_X012.getHrText());
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
