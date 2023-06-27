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
package net.sberg.openkim.pipeline.operation.mail.part;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.Multipart;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.nio.file.Files;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class AddMailAttachmentOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(AddMailAttachmentOperation.class);
    public static final String NAME = "AddMailAttachment";

    public static final String ENV_MSG = "message";
    public static final String ENV_ATTACHMENT = "attachment";
    public static final String ENV_ATTACHMENT_NAME = "attachmentName";
    public static final String ENV_CONTENT_TYPE = "contentType";
    public static final String ENV_RESULT_MSG = "resultMsg";

    @Autowired
    private AnalyzeMailPartsOperation analyzeMailPartsOperation;

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

            MimeMessage mimeMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MSG);
            String attachmentName = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ATTACHMENT_NAME);
            String contentType = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CONTENT_TYPE);
            File attachment = (File) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ATTACHMENT);

            defaultPipelineOperationContext.setEnvironmentValue(AnalyzeMailPartsOperation.NAME, AnalyzeMailPartsOperation.ENV_MSG, mimeMessage);
            analyzeMailPartsOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    try {
                        MailPartContent result = (MailPartContent) defaultPipelineOperationContext.getEnvironmentValue(AnalyzeMailPartsOperation.NAME, AnalyzeMailPartsOperation.ENV_RESULT);

                        byte[] bytes = Files.readAllBytes(attachment.toPath());
                        MimeBodyPart attachmentMimeBodyPart = new MimeBodyPart();
                        attachmentMimeBodyPart.setContent(bytes, contentType + "; name=" + attachmentName);
                        attachmentMimeBodyPart.setDisposition("attachment; filename=" + attachmentName);

                        Multipart mixedPart = result.getFirstMultipart("mixed");
                        if (mixedPart != null) {
                            mixedPart.addBodyPart(attachmentMimeBodyPart);
                        } else {
                            MimeMultipart mixedMimeMultipart = new MimeMultipart();
                            mixedMimeMultipart.addBodyPart(attachmentMimeBodyPart);

                            Multipart multipart = result.getFirstMultipart();
                            if (multipart != null) {
                                MimeBodyPart multiPartMimeBodyPart = new MimeBodyPart();
                                multiPartMimeBodyPart.setContent(multipart);
                                mixedMimeMultipart.addBodyPart(multiPartMimeBodyPart);
                            }
                            else {
                                String content = (String) mimeMessage.getContent();
                                ContentType contentTypeObj = new ContentType(mimeMessage.getHeader("Content-Type", null));
                                String charset = contentTypeObj.getParameterList().get("charset");

                                MimeBodyPart stringMimeBodyPart = new MimeBodyPart();
                                stringMimeBodyPart.setText(content, charset, contentTypeObj.getSubType());
                                mixedMimeMultipart.addBodyPart(stringMimeBodyPart);
                            }
                            mimeMessage.setContent(mixedMimeMultipart);
                        }

                        mimeMessage.saveChanges();
                        defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG, mimeMessage);
                    }
                    catch (Exception e) {
                        defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_EXCEPTION, e);
                    }
                },
                (context, e) -> {
                    defaultPipelineOperationContext.setEnvironmentValue(AnalyzeMailPartsOperation.NAME, ENV_EXCEPTION, e);
                }
            );

            timeMetric.stopAndPublish();

            if (hasError(defaultPipelineOperationContext, new String[] {NAME,analyzeMailPartsOperation.getName()})) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
            }
            else {
                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the AddMailAttachmentOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
