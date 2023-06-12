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
package net.sberg.openkim.mail;

import com.sun.mail.dsn.DeliveryStatus;
import com.sun.mail.dsn.DispositionNotification;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

@Service
public class MailPartContentService {

    private static final Logger log = LoggerFactory.getLogger(MailPartContentService.class);

    public MimeMessage addAttachment(DefaultLogger logger, MimeMessage mimeMessage, File attachment, String fileName, String contentTypeStr) {
        TimeMetric timeMetric = null;
        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailPartContentService:addAttachment");

            byte[] bytes = Files.readAllBytes(attachment.toPath());
            MimeBodyPart attachmentMimeBodyPart = new MimeBodyPart();
            attachmentMimeBodyPart.setContent(bytes, contentTypeStr + "; name=" + fileName);
            attachmentMimeBodyPart.setDisposition("attachment; filename=" + fileName);

            MailPartContent result = analyze(logger, mimeMessage);
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
                } else {
                    String content = (String) mimeMessage.getContent();
                    ContentType contentType = new ContentType(mimeMessage.getHeader("Content-Type", null));
                    String charset = contentType.getParameterList().get("charset");

                    MimeBodyPart stringMimeBodyPart = new MimeBodyPart();
                    stringMimeBodyPart.setText(content, charset, contentType.getSubType());
                    mixedMimeMultipart.addBodyPart(stringMimeBodyPart);

                }
                mimeMessage.setContent(mixedMimeMultipart);
            }

            mimeMessage.saveChanges();

            timeMetric.stopAndPublish();

            return mimeMessage;
        } catch (Exception e) {
            log.error("error on adding text for the mimeMessage", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw new IllegalStateException("error on adding text for the mimeMessage", e);
        }
    }

    private MimeBodyPart addText(MimeBodyPart mimeBodyPart, String text) throws Exception {
        if (mimeBodyPart.isMimeType("text/html")) {
            String html = text.replaceAll("\n", "<br>");
            String content = (String) mimeBodyPart.getContent();
            int idx = content.toLowerCase().lastIndexOf("</body>");
            if (idx != -1) {
                String b = content.substring(0, idx);
                String e = content.substring(idx + "</body>".length());
                content = b + html + "</body>" + e;
            }
            mimeBodyPart.setContent(content, mimeBodyPart.getContentType());
        } else {
            String content = (String) mimeBodyPart.getContent();
            content = content + "\n" + text;
            mimeBodyPart.setContent(content, mimeBodyPart.getContentType());
        }
        return mimeBodyPart;
    }

    public MimeMessage addText(DefaultLogger logger, MimeMessage mimeMessage, String text) {
        TimeMetric timeMetric = null;
        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailPartContentService:addText");

            MailPartContent result = analyze(logger, mimeMessage);
            List<MimeBodyPart> parts = result.getBodyParts("text/*");
            if (parts.isEmpty() && mimeMessage.getContent() instanceof String) {
                String content = (String) mimeMessage.getContent();
                content = content + "\n" + text;
                mimeMessage.setContent(content, mimeMessage.getContentType());
            } else if (!parts.isEmpty()) {
                for (Iterator<MimeBodyPart> iterator = parts.iterator(); iterator.hasNext(); ) {
                    MimeBodyPart bodyPart = iterator.next();
                    bodyPart = addText(bodyPart, text);
                }
            }

            mimeMessage.saveChanges();

            timeMetric.stopAndPublish();

            return mimeMessage;
        } catch (Exception e) {
            log.error("error on adding text for the mimeMessage", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw new IllegalStateException("error on adding text for the mimeMessage", e);
        }
    }

    public MailPartContent analyze(DefaultLogger logger, MimeMessage mimeMessage) {
        TimeMetric timeMetric = null;
        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailPartContentService:analyze");
            MailPartContent result = createContent(mimeMessage, mimeMessage, 0, EnumMailPartContentType.MimeMessage);
            timeMetric.stopAndPublish();
            return result;
        } catch (Exception e) {
            log.error("error on analyze the mimeMessage", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw new IllegalStateException("error on analyze the mimeMessage", e);
        }
    }

    private MailPartContent createContent(Object mimePart, Object contentPart, int idx, EnumMailPartContentType mimePartContentType) throws Exception {

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
                mimePartContent.getChildren().add(createContent(contentPart, multipart, 0, EnumMailPartContentType.Multipart));
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
                    mimePartContent.getChildren().add(createContent(mimeMessage, null, 0, EnumMailPartContentType.MimeMessage));
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
                    childMailPartContent = createContent(bodyPart, content, i, EnumMailPartContentType.Multipart);
                } else if (content instanceof String) {
                    childMailPartContent = createContent(bodyPart, content, i, EnumMailPartContentType.Text);
                    if (bodyPart.getDisposition() != null && bodyPart.getDisposition().toLowerCase().equals(EnumMailPartDispositionType.Xkas.getName())) {
                        childMailPartContent.setMimePartDispositionType(EnumMailPartDispositionType.Xkas);
                    }
                } else if (content instanceof InputStream || content instanceof byte[]) {
                    childMailPartContent = createContent(bodyPart, content, i, EnumMailPartContentType.Binary);
                } else if (content instanceof MimeMessage) {
                    childMailPartContent = createContent(bodyPart, content, i, EnumMailPartContentType.MimeMessage);
                } else if (content instanceof DispositionNotification) {
                    childMailPartContent = createContent(bodyPart, content, i, EnumMailPartContentType.DispositionNotification);
                } else if (content instanceof DeliveryStatus) {
                    childMailPartContent = createContent(bodyPart, content, i, EnumMailPartContentType.DeliveryStatus);
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
