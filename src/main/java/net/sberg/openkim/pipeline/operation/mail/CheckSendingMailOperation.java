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
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class CheckSendingMailOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(CheckSendingMailOperation.class);
    public static final String NAME = "CheckSendingMail";

    public static final String ENV_MSG = "msg";
    public static final String ENV_VALID_RESULT = "validResult";
    public static final String ENV_RECIPIENT_CERTS = "recipientCerts";
    public static final String ENV_SENDER_ADDRESS = "senderAddress";
    public static final String ENV_RESULT_MSG = "resultMsg";

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

            MimeMessage message = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MSG);
            List<X509CertificateResult> recipientCerts = (List<X509CertificateResult>) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_RECIPIENT_CERTS);
            String senderAddress = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SENDER_ADDRESS);

            if (message.getFrom() == null || message.getFrom().length == 0) {
                logger.logLine("no from header available for senderAddress: " + senderAddress);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_EXCEPTION, new IllegalStateException("no from header available for senderAddress: " + senderAddress));
            } else if (message.getFrom().length > 1) {
                logger.logLine("more than one from header available for senderAddress: " + senderAddress);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_EXCEPTION, new IllegalStateException("more than one from header available for senderAddress: " + senderAddress));
            }

            InternetAddress from = (message.getFrom() != null && message.getFrom().length > 0)
                    ? (InternetAddress) message.getFrom()[0]
                    : null;

            //reply to handling
            InternetAddress replyTo = null;
            String replyToStr = message.getHeader(MailUtils.REPLY_TO, ",");
            if (replyToStr != null) {
                try {
                    replyTo = InternetAddress.parseHeader(replyToStr, true)[0];
                } catch (Exception e) {
                    logger.logLine("error on parsing reply-to header: " + replyToStr);
                }
            }

            if (from != null && !from.getAddress().toLowerCase().equals(senderAddress)) {
                message.removeHeader(MailUtils.FROM);
                logger.logLine("from header " + from.getAddress().toLowerCase() + " not equal to senderAddress: " + senderAddress);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_EXCEPTION, new IllegalStateException("from header " + from.getAddress().toLowerCase() + " not equal to senderAddress: " + senderAddress));
            }
            if (replyTo != null && !replyTo.getAddress().toLowerCase().equals(senderAddress)) {
                logger.logLine("reply-to header " + replyTo.getAddress().toLowerCase() + " not equal to senderAddress: " + senderAddress);
                message.setReplyTo(null);
            }

            message = MailUtils.removeRecipients(logger, recipientCerts, message, Message.RecipientType.TO);
            message = MailUtils.removeRecipients(logger, recipientCerts, message, Message.RecipientType.CC);
            message = MailUtils.removeRecipients(logger, recipientCerts, message, Message.RecipientType.BCC);

            if (message.getRecipients(Message.RecipientType.TO).length == 0
                &&
                message.getRecipients(Message.RecipientType.CC).length == 0
                &&
                message.getRecipients(Message.RecipientType.BCC).length == 0) {
                logger.logLine("no recipients available");
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_EXCEPTION, new IllegalStateException("no recipients available"));
            }
            message.saveChanges();

            timeMetric.stopAndPublish();

            if (hasError(defaultPipelineOperationContext, new String[] {NAME})) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, false);
            }
            else {
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, true);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG, message);
                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the CheckSendingMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X020);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X020 + " - " + EnumErrorCode.CODE_X020.getHrText());

            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
