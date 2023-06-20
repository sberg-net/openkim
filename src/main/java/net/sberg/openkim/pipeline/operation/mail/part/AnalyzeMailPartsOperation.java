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

import com.sun.mail.dsn.DeliveryStatus;
import com.sun.mail.dsn.DispositionNotification;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class AnalyzeMailPartsOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeMailPartsOperation.class);
    public static final String NAME = "AnalyzeMailParts";

    public static final String ENV_MSG = "message";
    public static final String ENV_RESULT = "result";

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
            MailPartContent result = create(mimeMessage, mimeMessage, 0, EnumMailPartContentType.MimeMessage);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT, result);
            timeMetric.stopAndPublish();
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the AnalyzeMailPartsOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }

    private MailPartContent create(Object mimePart, Object contentPart, int idx, EnumMailPartContentType mimePartContentType) throws Exception {

        MailPartContent mimePartContent = new MailPartContent();

        Enumeration<String> headerLines = null;
        if (mimePart instanceof MimeBodyPart) {
            headerLines = ((MimeBodyPart) mimePart).getAllHeaderLines();
        } else if (mimePart instanceof MimeMessage) {
            headerLines = ((MimeMessage) mimePart).getAllHeaderLines();
        }
        if (headerLines != null) {
            while (headerLines.hasMoreElements()) {
                String header = headerLines.nextElement();
                if (header.toLowerCase().startsWith("content-type:")) {
                    mimePartContent.setContentTypeHeader(header);
                }
                if (header.toLowerCase().startsWith("content-disposition:") && header.toLowerCase().contains("attachment;")) {
                    mimePartContent.setContentDispositionHeader(header);
                    mimePartContent.setAttachmentInline(false);
                }
                if (header.toLowerCase().startsWith("content-disposition:") && header.toLowerCase().contains("inline;")) {
                    mimePartContent.setContentDispositionHeader(header);
                    mimePartContent.setAttachmentInline(true);
                }
                mimePartContent.getHeader().add(header);
            }
        }

        mimePartContent.setMimePart(mimePart);
        mimePartContent.setContentPart(contentPart);
        mimePartContent.setIdx(idx);
        mimePartContent.setMimePartContentType(mimePartContentType);

        if (mimePartContentType.equals(EnumMailPartContentType.MimeMessage)) {
            Object content = ((MimeMessage) contentPart).getContent();
            if (content instanceof Multipart) {
                Multipart multipart = (Multipart) ((MimeMessage) contentPart).getContent();
                mimePartContent.getChildren().add(create(contentPart, multipart, 0, EnumMailPartContentType.Multipart));
            } else if (content instanceof InputStream) {
                mimePartContent.setAttachment(true);
                if (((MimeMessage) contentPart).getEncoding().equalsIgnoreCase("base64")) {
                    mimePartContent.setAttachementSize(((MimeMessage) contentPart).getSize() / 1.37);
                } else {
                    mimePartContent.setAttachementSize(((MimeMessage) contentPart).getSize());
                }

                //try to get a message
                try {
                    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()), (InputStream) content);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (final InputStream is = mimeMessage.getInputStream()) {
                        for (int c = is.read(); c != -1; c = is.read()) {
                            bos.write(c);
                        }
                    }
                    final byte[] msg = bos.toByteArray();
                    mimeMessage = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(msg));
                    mimePartContent.getChildren().add(create(mimeMessage, null, 0, EnumMailPartContentType.MimeMessage));
                } catch (Exception e) {
                }

            } else {
                mimePartContent.setNotAttachementSize(((MimeMessage) contentPart).getSize());
            }
        } else if (mimePartContentType.equals(EnumMailPartContentType.Multipart)) {
            int count = ((Multipart) contentPart).getCount();
            MimeBodyPart bodyPart;

            for (int i = 0; i < count; i++) {
                bodyPart = (MimeBodyPart) ((Multipart) contentPart).getBodyPart(i);
                Object content = bodyPart.getContent();
                MailPartContent childMailPartContent = null;
                if (content instanceof Multipart) {
                    childMailPartContent = create(bodyPart, content, i, EnumMailPartContentType.Multipart);
                } else if (content instanceof String) {
                    childMailPartContent = create(bodyPart, content, i, EnumMailPartContentType.Text);
                    if (bodyPart.getDisposition() != null && bodyPart.getDisposition().toLowerCase().equals(EnumMailPartDispositionType.Xkas.getName())) {
                        childMailPartContent.setMimePartDispositionType(EnumMailPartDispositionType.Xkas);
                    }
                } else if (content instanceof InputStream || content instanceof byte[]) {
                    childMailPartContent = create(bodyPart, content, i, EnumMailPartContentType.Binary);
                } else if (content instanceof MimeMessage) {
                    childMailPartContent = create(bodyPart, content, i, EnumMailPartContentType.MimeMessage);
                } else if (content instanceof DispositionNotification) {
                    childMailPartContent = create(bodyPart, content, i, EnumMailPartContentType.DispositionNotification);
                } else if (content instanceof DeliveryStatus) {
                    childMailPartContent = create(bodyPart, content, i, EnumMailPartContentType.DeliveryStatus);
                } else {
                    throw new IllegalStateException("following content type not handled: " + bodyPart.getContentType() + " - content class: " + content.getClass().getName());
                }

                if (childMailPartContent.getMimePartContentType().equals(EnumMailPartContentType.Binary) && bodyPart.getEncoding().equalsIgnoreCase("base64")) {
                    childMailPartContent.setAttachementSize(bodyPart.getSize() / 1.37);
                    childMailPartContent.setAttachment(true);
                } else if (childMailPartContent.getMimePartContentType().equals(EnumMailPartContentType.Binary)) {
                    childMailPartContent.setAttachementSize(bodyPart.getSize());
                    childMailPartContent.setAttachment(true);
                } else {
                    childMailPartContent.setNotAttachementSize(bodyPart.getSize());
                }

                mimePartContent.getChildren().add(childMailPartContent);

            }
        }

        return mimePartContent;
    }
}
