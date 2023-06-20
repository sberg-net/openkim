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

import com.sun.mail.util.MailSSLSocketFactory;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.MailaddressCertErrorContext;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import net.sberg.openkim.common.EnumMailAuthMethod;
import net.sberg.openkim.common.EnumMailConnectionSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MailUtils {

    private static final Logger log = LoggerFactory.getLogger(MailUtils.class);

    public static final String X_KIM_TESTNACHRICHT = "X-KIM-Testnachricht";
    public static final String X_KIM_TESTID = "X-KIM-Testid";
    public static final String X_KIM_DIENSTKENNUNG = "X-KIM-Dienstkennung";
    public static final String X_KOM_LE_VERSION = "X-KOM-LE-Version";
    public static final String X_KIM_DIENSTKENNUNG_KIM_MAIL = "KIM-Mail;Default;V1.5";
    public static final String DATE = "Date";
    public static final String RETURN_PATH = "Return-Path";
    public static final String RECEIVED = "Received";
    public static final String FROM = "From";
    public static final String TO = "To";
    public static final String CC = "Cc";
    public static final String BCC = "Bcc";
    public static final String REPLY_TO = "Reply-To";
    public static final String SENDER = "Sender";
    public static final String X_KIM_CMVERSION = "X-KIM-CMVersion";
    public static final String X_KIM_PTVERSION = "X-KIM-PTVersion";
    public static final String X_KIM_KONVERSION = "X-KIM-KONVersion";
    public static final String X_KIM_FEHLERMELDUNG = "X-KIM-Fehlermeldung";
    public static final String X_KIM_DECRYPTION_RESULT = "X-KIM-DecryptionResult";
    public static final String X_KIM_INTEGRITY_CHECK_RESULT = "X-KIM-IntegrityCheckResult";
    public static final String EXPIRES = "Expires";
    public static final String SUBJECT_KOM_LE_NACHRICHT = "KOM-LE-Nachricht";
    public static final List VALID_KIM_VERSIONS = Arrays.asList("1.0", "1.5");

    public static final DateTimeFormatter RFC822_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.GERMAN);

    public static final MimeMessage createMimeMessage(Session session, InputStream inputStream, boolean updateMessageID) throws Exception {
        if (updateMessageID) {
            return new MimeMessage(session, inputStream);
        } else {
            return new MimeMessage(session, inputStream) {
                protected void updateMessageID() {
                }
            };
        }
    }

    public static final MimeMessage setRecipients(
            DefaultLogger logger,
            List<X509CertificateResult> recipientCerts,
            MimeMessage originMessage,
            MimeMessage resultMessage,
            Message.RecipientType type
    ) throws Exception {
        if (originMessage.getRecipients(type) == null || originMessage.getRecipients(type).length == 0) {
            return resultMessage;
        }

        MailaddressCertErrorContext mailaddressCertErrorContext = logger.getDefaultLoggerContext().getMailaddressCertErrorContext();
        MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext();

        for (int i = 0; i < originMessage.getRecipients(type).length; i++) {
            InternetAddress rec = (InternetAddress) originMessage.getRecipients(type)[i];

            if (mailaddressCertErrorContext.isError(rec.getAddress().toLowerCase()) || mailaddressKimVersionErrorContext.isError(rec.getAddress().toLowerCase())) {
                logger.logLine("Mailadresse: " + rec.getAddress().toLowerCase() + " entfernt, da Fehler aufgetreten sind");
                continue;
            }

            for (Iterator<X509CertificateResult> iterator = recipientCerts.iterator(); iterator.hasNext(); ) {
                X509CertificateResult x509CertificateResult = iterator.next();
                if (rec.getAddress().equalsIgnoreCase(x509CertificateResult.getMailAddress())) {
                    resultMessage.addRecipient(type, rec);
                    break;
                }
            }
        }
        return resultMessage;
    }

    public static final boolean checkHeader(DefaultLogger logger, Konnektor konnektor, MimeMessage encryptedMsg, MimeMessage decryptedAndVerifiedMsg, String headerName) throws Exception {
        try {
            logger.logLine("check header: " + headerName);

            String encryptedMsgValues = encryptedMsg.getHeader(headerName, ",");
            String decryptedAndVerifiedMsgValues = decryptedAndVerifiedMsg.getHeader(headerName, ",");

            if ((encryptedMsgValues == null || encryptedMsgValues.trim().isEmpty()) && (decryptedAndVerifiedMsgValues == null || decryptedAndVerifiedMsgValues.trim().isEmpty())) {
                logger.logLine("check header: " + headerName + " - TRUE");
                return true;
            }

            List<InternetAddress> encryptedMsgValueAddresses = Arrays.asList(InternetAddress.parse(encryptedMsgValues, true));
            List<InternetAddress> decryptedAndVerifiedMsgValueAddresses = Arrays.asList(InternetAddress.parse(decryptedAndVerifiedMsgValues, true));

            if ((encryptedMsgValues == null || encryptedMsgValues.trim().isEmpty()) && (decryptedAndVerifiedMsgValues != null && !decryptedAndVerifiedMsgValues.trim().isEmpty())) {
                throw new IllegalStateException("error on checking header: " + headerName + " - " + konnektor.getIp());
            }
            if ((encryptedMsgValues != null && !encryptedMsgValues.trim().isEmpty()) && (decryptedAndVerifiedMsgValues == null || decryptedAndVerifiedMsgValues.trim().isEmpty())) {
                throw new IllegalStateException("error on checking header: " + headerName + " - " + konnektor.getIp());
            }

            if (encryptedMsgValueAddresses.size() != decryptedAndVerifiedMsgValueAddresses.size()) {
                throw new IllegalStateException("error on checking header: " + headerName + " - " + konnektor.getIp());
            }

            for (Iterator iterator = encryptedMsgValueAddresses.iterator(); iterator.hasNext(); ) {
                Object v = iterator.next();
                if (v.toString().trim().isEmpty()) {
                    continue;
                }
                logger.logLine("encryptedMsgValue: " + v);
                if (!decryptedAndVerifiedMsgValueAddresses.contains(v)) {
                    throw new IllegalStateException("error on checking header: " + headerName + " - " + konnektor.getIp());
                }
            }

            for (Iterator iterator = decryptedAndVerifiedMsgValueAddresses.iterator(); iterator.hasNext(); ) {
                Object v = iterator.next();
                if (v.toString().trim().isEmpty()) {
                    continue;
                }
                logger.logLine("decryptedAndVerifiedMsgValue: " + v);
                if (!encryptedMsgValueAddresses.contains(v)) {
                    throw new IllegalStateException("error on checking header: " + headerName + " - " + konnektor.getIp());
                }
            }

            logger.logLine("check header: " + headerName + " - TRUE");
            return true;
        } catch (Exception e) {
            log.error("error on checking header: " + headerName + " - " + konnektor.getIp(), e);
            logger.logLine("check header: " + headerName + " - FALSE");
            logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X001);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X001 + " - " + EnumErrorCode.CODE_X001.getHrText());
            throw e;
        }
    }

    public static final MimeMessage removeRecipients(DefaultLogger logger, List<X509CertificateResult> recipientCerts, MimeMessage originMessage, Message.RecipientType type) throws Exception {
        if (originMessage.getRecipients(type) == null || originMessage.getRecipients(type).length == 0) {
            return originMessage;
        }

        MailaddressCertErrorContext mailaddressCertErrorContext = logger.getDefaultLoggerContext().getMailaddressCertErrorContext();
        MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext();

        List<InternetAddress> res = new ArrayList<>();
        for (int i = 0; i < originMessage.getRecipients(type).length; i++) {
            InternetAddress rec = (InternetAddress) originMessage.getRecipients(type)[i];

            if (mailaddressCertErrorContext.isError(rec.getAddress().toLowerCase()) || mailaddressKimVersionErrorContext.isError(rec.getAddress().toLowerCase())) {
                logger.logLine("Mailadresse: " + rec.getAddress().toLowerCase() + " entfernt, da Fehler aufgetreten sind");
                continue;
            }

            for (Iterator<X509CertificateResult> iterator = recipientCerts.iterator(); iterator.hasNext(); ) {
                X509CertificateResult x509CertificateResult = iterator.next();
                if (rec.getAddress().equalsIgnoreCase(x509CertificateResult.getMailAddress())) {
                    res.add(rec);
                    break;
                }
            }
        }

        InternetAddress[] addresses = new InternetAddress[res.size()];
        addresses = res.toArray(addresses);
        originMessage.setRecipients(type, addresses);

        return originMessage;
    }

    public static final List<String> getAddresses(MimeMessage mimeMessage, boolean checkFrom) throws Exception {
        List<String> result = new ArrayList<>();
        Address[] addresses = mimeMessage.getRecipients(Message.RecipientType.TO);
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                result.add(((InternetAddress) addresses[i]).getAddress().toLowerCase());
            }
        }
        addresses = mimeMessage.getRecipients(Message.RecipientType.CC);
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                result.add(((InternetAddress) addresses[i]).getAddress().toLowerCase());
            }
        }
        addresses = mimeMessage.getRecipients(Message.RecipientType.BCC);
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                result.add(((InternetAddress) addresses[i]).getAddress().toLowerCase());
            }
        }

        if (checkFrom) {
            if (!result.contains(((InternetAddress) mimeMessage.getFrom()[0]).getAddress().toLowerCase())) {
                result.add(((InternetAddress) mimeMessage.getFrom()[0]).getAddress().toLowerCase());
            }
        }

        return result;
    }

    public static final Session createPop3ClientSession(
        Properties props,
        EnumMailConnectionSecurity connectionSecurity,
        EnumMailAuthMethod authMethod,
        String host,
        String port,
        int pop3ClientIdleTimeoutInSeconds,
        String sslCertFileName,
        String sslCertFilePwd,
        boolean createSSLSocketFactory
    ) throws Exception {

        MailSSLSocketFactory mailSSLSocketFactory = null;
        if (createSSLSocketFactory) {
            char[] passCharArray = sslCertFilePwd.toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new FileInputStream(sslCertFileName), passCharArray);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, passCharArray);

            mailSSLSocketFactory = new MailSSLSocketFactory();
            mailSSLSocketFactory.setKeyManagers(keyManagerFactory.getKeyManagers());
            mailSSLSocketFactory.setTrustAllHosts(true);
        }

        props = fillPop3MailProps(
            props,
            connectionSecurity,
            authMethod,
            host,
            port,
                pop3ClientIdleTimeoutInSeconds * 1000
        );

        if (createSSLSocketFactory) {
            props.put("mail.pop3.ssl.socketFactory", mailSSLSocketFactory);
        }

        return Session.getInstance(props);
    }

    public static final Properties fillPop3MailProps(
        Properties props,
        EnumMailConnectionSecurity connectionSecurity,
        EnumMailAuthMethod authMethod,
        String host,
        String port,
        int timeout) throws Exception {
        if (connectionSecurity.equals(EnumMailConnectionSecurity.STARTTLS)) {
            props.put("mail.pop3.starttls.enable", "true");
            props.put("mail.pop3.ssl.enable", "false");
        } else if (connectionSecurity.equals(EnumMailConnectionSecurity.SSLTLS)) {
            props.put("mail.pop3.starttls.enable", "false");
            props.put("mail.pop3.ssl.enable", "true");
        } else if (connectionSecurity.equals(EnumMailConnectionSecurity.NONE)) {
            props.put("mail.pop3.starttls.enable", "false");
            props.put("mail.pop3.ssl.enable", "false");
        }

        if (authMethod.equals(EnumMailAuthMethod.NORMALPWD)) {
            props.put("mail.pop3.auth", "true");
        } else {
            props.put("mail.pop3.auth", "false");
        }

        props.put("mail.transport.protocol", "pop3");
        props.put("mail.store.protocol", "pop3");
        props.put("mail.pop3.host", host);
        props.put("mail.pop3.port", port);
        props.put("mail.pop3.connectiontimeout", timeout);
        props.put("mail.pop3.timeout", timeout);
        props.put("mail.pop3.writetimeout", timeout);

        return props;
    }
}