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
import net.sberg.openkim.common.EnumMailAuthMethod;
import net.sberg.openkim.common.EnumMailConnectionSecurity;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.MailaddressCertErrorContext;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import net.sberg.openkim.log.error.MailaddressRcptToErrorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.KeyManagerFactory;
import java.io.*;
import java.security.KeyStore;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MailUtils {

    private static final Logger log = LoggerFactory.getLogger(MailUtils.class);

    public static final String X_OPENKIM_TEST_ID = "X-OPENKIM-TEST-ID";
    public static final String X_OPENKIM_ADDRESS_MAPPING = "X-OPENKIM-ADDRESS-MAPPING";
    public static final String X_KIM_DIENSTKENNUNG = "X-KIM-Dienstkennung";
    public static final String X_KIM_KAS_SIZE = "X-KIM-KAS-Size";
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
    public static final List VALID_KIM_VERSIONS = Arrays.asList("1.0", "1.5", "1.5+");

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

    public static final String sanitizeMailFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<> \"]", "_");
    }

    public static final File writeToFileDirectory(Message msg, String prefix, String messageId, String storageFolder) throws Exception {
        File f = new File(storageFolder);
        if (!f.exists()) {
            f.mkdirs();
        }
        String whereToSave = f.getAbsolutePath() + File.separator + prefix + sanitizeMailFilename(messageId) + ".eml";
        f = new File(whereToSave);
        if (!f.exists()) {
            OutputStream out = new FileOutputStream(new File(whereToSave));
            msg.writeTo(out);
            out.flush();
            out.close();
        }
        else {
            throw new IllegalStateException("error on writing the mail: "+messageId+" - file exists: "+f);
        }
        return f;
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

    public static final boolean checkAddressMapping(DefaultLogger logger, MimeMessage msg, boolean sendingMode) throws Exception {
        try {
            logger.logLine("checkAddressMapping");
            String addressMappingStr = (msg.getHeader(MailUtils.X_OPENKIM_ADDRESS_MAPPING) != null && msg.getHeader(MailUtils.X_OPENKIM_ADDRESS_MAPPING).length > 0)
                ? msg.getHeader(MailUtils.X_OPENKIM_ADDRESS_MAPPING)[0]
                : null;
            if (addressMappingStr == null) {
                logger.logLine("checkAddressMapping finished - no mappings available");
                return true;
            }
            //to|kim-test@sberg.net=uschi@web.de,from|basketmc@gmail.com=uschi@yahoo.de
            String[] mappings = addressMappingStr.split(",");
            for (int i = 0; i < mappings.length; i++) {
                String recOrSender = mappings[i].split("\\|")[0].toLowerCase();
                String mapping = mappings[i].split("\\|")[1];
                String source = mapping.split("=")[0].toLowerCase();
                String target = mapping.split("=")[1].toLowerCase();
                if (!recOrSender.equals(FROM.toLowerCase())
                    &&
                    !recOrSender.equals(TO.toLowerCase())
                    &&
                    !recOrSender.equals(CC.toLowerCase())
                    &&
                    !recOrSender.equals(BCC.toLowerCase())
                ) {
                    throw new IllegalStateException("falsches Format");
                }

                if (recOrSender.equals(FROM.toLowerCase())) {
                    logger.getDefaultLoggerContext().getSenderAddressMapping().put(source, target);
                }
                else {
                    if (!logger.getDefaultLoggerContext().getRecipientAddressMapping().containsKey(recOrSender)) {
                        logger.getDefaultLoggerContext().getRecipientAddressMapping().put(recOrSender, new HashMap<>());
                    }
                    logger.getDefaultLoggerContext().getRecipientAddressMapping().get(recOrSender).put(source, target);
                }
            }

            if (sendingMode) {
                String from = msg.getFrom()[0].toString().toLowerCase();
                if (logger.getDefaultLoggerContext().getSenderAddressMapping().containsKey(from)) {
                    msg.setFrom(logger.getDefaultLoggerContext().getSenderAddressMapping().get(from));
                }

                List<Message.RecipientType> types = List.of(Message.RecipientType.TO, Message.RecipientType.CC, Message.RecipientType.BCC);
                for (Iterator<Message.RecipientType> iterator = types.iterator(); iterator.hasNext(); ) {
                    Message.RecipientType type = iterator.next();
                    Address[] addresses = msg.getRecipients(type);
                    if (addresses != null && logger.getDefaultLoggerContext().getRecipientAddressMapping().containsKey(type.toString().toLowerCase())) {
                        msg.removeHeader(type.toString().toLowerCase());
                        for (int i = 0; i < addresses.length; i++) {
                            if (logger.getDefaultLoggerContext().getRecipientAddressMapping().get(type.toString().toLowerCase()).containsKey(addresses[i].toString().toLowerCase())) {
                                msg.addRecipient(type, new InternetAddress(logger.getDefaultLoggerContext().getRecipientAddressMapping().get(type.toString().toLowerCase()).get(addresses[i].toString().toLowerCase())));
                            } else {
                                msg.addRecipient(type, addresses[i]);
                            }
                        }
                    }
                }
            }

            return true;
        }
        catch (Exception e) {
            log.error("error on checkAddressMapping", e);
            logger.logLine("error on checkAddressMapping");
            throw e;
        }
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
        MailaddressRcptToErrorContext mailaddressRcptToErrorContext = logger.getDefaultLoggerContext().getMailaddressRcptToErrorContext();

        List<InternetAddress> res = new ArrayList<>();
        for (int i = 0; i < originMessage.getRecipients(type).length; i++) {
            InternetAddress rec = (InternetAddress) originMessage.getRecipients(type)[i];

            if (mailaddressCertErrorContext.isError(rec.getAddress().toLowerCase())
                ||
                mailaddressKimVersionErrorContext.isError(rec.getAddress().toLowerCase())
                ||
                mailaddressRcptToErrorContext.isError(rec.getAddress().toLowerCase())
            ) {
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
            pop3ClientIdleTimeoutInSeconds * 1000,
            !createSSLSocketFactory
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
        int timeout,
        boolean trustAllHosts) throws Exception {
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

        if (trustAllHosts) {
            props.put("mail.pop3.ssl.trust", "*");
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

    public static final Properties fillSmtpMailProps(
        Properties props,
        EnumMailConnectionSecurity connectionSecurity,
        EnumMailAuthMethod authMethod,
        String host,
        String port,
        int timeout,
        boolean trustAllHosts) throws Exception {
        if (connectionSecurity.equals(EnumMailConnectionSecurity.STARTTLS)) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.enable", "false");
        }
        else if (connectionSecurity.equals(EnumMailConnectionSecurity.SSLTLS)) {
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.ssl.enable", "true");
        }
        else if (connectionSecurity.equals(EnumMailConnectionSecurity.NONE)) {
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.ssl.enable", "false");
        }

        if (authMethod.equals(EnumMailAuthMethod.NORMALPWD)) {
            props.put("mail.smtp.auth", "true");
        }
        else {
            props.put("mail.smtp.auth", "false");
        }

        props.put("mail.transport.protocol", "smtp");
        if (trustAllHosts) {
            props.put("mail.smtp.ssl.trust", "*");
        }
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.connectiontimeout", timeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", timeout);

        return props;
    }
}
