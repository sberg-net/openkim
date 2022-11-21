/*
 * Copyright 2022 sberg it-systeme GmbH
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
package net.sberg.openkim.common.mail;

import com.sun.mail.dsn.DeliveryStatus;
import com.sun.mail.dsn.MultipartReport;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Properties;

public class DnsHelper {

    public static final MimeMessage createMessage(
        MimeMessage originalMail,
        String sender,
        String errorMsg,
        String statusCode,
        String diagnosticCode,
        String action,
        String subject,
        String reportingMTA,
        String receivedFromMTA
    ) throws Exception {
        InternetHeaders internetHeaders = new InternetHeaders(originalMail.getInputStream());

        DeliveryStatus deliveryStatus = new DeliveryStatus();
        deliveryStatus.setMessageDSN(create(originalMail, statusCode, diagnosticCode, action, reportingMTA, receivedFromMTA));

        MultipartReport multipartReport = new MultipartReport(errorMsg, deliveryStatus, internetHeaders);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        mimeMessage.setSubject(subject);
        mimeMessage.setFrom(sender);
        mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(sender));
        mimeMessage.setContent(multipartReport);
        mimeMessage.saveChanges();
        return mimeMessage;
    }

    private static final InternetHeaders create(
        MimeMessage originalMail,
        String statusCode,
        String diagnosticCode,
        String action,
        String reportingMTA,
        String receivedFromMTA) throws MessagingException {
        InternetHeaders headers = new InternetHeaders();

        headers.addHeader("Reporting-MTA", reportingMTA);
        headers.addHeader("Received-From-MTA", receivedFromMTA);
        headers.addHeader("Original-Envelope-Id", originalMail.getMessageID());
        if (originalMail.getReceivedDate() != null) {
            headers.addHeader("Arrival-Date", MailService.RFC822_DATE_FORMAT.format(ZonedDateTime.ofInstant(originalMail.getReceivedDate().toInstant(), ZoneId.systemDefault())));
        }

        for (int i = 0; i < originalMail.getAllRecipients().length; i++) {
            headers = appendRecipient(headers, originalMail.getAllRecipients()[i], statusCode, diagnosticCode, originalMail.getReceivedDate(), action);
        }

        return headers;
    }


    private static final InternetHeaders appendRecipient(InternetHeaders headers, Address mailAddress, String statusCode, String diagnosticCode, Date lastUpdated, String action) {
        headers.addHeader("Final-Recipient", "rfc822; " + mailAddress.toString());
        headers.addHeader("Action", action);
        headers.addHeader("Status", statusCode);
        headers.addHeader("Diagnostic-Code", "smtp; " + diagnosticCode);
        if (lastUpdated != null) {
            headers.addHeader("Last-Attempt-Date", MailService.RFC822_DATE_FORMAT.format(ZonedDateTime.ofInstant(lastUpdated.toInstant(), ZoneId.systemDefault())));
        }
        return headers;
    }
}
