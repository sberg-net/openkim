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
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.IErrorContext;
import net.sberg.openkim.log.error.MailDecryptErrorContext;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class CreateEmbeddedMessageRfc822Operation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(CreateEmbeddedMessageRfc822Operation.class);
    public static final String NAME = "CreateEmbeddedMessageRfc822";

    public static final String ENV_ORIGIN_MSG = "originMessage";
    public static final String ENV_ERROR_CONTEXT = "errorContext";
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

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            MimeMessage originMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ORIGIN_MSG);
            IErrorContext errorContext = (IErrorContext) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ERROR_CONTEXT);

            MimeMessage mimeMessage = null;

            if (errorContext instanceof MailDecryptErrorContext) {

                List<EnumErrorCode> codes = ((MailDecryptErrorContext) errorContext).getErrorCodes();

                mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
                mimeMessage.setSubject("Beim Entschlüsseln ist ein Fehler aufgetreten. Die Gründe werden aufgeführt.");

                MimeMultipart mixedMultiPart = new MimeMultipart();

                //error texts
                MimeBodyPart textMimeBodyPart = new MimeBodyPart();
                mixedMultiPart.addBodyPart(textMimeBodyPart);
                StringBuilder contentBuilder = new StringBuilder();
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(MailUtils.X_KIM_DECRYPTION_RESULT, code.getId());
                    contentBuilder.append(code.getHrText()).append("\n");
                }
                textMimeBodyPart.setText(contentBuilder.toString(), "UTF-8", "plain");

                //origin message as message/rfc822
                MimeBodyPart messageMimeBodyPart = new MimeBodyPart();
                mixedMultiPart.addBodyPart(messageMimeBodyPart);
                messageMimeBodyPart.setContent(originMessage, "message/rfc822");

                mimeMessage.setContent(mixedMultiPart);

                //set header
                String date = (originMessage.getHeader(MailUtils.DATE) != null && originMessage.getHeader(MailUtils.DATE).length > 0) ? originMessage.getHeader(MailUtils.DATE)[0] : null;
                InternetAddress from = (originMessage.getFrom() != null && originMessage.getFrom().length > 0) ? (InternetAddress) originMessage.getFrom()[0] : null;

                //reply to handling
                InternetAddress replyTo = null;
                String replyToStr = originMessage.getHeader(MailUtils.REPLY_TO, ",");
                if (replyToStr != null) {
                    try {
                        replyTo = InternetAddress.parseHeader(replyToStr, true)[0];
                    } catch (Exception e) {
                        logger.logLine("error on parsing reply-to header: " + replyToStr);
                    }
                }

                InternetAddress sender = (InternetAddress) originMessage.getSender();

                mimeMessage.setHeader(MailUtils.DATE, date);
                if (from != null) {
                    mimeMessage.addFrom(new Address[]{from});
                }
                if (sender != null) {
                    logger.logLine("set sender: " + sender);
                    mimeMessage.setSender(sender);
                }
                if (replyTo != null) {
                    logger.logLine("set reply-to: " + replyTo.getAddress());
                    mimeMessage.setReplyTo(new Address[]{replyTo});
                }

                mimeMessage.setRecipients(Message.RecipientType.TO, originMessage.getRecipients(Message.RecipientType.TO));
                mimeMessage.setRecipients(Message.RecipientType.CC, originMessage.getRecipients(Message.RecipientType.CC));
                mimeMessage.setRecipients(Message.RecipientType.BCC, originMessage.getRecipients(Message.RecipientType.BCC));

                mimeMessage.saveChanges();
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();

            timeMetric.stopAndPublish();
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG_BYTES, result);
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the CreateEmbeddedMessageRfc822Operation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
