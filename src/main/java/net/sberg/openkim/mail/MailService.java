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

import de.gematik.ws.conn.cardservice.v8_1_2.PinStatusEnum;
import de.gematik.ws.conn.cardservicecommon.v2.CardTypeType;
import de.gematik.ws.conn.certificateservice.v6_0_1.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.X509DataInfoListType;
import de.gematik.ws.conn.connectorcommon.DocumentType;
import de.gematik.ws.conn.encryptionservice.v6_1_1.DecryptDocumentResponse;
import de.gematik.ws.conn.encryptionservice.v6_1_1.EncryptDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.SignResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.VerifyDocumentResponse;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.mail.signreport.SignReportService;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.common.x509.IssuerAndSerial;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.KonnektorWebserviceUtils;
import net.sberg.openkim.konnektor.KonnektorCard;
import net.sberg.openkim.konnektor.KonnektorCardService;
import net.sberg.openkim.konnektor.webservice.CertificateService;
import net.sberg.openkim.konnektor.webservice.EncryptionService;
import net.sberg.openkim.konnektor.webservice.SignatureService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.*;
import oasis.names.tc.dss._1_0.core.schema.SignatureObject;
import oasis.names.tc.dss_x._1_0.profiles.verificationreport.schema_.VerificationReportType;
import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.metrics.api.TimeMetric;
import org.bouncycastle.asn1.cms.AuthEnvelopedData;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.EnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.mail.*;
import javax.mail.internet.*;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public static final String X_KIM_TESTNACHRICHT = "X-KIM-Testnachricht";
    public static final String X_KIM_TESTID = "X-KIM-Testid";
    private static final String X_KIM_DIENSTKENNUNG = "X-KIM-Dienstkennung";
    private static final String X_KOM_LE_VERSION = "X-KOM-LE-Version";
    private static final String X_KIM_DIENSTKENNUNG_KIM_MAIL = "KIM-Mail;Default;V1.5";
    private static final String DATE = "Date";
    private static final String RETURN_PATH = "Return-Path";
    private static final String RECEIVED = "Received";
    private static final String FROM = "From";
    private static final String TO = "To";
    private static final String CC = "Cc";
    private static final String BCC = "Bcc";
    private static final String REPLY_TO = "Reply-To";
    private static final String SENDER = "Sender";
    private static final String X_KIM_CMVERSION = "X-KIM-CMVersion";
    private static final String X_KIM_PTVERSION = "X-KIM-PTVersion";
    private static final String X_KIM_KONVERSION = "X-KIM-KONVersion";
    private static final String X_KIM_FEHLERMELDUNG = "X-KIM-Fehlermeldung";
    private static final String X_KIM_DECRYPTION_RESULT = "X-KIM-DecryptionResult";
    private static final String X_KIM_INTEGRITY_CHECK_RESULT = "X-KIM-IntegrityCheckResult";
    private static final String EXPIRES = "Expires";
    private static final String SUBJECT_KOM_LE_NACHRICHT = "KOM-LE-Nachricht";
    private static final List VALID_KIM_VERSIONS = Arrays.asList("1.0", "1.5");

    public static final DateTimeFormatter RFC822_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.GERMAN);

    @Autowired
    private SignatureService signatureService;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private KonnektorCardService konnektorCardService;
    @Autowired
    private MailPartContentService mailPartContentService;
    @Autowired
    private SignReportService signReportService;

    public byte[] createEmbeddedMessageRfc822(
        DefaultLogger logger,
        IErrorContext errorContext,
        MimeMessage originMessage
    ) throws Exception {

        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:createEmbeddedMessageRfc822");

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
                    mimeMessage.addHeader(X_KIM_DECRYPTION_RESULT, code.getId());
                    contentBuilder.append(code.getHrText()).append("\n");
                }
                textMimeBodyPart.setText(contentBuilder.toString(), "UTF-8", "plain");

                //origin message as message/rfc822
                MimeBodyPart messageMimeBodyPart = new MimeBodyPart();
                mixedMultiPart.addBodyPart(messageMimeBodyPart);
                messageMimeBodyPart.setContent(originMessage, "message/rfc822");

                mimeMessage.setContent(mixedMultiPart);

                //set header
                String date = (originMessage.getHeader(DATE) != null && originMessage.getHeader(DATE).length > 0) ? originMessage.getHeader(DATE)[0] : null;
                InternetAddress from = (originMessage.getFrom() != null && originMessage.getFrom().length > 0) ? (InternetAddress) originMessage.getFrom()[0] : null;

                //reply to handling
                InternetAddress replyTo = null;
                String replyToStr = originMessage.getHeader(REPLY_TO, ",");
                if (replyToStr != null) {
                    try {
                        replyTo = InternetAddress.parseHeader(replyToStr, true)[0];
                    } catch (Exception e) {
                        logger.logLine("error on parsing reply-to header: " + replyToStr);
                    }
                }

                InternetAddress sender = (InternetAddress) originMessage.getSender();

                mimeMessage.setHeader(DATE, date);
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

            return result;
        } catch (Exception e) {
            log.error("error on creating EmbeddedMessageRfc822 mail for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }

    }

    public byte[] createDsn(
        Pop3GatewaySession pop3GatewaySession,
        IErrorContext errorContext,
        MimeMessage originMessage
    ) throws Exception {

        TimeMetric timeMetric = null;

        DefaultLogger logger = pop3GatewaySession.getLogger();
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:createDsn");

            MimeMessage mimeMessage = null;

            if (errorContext instanceof MailSignVerifyErrorContext) {

                List<EnumErrorCode> codes = ((MailSignVerifyErrorContext) errorContext).getErrorCodes();

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("Es sind Fehler beim Verifizieren der Mail-Signatur aufgetreten.\r\n");
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DnsHelper.createMessage(
                    originMessage,
                    logger.getDefaultLoggerContext().getMailServerUsername(),
                    contentBuilder.toString(),
                    "",
                    "",
                    "failed",
                    originMessage.getSubject(),
                    konfiguration.getXkimCmVersion(),
                    konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(X_KIM_INTEGRITY_CHECK_RESULT, code.getId());
                }
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();

            timeMetric.stopAndPublish();

            return result;
        } catch (Exception e) {
            log.error("error on creating dsn mail for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public void sendDsn(
        SmtpGatewaySession smtpGatewaySession,
        IErrorContext errorContext,
        boolean senderContext,
        MimeMessage originMessage
    ) {

        TimeMetric timeMetric = null;

        DefaultLogger logger = smtpGatewaySession.getLogger();
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:sendDsn");

            MimeMessage mimeMessage = null;

            if (errorContext instanceof MailSignEncryptErrorContext) {

                List<EnumErrorCode> codes = ((MailSignEncryptErrorContext) errorContext).getErrorCodes();

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("Es sind Fehler beim Signieren und Verschlüsseln der Mail aufgetreten.\r\n");
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DnsHelper.createMessage(
                    originMessage,
                    smtpGatewaySession.getFromAddressStr(),
                    contentBuilder.toString(),
                    "",
                    "",
                    "failed",
                    originMessage.getSubject(),
                    konfiguration.getXkimCmVersion(),
                    konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(X_KIM_FEHLERMELDUNG, code.getId());
                }
            } else if (senderContext) {

                String senderAddress = null;
                List<EnumErrorCode> codes = null;

                if (errorContext instanceof MailaddressCertErrorContext) {
                    senderAddress = ((MailaddressCertErrorContext) errorContext).getFromSenderAddresses().get(0);
                    codes = ((MailaddressCertErrorContext) errorContext).getAddressErrors().get(senderAddress);
                } else if (errorContext instanceof MailaddressKimVersionErrorContext) {
                    senderAddress = ((MailaddressCertErrorContext) errorContext).getFromSenderAddresses().get(0);
                    codes = ((MailaddressCertErrorContext) errorContext).getAddressErrors().get(senderAddress);
                }

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder
                    .append("Die Mail konnte nicht versandt werden. Für den Sender ")
                    .append(senderAddress)
                    .append(" wurden beim Versand Probleme festgestellt.\r\n");
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DnsHelper.createMessage(
                    originMessage,
                    smtpGatewaySession.getFromAddressStr(),
                    contentBuilder.toString(),
                    "",
                    "",
                    "failed",
                    originMessage.getSubject(),
                    konfiguration.getXkimCmVersion(),
                    konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(X_KIM_FEHLERMELDUNG, code.getId());
                }
            } else {

                List<String> rcptAddresses = null;
                Map<String, List<EnumErrorCode>> addressErrors = null;

                if (errorContext instanceof MailaddressCertErrorContext) {
                    rcptAddresses = ((MailaddressCertErrorContext) errorContext).getRcptAddresses();
                    addressErrors = ((MailaddressCertErrorContext) errorContext).getAddressErrors();
                } else if (errorContext instanceof MailaddressKimVersionErrorContext) {
                    rcptAddresses = ((MailaddressKimVersionErrorContext) errorContext).getRcptAddresses();
                    addressErrors = ((MailaddressKimVersionErrorContext) errorContext).getAddressErrors();
                }

                StringBuilder contentBuilder = new StringBuilder();
                if (smtpGatewaySession.extractNoFailureCertRcpts().isEmpty() && !smtpGatewaySession.extractFailureCertRcpts().isEmpty()) {
                    contentBuilder.append("Die Mail konnte nicht versandt werden. Für alle Empfänger wurden beim Versand Probleme festgestellt:\r\n");
                }
                if (!smtpGatewaySession.extractNoFailureCertRcpts().isEmpty() && !smtpGatewaySession.extractFailureCertRcpts().isEmpty()) {
                    contentBuilder.append("Die Mail konnte versandt werden. Für einige Empfänger wurden beim Versand Probleme festgestellt:\r\n");
                }
                contentBuilder.append(String.join(",", rcptAddresses));
                contentBuilder.append("\r\n");

                List<EnumErrorCode> codes = new ArrayList<>();
                for (Iterator<String> iterator = rcptAddresses.iterator(); iterator.hasNext(); ) {
                    String address = iterator.next();
                    List<EnumErrorCode> addressCodes = addressErrors.get(address);
                    for (Iterator<EnumErrorCode> enumErrorCodeIterator = addressCodes.iterator(); enumErrorCodeIterator.hasNext(); ) {
                        EnumErrorCode code = enumErrorCodeIterator.next();
                        if (!codes.contains(code)) {
                            codes.add(code);
                        }
                    }
                }
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DnsHelper.createMessage(
                    originMessage,
                    smtpGatewaySession.getFromAddressStr(),
                    contentBuilder.toString(),
                    "",
                    "",
                    "failed",
                    originMessage.getSubject(),
                    konfiguration.getXkimCmVersion(),
                    konfiguration.getXkimCmVersion()
                );
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    mimeMessage.addHeader(X_KIM_FEHLERMELDUNG, code.getId());
                }
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(byteArrayOutputStream);
            byteArrayOutputStream.close();

            String certfileName = ICommonConstants.BASE_DIR + File.separator + konfiguration.getFachdienstCertFilename();
            char[] passCharArray = konfiguration.getFachdienstCertAuthPwd().toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new FileInputStream(certfileName), passCharArray);

            SSLContext sslContext = new SSLContextBuilder().loadKeyMaterial(keyStore, passCharArray).loadTrustMaterial(new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    return true;
                }
            }).build();

            AuthenticatingSMTPClient client = new AuthenticatingSMTPClient(true, sslContext);
            client.connect(logger.getDefaultLoggerContext().getMailServerHost(), Integer.parseInt(logger.getDefaultLoggerContext().getMailServerPort()));
            boolean res = client.auth(
                AuthenticatingSMTPClient.AUTH_METHOD.LOGIN,
                logger.getDefaultLoggerContext().getMailServerUsername(),
                logger.getDefaultLoggerContext().getMailServerPassword()
            );
            logger.logLine("dsn sending - smtp auth: " + res);
            if (res) {
                String content = byteArrayOutputStream.toString();
                String[] recs = new String[]{smtpGatewaySession.getFromAddressStr()};
                res = client.sendSimpleMessage(smtpGatewaySession.getFromAddressStr(), recs, content);
                logger.logLine("dsn sending - smtp sent: " + res);
            }

            timeMetric.stopAndPublish();
        } catch (Exception e) {
            log.error("error on sending dsn mail for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
        }
    }

    public boolean checkMailEncryptFormat(DefaultLogger logger, MimeMessage encryptedMsg, boolean decryptMode) {

        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:checkMailEncyrptFormat");

            //header
            String[] header = encryptedMsg.getHeader(X_KOM_LE_VERSION);
            if (header == null || header.length != 1 || !VALID_KIM_VERSIONS.contains(header[0])) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X014);
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X014 + " - " + EnumErrorCode.CODE_X014.getHrText());
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());
                return false;
            }

            //subject
            if (encryptedMsg.getSubject() == null || !encryptedMsg.getSubject().equals(SUBJECT_KOM_LE_NACHRICHT)) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X015);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X015 + " - " + EnumErrorCode.CODE_X015.getHrText());
                return false;
            }

            MailPartContent encryptMailPartContent = mailPartContentService.analyze(logger, encryptedMsg);
            if (!encryptMailPartContent.getContentTypeHeader().toLowerCase().startsWith("content-type: application/pkcs7-mime")
                ||
                encryptMailPartContent.getChildren().size() != 0
                ||
                !(encryptMailPartContent.getContentPart() instanceof MimePart)
                ||
                !encryptMailPartContent.isAttachment()
                ||
                encryptMailPartContent.isAttachmentInline()
                ||
                encryptMailPartContent.getAttachementSize() == 0) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X016);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X016 + " - " + EnumErrorCode.CODE_X016.getHrText());
                return false;
            }

            //extract body
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (final InputStream inputStream = (InputStream) encryptedMsg.getContent()) {
                for (int c = inputStream.read(); c != -1; c = inputStream.read()) {
                    bos.write(c);
                }
            }
            byte[] encryptedPart = bos.toByteArray();
            bos.reset();
            bos.close();
            ContentInfo encryptedContentInfo = ContentInfo.getInstance(encryptedPart);
            if (decryptMode) {
                logger.getDefaultLoggerContext().setEncryptedContentInfo(encryptedContentInfo);
                logger.getDefaultLoggerContext().setEncryptedPart(encryptedPart);
            }

            //1.2.840.113549.1.9.16.1.23
            if (encryptedContentInfo.getContentType().getId().equals(CMSUtils.ENVELOPED_DATA_OID)
                && !encryptedContentInfo.getContentType().getId().equals(CMSUtils.AUTH_ENVELOPED_DATA_OID)
            ) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X017);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X017 + " - " + EnumErrorCode.CODE_X017.getHrText());
                return false;
            }

            AuthEnvelopedData authEnvelopedData = CMSUtils.extractEnvelopedCMS(encryptedPart);
            EnvelopedData envelopedData = new EnvelopedData(
                authEnvelopedData.getOriginatorInfo(),
                authEnvelopedData.getRecipientInfos(),
                authEnvelopedData.getAuthEncryptedContentInfo(),
                authEnvelopedData.getUnauthAttrs()
            );
            ContentInfo envelopedDataContentInfo = new ContentInfo(CMSObjectIdentifiers.envelopedData, envelopedData);
            CMSEnvelopedData cmsEnvelopedData = new CMSEnvelopedData(envelopedDataContentInfo.getEncoded());

            //encryptedRecipientInfosAvailable
            try {
                if (!CMSUtils.encryptedRecipientInfosAvailable(cmsEnvelopedData)) {
                    logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X018);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_X018 + " - " + EnumErrorCode.CODE_X018.getHrText());
                    return false;
                }
            } catch (Exception e) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X018);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X018 + " - " + EnumErrorCode.CODE_X018.getHrText());
                return false;
            }

            //encryptedRecipientEmailsAvailable
            try {
                if (!CMSUtils.encryptedRecipientEmailsAvailable(encryptedContentInfo)) {
                    logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X019);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_X019 + " - " + EnumErrorCode.CODE_X019.getHrText());
                    return false;
                }
            } catch (Exception e) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X019);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X019 + " - " + EnumErrorCode.CODE_X019.getHrText());
                return false;
            }

            timeMetric.stopAndPublish();
            return true;
        } catch (Exception e) {
            log.error("error on checking message encrypting format for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X020);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X020 + " - " + EnumErrorCode.CODE_X020.getHrText());
            return false;
        }
    }

    public MimeMessage checkOriginMsg(
        DefaultLogger logger,
        MimeMessage originMimeMessage,
        List<X509CertificateResult> recipientCerts,
        String senderAddress,
        boolean throwException
    ) throws Exception {

        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:checkOriginMsg");

            if (originMimeMessage.getFrom() == null || originMimeMessage.getFrom().length == 0) {
                logger.logLine("no from header available for senderAddress: " + senderAddress);
                throw new IllegalStateException("no from header available for senderAddress: " + senderAddress);
            } else if (originMimeMessage.getFrom().length > 1) {
                logger.logLine("more than one from header available for senderAddress: " + senderAddress);
                throw new IllegalStateException("more than one from header available for senderAddress: " + senderAddress);
            }

            InternetAddress from = (originMimeMessage.getFrom() != null && originMimeMessage.getFrom().length > 0)
                ? (InternetAddress) originMimeMessage.getFrom()[0]
                : null;

            //reply to handling
            InternetAddress replyTo = null;
            String replyToStr = originMimeMessage.getHeader(REPLY_TO, ",");
            if (replyToStr != null) {
                try {
                    replyTo = InternetAddress.parseHeader(replyToStr, true)[0];
                } catch (Exception e) {
                    logger.logLine("error on parsing reply-to header: " + replyToStr);
                }
            }

            if (from != null && !from.getAddress().toLowerCase().equals(senderAddress)) {
                originMimeMessage.removeHeader(FROM);
                logger.logLine("from header " + from.getAddress().toLowerCase() + " not equal to senderAddress: " + senderAddress);
                throw new IllegalStateException("from header " + from.getAddress().toLowerCase() + " not equal to senderAddress: " + senderAddress);
            }
            if (replyTo != null && !replyTo.getAddress().toLowerCase().equals(senderAddress)) {
                logger.logLine("reply-to header " + replyTo.getAddress().toLowerCase() + " not equal to senderAddress: " + senderAddress);
                originMimeMessage.setReplyTo(null);
            }

            originMimeMessage = removeRecipients(logger, recipientCerts, originMimeMessage, Message.RecipientType.TO);
            originMimeMessage = removeRecipients(logger, recipientCerts, originMimeMessage, Message.RecipientType.CC);
            originMimeMessage = removeRecipients(logger, recipientCerts, originMimeMessage, Message.RecipientType.BCC);

            if (originMimeMessage.getRecipients(Message.RecipientType.TO).length == 0
                &&
                originMimeMessage.getRecipients(Message.RecipientType.CC).length == 0
                &&
                originMimeMessage.getRecipients(Message.RecipientType.BCC).length == 0) {

                logger.logLine("no recipients available");
                throw new IllegalStateException("no recipients available");
            }

            originMimeMessage.saveChanges();

            timeMetric.stopAndPublish();

            return originMimeMessage;
        } catch (Exception e) {
            log.error("error on checking origin message for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X013);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X013 + " - " + EnumErrorCode.CODE_X013.getHrText());
            if (throwException) {
                throw e;
            } else {
                return originMimeMessage;
            }
        }
    }

    public byte[] composeEncryptedMsg(
        DefaultLogger logger,
        byte[] encryptedMsg,
        MimeMessage originMimeMessage,
        List<X509CertificateResult> recipientCerts,
        boolean testMail,
        boolean throwException
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:compose");

            //create result message
            //analyze origin header
            String date = (originMimeMessage.getHeader(DATE) != null && originMimeMessage.getHeader(DATE).length > 0)
                ? originMimeMessage.getHeader(DATE)[0]
                : null;
            String messageId = originMimeMessage.getMessageID();
            InternetAddress from = (originMimeMessage.getFrom() != null && originMimeMessage.getFrom().length > 0)
                ? (InternetAddress) originMimeMessage.getFrom()[0]
                : null;

            //reply to handling
            InternetAddress replyTo = null;
            String replyToStr = originMimeMessage.getHeader(REPLY_TO, ",");
            if (replyToStr != null) {
                try {
                    replyTo = InternetAddress.parseHeader(replyToStr, true)[0];
                } catch (Exception e) {
                    logger.logLine("error on parsing reply-to header: " + replyToStr);
                }
            }

            InternetAddress sender = (InternetAddress) originMimeMessage.getSender();
            String dienstkennung = (originMimeMessage.getHeader(X_KIM_DIENSTKENNUNG) != null && originMimeMessage.getHeader(X_KIM_DIENSTKENNUNG).length > 0)
                ? originMimeMessage.getHeader(X_KIM_DIENSTKENNUNG)[0]
                : X_KIM_DIENSTKENNUNG_KIM_MAIL;

            MimeMessage resultMsg = new MimeMessage(Session.getInstance(new Properties()));
            resultMsg.setContent(encryptedMsg, CMSUtils.SMIME_CONTENT_AUTH_ENVELOPED_TYPE);
            resultMsg.setDisposition(CMSUtils.SMIME_DISPOSITION);
            resultMsg.setHeader(DATE, date);
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
            resultMsg.addHeader(X_KIM_DIENSTKENNUNG, dienstkennung);
            resultMsg = setRecipients(logger, recipientCerts, originMimeMessage, resultMsg, Message.RecipientType.TO);
            resultMsg = setRecipients(logger, recipientCerts, originMimeMessage, resultMsg, Message.RecipientType.CC);
            resultMsg = setRecipients(logger, recipientCerts, originMimeMessage, resultMsg, Message.RecipientType.BCC);
            resultMsg.addHeader(X_KOM_LE_VERSION, konfiguration.getXkimPtShortVersion());

            if (testMail) {
                resultMsg.addHeader(X_KIM_TESTNACHRICHT, "true");
                resultMsg.addHeader(X_KIM_TESTID, UUID.randomUUID().toString());
            }

            resultMsg.setSubject(SUBJECT_KOM_LE_NACHRICHT);
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
            resultMsg.setHeader(EXPIRES, ZonedDateTime.now().plusDays(
                logger.getDefaultLoggerContext().getAccountLimit() == null ? 90 : logger.getDefaultLoggerContext().getAccountLimit().getDataTimeToLive()
            ).format(RFC822_DATE_FORMAT));

            resultMsg.setHeader(X_KIM_CMVERSION, konfiguration.getXkimCmVersion());
            resultMsg.setHeader(X_KIM_PTVERSION, konfiguration.getXkimPtVersion());
            resultMsg.setHeader(X_KIM_KONVERSION, MessageFormat.format(
                "<{0}><{1}><{2}><{3}><{4}>",
                konnektor.getProductName(),
                konnektor.getProductType(),
                konnektor.getProductTypeVersion(),
                konnektor.getHwVersion(),
                konnektor.getFwVersion()
            ));

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            resultMsg.writeTo(byteArrayOutputStream);

            timeMetric.stopAndPublish();

            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("error on composing encrypt part for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X012);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X012 + " - " + EnumErrorCode.CODE_X012.getHrText());
            if (throwException) {
                throw e;
            } else {
                return null;
            }
        }
    }

    public byte[] signEncrypt(
        DefaultLogger logger,
        MimeMessage originMimeMessage,
        List<X509CertificateResult> recipientCerts,
        List<X509CertificateResult> fromSenderCerts,
        boolean testMail
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:signEncrypt");

            //is a mimebodypart encrypted?
            if (originMimeMessage.isMimeType("multipart/mixed")
                &&
                originMimeMessage.getContent() instanceof MimeMultipart
                &&
                ((MimeMultipart) originMimeMessage.getContent()).getCount() == 2
            ) {
                MimeMultipart mimeMultipart = (MimeMultipart) originMimeMessage.getContent();
                BodyPart bodyPart = null;
                if (mimeMultipart.getBodyPart(0).isMimeType("message/rfc822")) {
                    bodyPart = mimeMultipart.getBodyPart(0);
                } else if (mimeMultipart.getBodyPart(1).isMimeType("message/rfc822")) {
                    bodyPart = mimeMultipart.getBodyPart(1);
                }
                if (bodyPart != null) {
                    MimeMessage encryptedBodyPart = MailUtils.createMimeMessage(null, bodyPart.getInputStream(), true);
                    checkMailEncryptFormat(logger, encryptedBodyPart, false);
                    if (logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().isEmpty()) {
                        logger.logLine("bodypart is encrypted");
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        encryptedBodyPart.writeTo(byteArrayOutputStream);
                        byte[] result = byteArrayOutputStream.toByteArray();
                        byteArrayOutputStream.close();
                        return result;
                    } else {
                        logger.logLine("bodypart is not encrypted");
                    }
                }
            }

            String cardSignHandle = getSignCardHandle(logger, false);
            if (cardSignHandle == null) {
                timeMetric.stopAndPublish();
                return null;
            }

            List<X509CertificateResult> recipientSenderCerts = new ArrayList<>(recipientCerts);
            boolean add = true;
            for (Iterator<X509CertificateResult> iterator = fromSenderCerts.iterator(); iterator.hasNext(); ) {
                add = true;
                X509CertificateResult fromSender = iterator.next();
                for (Iterator<X509CertificateResult> iterator2 = recipientSenderCerts.iterator(); iterator2.hasNext(); ) {
                    X509CertificateResult x509CertificateResult = iterator2.next();
                    if (x509CertificateResult.getMailAddress().equals(fromSender.getMailAddress().toLowerCase())) {
                        add = false;
                        break;
                    }
                }
                if (add) {
                    recipientSenderCerts.add(fromSender);
                }
            }

            byte[] signedMsg = sign(logger, originMimeMessage, cardSignHandle, recipientSenderCerts, false);
            if (signedMsg == null) {
                timeMetric.stopAndPublish();
                return null;
            }

            byte[] encryptedMsg = encrypt(logger, recipientSenderCerts, signedMsg, false);
            if (encryptedMsg == null) {
                timeMetric.stopAndPublish();
                return null;
            }

            byte[] result = composeEncryptedMsg(logger, encryptedMsg, originMimeMessage, recipientCerts, testMail, false);

            timeMetric.stopAndPublish();
            return result;
        } catch (Exception e) {
            log.error("error on mail signing and encrypting for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    private MimeMessage setRecipients(
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

    private boolean checkHeader(DefaultLogger logger, Konnektor konnektor, MimeMessage encryptedMsg, MimeMessage decryptedAndVerifiedMsg, String headerName) throws Exception {
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

    private MimeMessage removeRecipients(DefaultLogger logger, List<X509CertificateResult> recipientCerts, MimeMessage originMessage, Message.RecipientType type) throws Exception {
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

    public byte[] sign(
        DefaultLogger logger,
        MimeMessage originMimeMessage,
        String signCardHandle,
        List<X509CertificateResult> certs,
        boolean throwException) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:sign");

            SignDocumentResponse signDocumentResponse = signatureService.sign(
                logger,
                originMimeMessage,
                signCardHandle,
                certs
            );

            if (signDocumentResponse.getSignResponse().isEmpty()) {
                throw new IllegalStateException("empty sign response for the cardHandle: " + signCardHandle);
            }
            SignResponse signResponse = signDocumentResponse.getSignResponse().get(0);
            if (!signResponse.getStatus().getResult().equals("OK")) {
                throw new IllegalStateException("sign response not ok for the cardHandle: " + signCardHandle + " - " + signResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + signResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
            }
            SignatureObject signatureObject = signResponse.getSignatureObject();
            if (signatureObject == null) {
                throw new IllegalStateException("sign response signatureObject empty for the cardHandle: " + signCardHandle);
            }
            if (signatureObject.getBase64Signature().getValue() == null) {
                throw new IllegalStateException("sign response signatureObject empty for the cardHandle: " + signCardHandle);
            }

            MimeBodyPart mimeBodyPartSignedMsg = new MimeBodyPart();
            mimeBodyPartSignedMsg.setContent(signatureObject.getBase64Signature().getValue(), CMSUtils.SMIME_CONTENT_TYPE);
            mimeBodyPartSignedMsg.setHeader("Content-Type", CMSUtils.SMIME_CONTENT_TYPE);
            mimeBodyPartSignedMsg.setHeader("Content-Transfer-Encoding", "binary");
            mimeBodyPartSignedMsg.setDisposition(CMSUtils.SMIME_DISPOSITION);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mimeBodyPartSignedMsg.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();

            timeMetric.stopAndPublish();

            return result;
        } catch (Exception e) {
            log.error("error on mail signing for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X009);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X009 + " - " + EnumErrorCode.CODE_X009.getHrText());
            if (throwException) {
                throw e;
            } else {
                return null;
            }
        }
    }

    public byte[] decryptVerify(
        DefaultLogger logger,
        String userMailAddress,
        MimeMessage encryptedMsg
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:decrypt");

            //Header X-KOM-LE-Version available
            if (encryptedMsg.getHeader(X_KOM_LE_VERSION) == null || encryptedMsg.getHeader(X_KOM_LE_VERSION).length == 0) {
                logger.logLine("Header " + X_KOM_LE_VERSION + " not available");

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                encryptedMsg.writeTo(byteArrayOutputStream);
                byte[] result = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();

                timeMetric.stopAndPublish();
                return result;
            }
            logger.logLine("Header " + X_KOM_LE_VERSION + " available");

            //unterstützte versionen nicht verfügbar
            String[] komLeVersionHeader = encryptedMsg.getHeader(X_KOM_LE_VERSION);
            if (!VALID_KIM_VERSIONS.contains(komLeVersionHeader[0])) {
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X014);
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X014 + " - " + EnumErrorCode.CODE_X014.getHrText());
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());
                timeMetric.stopAndPublish();
                return null;
            }

            //unterstützte version kleiner als die version der mail
            String komLeVersion = komLeVersionHeader[0];
            if (StringUtils.isNewVersionHigher(logger.getDefaultLoggerContext().getKonfiguration().getXkimPtShortVersion(), komLeVersion)) {
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());

                timeMetric.stopAndPublish();
                return null;
            }

            //analyze mail
            log.info("analyze mail encrypt format");
            logger.logLine("analyze mail encrypt format");

            if (!checkMailEncryptFormat(logger, encryptedMsg, true)) {
                log.error("mail encrypt format is wrong");
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4010);
                throw new IllegalStateException("mail encrypt format is wrong");
            }

            log.info("analyze mail encrypt format finished");
            logger.logLine("analyze mail encrypt format finished");

            byte[] encryptedPart = logger.getDefaultLoggerContext().getEncryptedPart();
            ContentInfo contentInfo = logger.getDefaultLoggerContext().getEncryptedContentInfo();

            //card handle
            log.info("getDecryptCardHandle");
            logger.logLine("getDecryptCardHandle");

            String decryptCardHandle = getDecryptCardHandle(logger, userMailAddress, contentInfo, false);
            if (decryptCardHandle == null) {
                timeMetric.stopAndPublish();
                return null;
            }

            log.info("getDecryptCardHandle finished: " + decryptCardHandle);
            logger.logLine("getDecryptCardHandle finished: " + decryptCardHandle);

            //decrypt
            DecryptDocumentResponse decryptDocumentResponse = null;
            DocumentType documentType = null;
            try {
                decryptDocumentResponse = encryptionService.decryptMail(logger, decryptCardHandle, encryptedPart);
                if (!decryptDocumentResponse.getStatus().getResult().equals("OK")) {
                    throw new IllegalStateException("decrypt response not ok for the konnektor: " + konnektor.getIp() + " - " + decryptDocumentResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + decryptDocumentResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
                }
                documentType = decryptDocumentResponse.getDocument();
                if (documentType == null) {
                    throw new IllegalStateException("decrypt response document empty for the konnektor: " + konnektor.getIp());
                }
                if (documentType.getBase64Data().getValue() == null) {
                    throw new IllegalStateException("decrypt response document empty for the konnektor: " + konnektor.getIp());
                }
            } catch (Exception e) {
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());
                timeMetric.stopAndPublish();
                return null;
            }

            //extract decrypted message and parse signed content
            byte[] decryptedMsgBytes = null;
            byte[] signedContent = null;
            try {
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(documentType.getBase64Data().getValue());
                MimePart mimePart = new MimeBodyPart(byteArrayInputStream);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                InputStream is = mimePart.getInputStream();
                for (int c = is.read(); c != -1; c = is.read()) {
                    byteArrayOutputStream.write(c);
                }
                decryptedMsgBytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();

                signedContent = CMSUtils.extractSignedContent(mimePart, true);
            } catch (Exception e) {
                log.error("error on extract decrypted message and parse signed content for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress, e);
                logger.logLine("error on extract decrypted message and parse signed content for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X023);
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4253);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X023 + " - " + EnumErrorCode.CODE_X023.getHrText());
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4253 + " - " + EnumErrorCode.CODE_4253.getHrText());

                timeMetric.stopAndPublish();
                return null;
            }

            //verify document
            File signReportFile = null;
            try {
                VerifyDocumentResponse verifyDocumentResponse = signatureService.verify(logger, decryptedMsgBytes);

                if (!verifyDocumentResponse.getStatus().getResult().equals("OK")) {
                    logger.logLine("verify response not ok for the konnektor: " + konnektor.getIp() + " - " + verifyDocumentResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + verifyDocumentResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
                    logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4115);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_4115 + " - " + EnumErrorCode.CODE_4115.getHrText());
                    timeMetric.stopAndPublish();
                    return null;
                }

                if (verifyDocumentResponse.getVerificationResult() != null) {
                    logger.logLine("verification result: " + verifyDocumentResponse.getVerificationResult().getHighLevelResult());
                }

                VerifyDocumentResponse.OptionalOutputs oo = verifyDocumentResponse.getOptionalOutputs();
                if (oo != null && oo.getVerificationReport() != null) {
                    VerificationReportType verificationReportType = oo.getVerificationReport();

                    if (verificationReportType == null) {
                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4253);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4253 + " - " + EnumErrorCode.CODE_4253.getHrText());
                        timeMetric.stopAndPublish();
                        return null;
                    }

                    signReportFile = signReportService.execute(logger, verificationReportType);
                    if (signReportFile == null) {
                        timeMetric.stopAndPublish();
                        return null;
                    }
                } else {
                    logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4253);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_4253 + " - " + EnumErrorCode.CODE_4253.getHrText());
                    timeMetric.stopAndPublish();
                    return null;
                }
            } catch (Exception e) {
                log.error("error on verifying message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress, e);
                logger.logLine("error on verifying message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());

                timeMetric.stopAndPublish();
                return null;
            }

            //extracted decrypted and verified message
            byte[] preHeader = "Content-Type: message/rfc822\r\n\r\n".getBytes(StandardCharsets.UTF_8);
            int preHeaderLength = preHeader.length;
            byte[] signedContentPayload = Arrays.copyOfRange(signedContent, preHeaderLength, signedContent.length);
            MimeMessage decryptedAndVerifiedMessage = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(signedContentPayload));

            checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, FROM);
            checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, SENDER);
            checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, REPLY_TO);
            checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, TO);
            checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, CC);
            checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, BCC);

            //set header Return-Path, Received
            logger.logLine("set " + RETURN_PATH);
            String[] returnPath = encryptedMsg.getHeader(RETURN_PATH);
            if (returnPath != null && returnPath.length > 0) {
                decryptedAndVerifiedMessage.setHeader(RETURN_PATH, returnPath[0]);
            }
            logger.logLine("set " + RECEIVED);
            String[] received = encryptedMsg.getHeader(RECEIVED);
            if (received != null && received.length > 0) {
                for (int i = 0; i < received.length; i++) {
                    decryptedAndVerifiedMessage.addHeader(RECEIVED, received[i]);
                }
            }
            logger.logLine("set " + REPLY_TO);
            String[] replyTo = encryptedMsg.getHeader(REPLY_TO);
            if (replyTo != null && replyTo.length > 0) {
                decryptedAndVerifiedMessage.setHeader(REPLY_TO, replyTo[0]);
            }

            //add signature report
            decryptedAndVerifiedMessage = mailPartContentService.addAttachment(logger, decryptedAndVerifiedMessage, signReportFile, "Signaturpruefbericht.pdf", "application/pdf");
            decryptedAndVerifiedMessage = mailPartContentService.addText(logger, decryptedAndVerifiedMessage, "----------------------------------\n!!!Die Signatur wurde erfolgreich geprueft!!!\n----------------------------------");

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            decryptedAndVerifiedMessage.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();

            timeMetric.stopAndPublish();

            return result;
        } catch (Exception e) {
            log.error("error on mail decrypting for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public byte[] encrypt(
        DefaultLogger logger,
        List<X509CertificateResult> certs,
        byte[] mail,
        boolean throwException
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:encrypt");

            EncryptDocumentResponse encryptDocumentResponse = encryptionService.encryptMail(
                logger,
                certs,
                mail
            );

            if (!encryptDocumentResponse.getStatus().getResult().equals("OK")) {
                throw new IllegalStateException("encrypt response not ok for the konnektor: " + konnektor.getIp() + " - " + encryptDocumentResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + encryptDocumentResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
            }
            DocumentType documentType = encryptDocumentResponse.getDocument();
            if (documentType == null) {
                throw new IllegalStateException("encrypt response document empty for the konnektor: " + konnektor.getIp());
            }
            if (documentType.getBase64Data().getValue() == null) {
                throw new IllegalStateException("encrypt response document empty for the konnektor: " + konnektor.getIp());
            }

            timeMetric.stopAndPublish();

            return documentType.getBase64Data().getValue();
        } catch (Exception e) {
            log.error("error on mail signing for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X011);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X011 + " - " + EnumErrorCode.CODE_X011.getHrText());
            if (throwException) {
                throw e;
            } else {
                return null;
            }
        }
    }

    private String getDecryptCardHandle(
        DefaultLogger logger,
        String userMailAddress,
        ContentInfo contentInfo,
        boolean throwException
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:getDecryptCardHandle");

            List<IssuerAndSerial> certIssuerAndSerialNumbers = CMSUtils.getCertIssuerAndSerialNumber(contentInfo, userMailAddress);
            if (certIssuerAndSerialNumbers.isEmpty()) {
                log.error("error on getDecryptCardHandle - CertIssuerAndSerialNumber in contentinfo not available for mailaddress: " + userMailAddress);
                logger.logLine("error on getDecryptCardHandle - CertIssuerAndSerialNumber in contentinfo not available for mailaddress: " + userMailAddress);
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X022);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X022 + " - " + EnumErrorCode.CODE_X022.getHrText());
                throw new IllegalStateException("error on getDecryptCardHandle - CertIssuerAndSerialNumber in contentinfo not available for mailaddress: " + userMailAddress);
            }

            //load all cards
            log.info("load all cards - start");
            logger.logLine("load all cards - start");

            try {
                konnektor = konnektorCardService.loadAllCards(logger);
            } catch (Exception e) {
                log.error("error on getDecryptCardHandle - load all cards - konnektor not available for: " + konnektor.getIp(), e);
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());
                throw new IllegalStateException("error on getDecryptCardHandle - load all cards - konnektor not available for: " + konnektor.getIp());
            }

            log.info("load all cards - finished");
            logger.logLine("load all cards - finished");

            List<KonnektorCard> cards = konnektor.getCards();
            KonnektorCard selectedCard = null;
            for (Iterator<KonnektorCard> iterator = cards.iterator(); iterator.hasNext(); ) {
                KonnektorCard konnektorCard = iterator.next();

                //Verschlüsselungs-Zertifikate laden für die Karte
                if (KonnektorWebserviceUtils.interestingCardTypes.contains(konnektorCard.getCardType())) {
                    try {
                        ReadCardCertificateResponse readCardCertificateResponse = certificateService.getCertificate(
                            logger,
                            KonnektorWebserviceUtils.CERT_REF_ENC,
                            konnektorCard.getCardHandle()
                        );
                        if (!readCardCertificateResponse.getStatus().getResult().equals(KonnektorWebserviceUtils.STATUS_OK)) {
                            logger.logLine("request getCertificate status not OK for the konnektor: " + konnektor.getIp() + " and the cardHandle: " + konnektorCard.getCardHandle() + " and the certRef: " + KonnektorWebserviceUtils.CERT_REF_ENC);
                            continue;
                        }
                        boolean contains = false;
                        for (Iterator<X509DataInfoListType.X509DataInfo> konnektorCardIterator = readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().iterator(); konnektorCardIterator.hasNext(); ) {
                            X509DataInfoListType.X509DataInfo x509DataInfo = konnektorCardIterator.next();
                            IssuerAndSerial issuerAndSerial = new IssuerAndSerial();
                            issuerAndSerial.setSerialNumber(x509DataInfo.getX509Data().getX509IssuerSerial().getX509SerialNumber());
                            issuerAndSerial.setIssuer(x509DataInfo.getX509Data().getX509IssuerSerial().getX509IssuerName());
                            if (certIssuerAndSerialNumbers.contains(issuerAndSerial)) {
                                contains = true;
                                break;
                            }
                        }
                        if (!contains) {
                            logger.logLine("no enc certificate available for card handle: " + konnektorCard.getCardHandle());
                            continue;
                        }
                        logger.logLine("enc certificate available for card handle: " + konnektorCard.getCardHandle());
                    } catch (Exception e) {
                        log.error("error on getDecryptCardHandle - load enc certificate - card handle: " + konnektorCard.getCardHandle() + " konnektor not available for: " + konnektor.getIp(), e);
                        logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());
                        throw new IllegalStateException("error on getDecryptCardHandle - load enc certificate - card handle: " + konnektorCard.getCardHandle() + " konnektor not available for: " + konnektor.getIp());
                    }
                } else {
                    continue;
                }

                log.info("analyze card: " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType() + " - " + konnektorCard.getPinStatus() + " - " + konnektorCard.getTelematikId());
                logger.logLine("analyze card: " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType() + " - " + konnektorCard.getPinStatus() + " - " + konnektorCard.getTelematikId());

                if (!konnektorCard.getCardType().equals(CardTypeType.SMC_B.value())) {
                    log.info("konnektor card is not a smcb: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType());
                    logger.logLine("konnektor card is not a smcb: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType());
                    continue;
                }
                if (konnektorCard.getPinStatus().equals(PinStatusEnum.BLOCKED.value())) {
                    log.info("konnektor card is blocked for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    logger.logLine("konnektor card is blocked for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    continue;
                }
                if (konnektorCard.getPinStatus().equals(PinStatusEnum.VERIFIABLE.value())) {
                    log.info("konnektor card is verifiable for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    logger.logLine("konnektor card is verifiable for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    continue;
                }
                if (konnektorCard.getPinStatus().equals(PinStatusEnum.VERIFIED.value())) {
                    selectedCard = konnektorCard;
                }
            }

            if (selectedCard != null) {
                timeMetric.stopAndPublish();
                return selectedCard.getCardHandle();
            }

            logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4009);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_4009 + " - " + EnumErrorCode.CODE_4009.getHrText());
            throw new IllegalStateException("getDecryptCardHandle not found for konnektor: " + konnektor.getIp() + " - " + userMailAddress);
        } catch (Exception e) {
            log.error("error on getDecryptCardHandle for konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X021);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X021 + " - " + EnumErrorCode.CODE_X021.getHrText());
            if (throwException) {
                throw e;
            } else {
                return null;
            }
        }
    }

    public String getSignCardHandle(DefaultLogger logger, boolean throwException) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("MailService:getSignCardHandle");

            //load all cards
            log.info("load all cards - start");
            logger.logLine("load all cards - start");
            konnektor = konnektorCardService.loadAllCards(logger);
            log.info("load all cards - finished");
            logger.logLine("load all cards - finished");

            List<KonnektorCard> cards = konnektor.getCards();
            KonnektorCard selectedCard = null;
            for (Iterator<KonnektorCard> iterator = cards.iterator(); iterator.hasNext(); ) {
                KonnektorCard konnektorCard = iterator.next();

                log.info("analyze card: " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType() + " - " + konnektorCard.getPinStatus() + " - " + konnektorCard.getTelematikId());
                logger.logLine("analyze card: " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType() + " - " + konnektorCard.getPinStatus() + " - " + konnektorCard.getTelematikId());

                if (!konnektorCard.getCardType().equals(CardTypeType.SMC_B.value())) {
                    log.info("konnektor card is not a smcb: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType());
                    logger.logLine("konnektor card is not a smcb: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn() + " - " + konnektorCard.getCardType());
                    continue;
                }
                if (konnektorCard.getPinStatus().equals(PinStatusEnum.BLOCKED.value())) {
                    log.info("konnektor card is blocked for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    logger.logLine("konnektor card is blocked for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    continue;
                }
                if (konnektorCard.getPinStatus().equals(PinStatusEnum.VERIFIABLE.value())) {
                    log.info("konnektor card is verifiable for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    logger.logLine("konnektor card is verifiable for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    continue;
                }
                if (konnektorCard.getPinStatus().equals(PinStatusEnum.VERIFIED.value())) {
                    selectedCard = konnektorCard;
                    log.info("konnektor card is verified for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    logger.logLine("konnektor card is verified for konnektor: " + konnektor.getIp() + " -> " + konnektorCard.getCardHandle() + " - " + konnektorCard.getIccsn());
                    break;
                }
            }

            if (selectedCard != null) {
                timeMetric.stopAndPublish();
                return selectedCard.getCardHandle();
            }

            throw new IllegalStateException("cardHandle not found for konnektor: " + konnektor.getIp());
        } catch (Exception e) {
            log.error("error on getSignCardHandle for konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            logger.getDefaultLoggerContext().getMailSignEncryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X010);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X010 + " - " + EnumErrorCode.CODE_X010.getHrText());
            if (throwException) {
                throw e;
            } else {
                return null;
            }
        }
    }
}
