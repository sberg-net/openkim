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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimePart;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class MailPartContent {
    private int idx = 0;
    private double notAttachementSize = 0;
    private double attachementSize = 0;
    private boolean attachment;
    private boolean attachmentInline;

    @JsonIgnore
    private Object mimePart;
    @JsonIgnore
    private Object contentPart;

    private EnumMailPartContentType mimePartContentType = EnumMailPartContentType.Unknown;
    private EnumMailPartDispositionType mimePartDispositionType = EnumMailPartDispositionType.Unknown;
    private List<MailPartContent> children = new ArrayList<>();
    private List<String> header = new ArrayList<>();

    @JsonIgnore
    private String contentDispositionHeader;

    @JsonIgnore
    private String contentTypeHeader;

    public Multipart getFirstMultipart(String subtype) throws Exception {
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            MimePart mimePart = (MimePart) child.getMimePart();
            if (child.getMimePartContentType().equals(EnumMailPartContentType.Multipart) && mimePart.isMimeType("multipart/" + subtype)) {
                return (Multipart) child.getContentPart();
            }
            Multipart result = child.getFirstMultipart(subtype);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public List<MimeBodyPart> getBodyParts(String mimeType) throws Exception {
        List<MimeBodyPart> result = new ArrayList<>();
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            if (child.getMimePart() instanceof MimeBodyPart && ((MimeBodyPart) child.getMimePart()).isMimeType(mimeType)) {
                result.add((MimeBodyPart) child.getMimePart());
            }
            if (child.getMimePartContentType().equals(EnumMailPartContentType.Multipart)) {
                result.addAll(child.getBodyParts(mimeType));
            }
        }
        return result;
    }

    public Multipart getFirstMultipart() throws Exception {
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            if (child.getMimePartContentType().equals(EnumMailPartContentType.Multipart)) {
                return (Multipart) child.getContentPart();
            }
            Multipart result = child.getFirstMultipart();
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public double sumTotalSize() {
        double sum = notAttachementSize + attachementSize;
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            sum = sum + child.sumTotalSize();
        }
        return sum;
    }

    public double sumAttachmentSize() {
        double sum = attachementSize;
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            sum = sum + child.sumAttachmentSize();
        }
        return sum;
    }

    public double sumNotAttachmentSize() {
        double sum = notAttachementSize;
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            sum = sum + child.sumNotAttachmentSize();
        }
        return sum;
    }

    public List<MailPartContent> collectAllAttachments(List<MailPartContent> result) throws Exception {
        if (mimePartContentType.equals(EnumMailPartContentType.Binary)) {
            result.add(this);
        }
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            result = child.collectAllAttachments(result);
        }
        return result;
    }

    public List<MailPartContent> collectAllXKasParts(List<MailPartContent> result) throws Exception {
        if (mimePartDispositionType.equals(EnumMailPartDispositionType.Xkas)) {
            result.add(this);
        }
        for (Iterator<MailPartContent> iterator = children.iterator(); iterator.hasNext(); ) {
            MailPartContent child = iterator.next();
            result = child.collectAllAttachments(result);
        }
        return result;
    }
}
