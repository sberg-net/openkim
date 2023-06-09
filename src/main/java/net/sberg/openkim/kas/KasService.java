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
package net.sberg.openkim.kas;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.mail.MailPartContent;
import net.sberg.openkim.mail.MailPartContentService;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;

import javax.activation.DataHandler;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class KasService {

    private static final String CM_1_5_VERSION = "1.5";

    private static final Logger log = LoggerFactory.getLogger(KasService.class);

    @Autowired
    private MailPartContentService mailPartContentService;

    public MimeMessage executeOutgoing(DefaultLogger logger, MimeMessage mimeMessage, SmtpGatewaySession smtpGatewaySession) throws KasServiceException {
        try {

            Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

            smtpGatewaySession.log("start executeOutgoing message");

            MailPartContent mailPartContent = mailPartContentService.analyze(logger, mimeMessage);

            double totalSize = mailPartContent.sumTotalSize();

            smtpGatewaySession.log("total mailsize " + totalSize + " in Bytes");

            if (totalSize <= konfiguration.getMailSizeLimitInMB() * 1024 * 1024) {
                smtpGatewaySession.log("kas not used");
                return mimeMessage;
            }

            //check rcpts kim version > 1.0
            MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = smtpGatewaySession.getLogger().getDefaultLoggerContext().getMailaddressKimVersionErrorContext();
            for (Iterator<X509CertificateResult> iterator = smtpGatewaySession.getRecipientCerts().iterator(); iterator.hasNext(); ) {
                X509CertificateResult rcptX509CertificateResult = iterator.next();
                smtpGatewaySession.log("check komle-version for rcpt: " + rcptX509CertificateResult.getMailAddress());
                String cmVersion = rcptX509CertificateResult.getVzdResults().get(0).getKomleVersion();
                if (StringUtils.isNewVersionHigher(cmVersion, CM_1_5_VERSION)) {
                    mailaddressKimVersionErrorContext.add(rcptX509CertificateResult, EnumErrorCode.CODE_4001, false);
                    smtpGatewaySession.log("false check komle-version ending for rcpt: " + rcptX509CertificateResult.getMailAddress() + " - " + CM_1_5_VERSION + " - " + cmVersion);
                }
                smtpGatewaySession.log("check komle-version ending for rcpt: " + rcptX509CertificateResult.getMailAddress());
            }

            if (smtpGatewaySession.extractNoFailureKimVersionRcpts().isEmpty()) {
                return mimeMessage;
            }

            smtpGatewaySession.log("kas used");

            Address[] recipientsArr = mimeMessage.getAllRecipients();
            List<String> recipients = new ArrayList<>();
            for (int i = 0; i < recipientsArr.length; i++) {
                recipients.add(recipientsArr[i].toString());
            }

            List<MailPartContent> attachments = new ArrayList<>();
            attachments = mailPartContent.collectAllAttachments(attachments);

            ZonedDateTime date = ZonedDateTime.now().plusMonths(12);
            String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(date);

            File attachmentDir = new File(
                System.getProperty("java.io.tmpdir")
                + File.separator
                + mimeMessage.getMessageID()
                + System.currentTimeMillis()
                + File.separator
            );
            attachmentDir.mkdirs();

            for (Iterator<MailPartContent> iterator = attachments.iterator(); iterator.hasNext(); ) {

                MailPartContent attMailPartContent = iterator.next();

                //read file
                File attachmentFileInput = null;
                File attachmentFileOutput = null;
                byte[] attachmentBytes;
                try {
                    attachmentFileInput = readBinaries((BodyPart) attMailPartContent.getMimePart(), attachmentDir);
                    attachmentBytes = IOUtils.toByteArray(((BodyPart) attMailPartContent.getMimePart()).getInputStream());
                    smtpGatewaySession.log("read attachment: " + attachmentFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on reading the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + ((MimeBodyPart) attMailPartContent.getMimePart()).getFileName(), e);
                    smtpGatewaySession.log("read attachment ends " + ((MimeBodyPart) attMailPartContent.getMimePart()).getFileName() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.readAttachmentFromMail,
                        ((MimeBodyPart) attMailPartContent.getMimePart()).getFileName(),
                        "error on reading the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + ((MimeBodyPart) attMailPartContent.getMimePart()).getFileName(),
                        e
                    );
                }

                //encrypt
                SecretKey secretKey = null;
                try {
                    secretKey = AesGcmHelper.getAESKey(AesGcmHelper.AES_KEY_BIT);
                    byte[] iv = AesGcmHelper.getRandomNonce(AesGcmHelper.IV_LENGTH_BYTE);

                    attachmentFileOutput = new File(attachmentDir.getAbsolutePath() + File.separator + "output_" + attachmentFileInput.getName());
                    if (attachmentFileOutput.exists()) {
                        attachmentFileOutput.delete();
                    }

                    AesGcmHelper.encryptWithStream(attachmentFileOutput, attachmentFileInput, secretKey, iv, true);
                    smtpGatewaySession.log("encrypt attachment: " + attachmentFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on encrypting the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(), e);
                    smtpGatewaySession.log("encrypt attachment ends " + attachmentFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.encryptAttachment,
                        attachmentFileInput.getAbsolutePath(),
                        "error on encrypting the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(),
                        e
                    );
                }

                ResponseEntity<Object> apiResult = null;
                try {
                    apiResult = logger.getDefaultLoggerContext().getFachdienst().getAttachmentsApi().addAttachmentWithHttpInfo(mimeMessage.getMessageID(), recipients, expires, attachmentFileOutput);
                    attachmentFileOutput.delete();
                    if (apiResult.getStatusCode().equals(HttpStatus.CREATED)) {
                        smtpGatewaySession.log("send attachment to kas-service: " + attachmentFileInput.getAbsolutePath() + " ends");
                    } else if (apiResult.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                        smtpGatewaySession.log("send attachment to kas-service: " + attachmentFileInput.getAbsolutePath() + " - error " + HttpStatus.BAD_REQUEST);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentBadRequest,
                            attachmentFileInput.getAbsolutePath(),
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
                        smtpGatewaySession.log("send attachment to kas-service: " + attachmentFileInput.getAbsolutePath() + " - error " + HttpStatus.UNAUTHORIZED);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentUnauthorized,
                            attachmentFileInput.getAbsolutePath(),
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.PAYLOAD_TOO_LARGE)) {
                        smtpGatewaySession.log("send attachment to kas-service: " + attachmentFileInput.getAbsolutePath() + " - error " + HttpStatus.PAYLOAD_TOO_LARGE);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentPayloadTooLarge,
                            attachmentFileInput.getAbsolutePath(),
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
                        smtpGatewaySession.log("send attachment to kas-service: " + attachmentFileInput.getAbsolutePath() + " - error " + HttpStatus.INTERNAL_SERVER_ERROR);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentInternalServerError,
                            attachmentFileInput.getAbsolutePath(),
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.INSUFFICIENT_STORAGE)) {
                        smtpGatewaySession.log("send attachment to kas-service: " + attachmentFileInput.getAbsolutePath() + " - error " + HttpStatus.INSUFFICIENT_STORAGE);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentInsufficientStorage,
                            attachmentFileInput.getAbsolutePath(),
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath()
                        );
                    }
                } catch (KasServiceException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(), e);
                    smtpGatewaySession.log("send attachment ends " + attachmentFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.sendAttachment,
                        attachmentFileInput.getAbsolutePath(),
                        "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(),
                        e
                    );
                }

                //hash plain attachment
                String attachmentEncodedHash = null;
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    attachmentEncodedHash = Base64Utils.encodeToString(FileUtils.getFileChecksum(digest, attachmentFileInput));
                    smtpGatewaySession.log("hash attachment: " + attachmentFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on hashing the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(), e);
                    smtpGatewaySession.log("hash attachment ends " + attachmentFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.hashPlainAttachment,
                        attachmentFileInput.getAbsolutePath(),
                        "error on hashing the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(),
                        e
                    );
                }

                //create x-kas mimebodypart
                try {
                    HashMap body = (HashMap) apiResult.getBody();
                    KasMetaObj kasMetaObj = new KasMetaObj();
                    kasMetaObj.setHash(attachmentEncodedHash);
                    kasMetaObj.setK(Base64Utils.encodeToString(secretKey.getEncoded()));
                    kasMetaObj.setLink((String) body.get("sharedLink"));
                    kasMetaObj.setSize((int) attMailPartContent.getAttachementSize());
                    kasMetaObj.setType(((MimeBodyPart) attMailPartContent.getMimePart()).getContentType().split(";")[0]);
                    kasMetaObj.setName(attachmentFileInput.getName());

                    Enumeration<Header> headerEnum = ((BodyPart) attMailPartContent.getMimePart()).getAllHeaders();
                    while (headerEnum.hasMoreElements()) {
                        Header header = headerEnum.nextElement();
                        ((MimeBodyPart) attMailPartContent.getMimePart()).removeHeader(header.getName());
                    }

                    ((MimeBodyPart) attMailPartContent.getMimePart()).setDisposition("x-kas");
                    ((MimeBodyPart) attMailPartContent.getMimePart()).setText(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(kasMetaObj), "utf-8");
                    mimeMessage.saveChanges();
                    smtpGatewaySession.log("creating x-kas mimebodypart x-kas mimebodypart: " + attachmentFileInput.getAbsolutePath() + " ends");
                    attachmentFileInput.delete();
                } catch (Exception e) {
                    log.error("error on creating x-kas mimebodypart of the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(), e);
                    smtpGatewaySession.log("creating x-kas mimebodypart attachment ends " + attachmentFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.creatingXkasMimebodypart,
                        attachmentFileInput.getAbsolutePath(),
                        "error on creating x-kas mimebodypart of the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + attachmentFileInput.getAbsolutePath(),
                        e
                    );
                }
            }

            smtpGatewaySession.log("ends executeOutgoing message");
            return mimeMessage;
        } catch (KasServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("error on executeOutgoing message: " + smtpGatewaySession.getSessionID(), e);
            throw new KasServiceException(
                EnumKasServiceErrorCode.technical,
                "unknown",
                "error on executeOutgoing message: " + smtpGatewaySession.getSessionID(),
                e
            );
        }
    }

    public MimeMessage executeIncoming(DefaultLogger logger, MimeMessage mimeMessage, Pop3GatewaySession pop3GatewaySession) throws KasServiceException {
        try {
            pop3GatewaySession.log("start executeIncoming message");

            MailPartContent mailPartContent = mailPartContentService.analyze(logger, mimeMessage);

            List<MailPartContent> xkasParts = new ArrayList<>();
            xkasParts = mailPartContent.collectAllXKasParts(xkasParts);

            File attachmentDir = new File(
                System.getProperty("java.io.tmpdir")
                + File.separator
                + mimeMessage.getMessageID()
                + System.currentTimeMillis()
                + File.separator
            );
            attachmentDir.mkdirs();

            String originSubject = mimeMessage.getSubject();

            for (Iterator<MailPartContent> iterator = xkasParts.iterator(); iterator.hasNext(); ) {

                MailPartContent attMailPartContent = iterator.next();

                KasMetaObj kasMetaObj = new ObjectMapper().readValue((String) attMailPartContent.getMimePart(), KasMetaObj.class);

                ResponseEntity<File> apiResult = null;
                EnumKasServiceErrorCode kasServiceErrorCode = EnumKasServiceErrorCode.unknown;
                try {
                    apiResult = logger.getDefaultLoggerContext().getFachdienst().getAttachmentsApi().readAttachmentWithHttpInfo(
                        kasMetaObj.getLink(),
                        pop3GatewaySession.getLogger().getDefaultLoggerContext().getMailServerUsername()
                    );
                    if (apiResult.getStatusCode().equals(HttpStatus.OK)) {
                        pop3GatewaySession.log("read attachment from kas-service: " + kasMetaObj.getLink() + " ends");
                    } else if (apiResult.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                        pop3GatewaySession.log("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.FORBIDDEN);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.readAttachmentForbidden,
                            kasMetaObj.getLink(),
                            "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                        pop3GatewaySession.log("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.NOT_FOUND);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.readAttachmentNotFound,
                            kasMetaObj.getLink(),
                            "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS)) {
                        pop3GatewaySession.log("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.TOO_MANY_REQUESTS);
                        kasServiceErrorCode = EnumKasServiceErrorCode.readAttachmentTooManyRequests;
                    } else if (apiResult.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
                        pop3GatewaySession.log("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.INTERNAL_SERVER_ERROR);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.readAttachmentInternalServerError,
                            kasMetaObj.getLink(),
                            "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink()
                        );
                    }
                } catch (KasServiceException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(), e);
                    pop3GatewaySession.log("read attachment ends " + kasMetaObj.getLink() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.readAttachment,
                        kasMetaObj.getLink(),
                        "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(),
                        e
                    );
                }

                ////create error mimebodypart -> A_22412 - Behandlung von Zugriffs-Limitierung
                if (kasServiceErrorCode.equals(EnumKasServiceErrorCode.readAttachmentTooManyRequests)) {
                    try {
                        Enumeration<Header> headerEnum = ((BodyPart) attMailPartContent.getMimePart()).getAllHeaders();
                        while (headerEnum.hasMoreElements()) {
                            Header header = headerEnum.nextElement();
                            ((MimeBodyPart) attMailPartContent.getMimePart()).removeHeader(header.getName());
                        }

                        String name = kasMetaObj.getName().substring(0, kasMetaObj.getName().lastIndexOf("."));
                        String errorFileName = name + "_Fehlermeldung.txt";

                        File attachmentFileOutput = new File(attachmentDir.getAbsolutePath() + File.separator + System.nanoTime() + errorFileName);
                        if (attachmentFileOutput.exists()) {
                            attachmentFileOutput.delete();
                        }

                        KasMetaObjError kasMetaObjError = new KasMetaObjError();
                        kasMetaObjError.setName(kasMetaObj.getName());
                        kasMetaObjError.setSize(kasMetaObj.getSize());
                        kasMetaObjError.setType(kasMetaObj.getType());

                        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(attachmentFileOutput, kasMetaObjError);

                        ((MimeBodyPart) attMailPartContent.getMimePart()).setDataHandler(new DataHandler(attachmentFileOutput.toURI().toURL()));
                        ((MimeBodyPart) attMailPartContent.getMimePart()).setFileName(errorFileName);
                        ((MimeBodyPart) attMailPartContent.getMimePart()).setDisposition(Part.ATTACHMENT);

                        mimeMessage.setSubject("[Fehler beim Abruf eines Anhangs *_Fehlermeldung.txt] " + originSubject);
                        mimeMessage.saveChanges();
                    } catch (Exception e) {
                        log.error("error on creating original mimebodypart of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(), e);
                        pop3GatewaySession.log("creating original mimebodypart attachment ends " + kasMetaObj.getLink() + " - error");
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.creatingOriginalMimebodypart,
                            kasMetaObj.getLink(),
                            "error on creating original mimebodypart of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(),
                            e
                        );
                    }
                } else {
                    //decrypt
                    SecretKey secretKey = null;
                    File attachmentFileOutput = null;
                    try {
                        attachmentFileOutput = new File(attachmentDir.getAbsolutePath() + File.separator + "output_" + kasMetaObj.getName());
                        if (attachmentFileOutput.exists()) {
                            attachmentFileOutput.delete();
                        }
                        byte[] decKey = Base64Utils.decodeFromString(kasMetaObj.getK());
                        secretKey = new SecretKeySpec(decKey, AesGcmHelper.ENCRYPT_ALGO);
                        AesGcmHelper.decryptWithStreamWithPrefixIV(attachmentFileOutput, apiResult.getBody(), secretKey);
                        pop3GatewaySession.log("decrypt attachment from kas-service: " + kasMetaObj.getLink() + " ends");
                    } catch (Exception e) {
                        log.error("error on decrypting the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(), e);
                        pop3GatewaySession.log("decrypt attachment ends " + kasMetaObj.getLink() + " - error");
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.decryptAttachment,
                            kasMetaObj.getLink(),
                            "error on decrypting the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(),
                            e
                        );
                    }

                    //hash plain attachment -> check with origin
                    String attachmentEncodedHash = null;
                    try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        attachmentEncodedHash = Base64Utils.encodeToString(FileUtils.getFileChecksum(digest, attachmentFileOutput));
                        if (!kasMetaObj.getHash().equals(attachmentEncodedHash)) {
                            pop3GatewaySession.log("check hash attachment ends " + attachmentFileOutput.getAbsolutePath() + " - error");
                            throw new KasServiceException(
                                EnumKasServiceErrorCode.checkHashPlainAttachment,
                                attachmentFileOutput.getAbsolutePath(),
                                "error on checking the hash of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + attachmentFileOutput.getAbsolutePath()
                            );
                        } else {
                            pop3GatewaySession.log("check hash attachment: " + attachmentFileOutput.getAbsolutePath() + " ends");
                        }
                    } catch (Exception e) {
                        log.error("error on hashing the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + attachmentFileOutput.getAbsolutePath(), e);
                        pop3GatewaySession.log("hash attachment ends " + attachmentFileOutput.getAbsolutePath() + " - error");
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.hashPlainAttachment,
                            attachmentFileOutput.getAbsolutePath(),
                            "error on hashing the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + attachmentFileOutput.getAbsolutePath(),
                            e
                        );
                    }

                    //create original mimebodypart
                    try {
                        Enumeration<Header> headerEnum = ((BodyPart) attMailPartContent.getMimePart()).getAllHeaders();
                        while (headerEnum.hasMoreElements()) {
                            Header header = headerEnum.nextElement();
                            ((MimeBodyPart) attMailPartContent.getMimePart()).removeHeader(header.getName());
                        }

                        ((MimeBodyPart) attMailPartContent.getMimePart()).setDataHandler(new DataHandler(attachmentFileOutput.toURI().toURL()));
                        ((MimeBodyPart) attMailPartContent.getMimePart()).setFileName(kasMetaObj.getName());
                        ((MimeBodyPart) attMailPartContent.getMimePart()).setDisposition(Part.ATTACHMENT);

                        mimeMessage.saveChanges();
                    } catch (Exception e) {
                        log.error("error on creating original mimebodypart of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(), e);
                        pop3GatewaySession.log("creating original mimebodypart attachment ends " + kasMetaObj.getLink() + " - error");
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.creatingOriginalMimebodypart,
                            kasMetaObj.getLink(),
                            "error on creating original mimebodypart of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(),
                            e
                        );
                    }
                }
            }

            pop3GatewaySession.log("ends executeIncoming message");
            return mimeMessage;
        } catch (KasServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("error on executeIncoming message: " + pop3GatewaySession.getSessionID(), e);
            throw new KasServiceException(
                EnumKasServiceErrorCode.technical,
                "unknown",
                "error on executeIncoming message: " + pop3GatewaySession.getSessionID(),
                e
            );
        }
    }

    private File readBinaries(BodyPart bodyPart, File tmpDir) throws Exception {
        String fileName = bodyPart.getFileName();
        int idx = fileName.lastIndexOf(File.separator);
        if (idx != -1) {
            fileName = fileName.substring(idx + 1);
        }
        fileName = fileName.replaceAll("\\?", "");
        fileName = fileName.replaceAll("=", "");

        File result = new File(tmpDir.getAbsolutePath() + File.separator + fileName);
        if (result.exists()) {
            result.delete();
        }

        FileChannel outChannel = new FileOutputStream(result).getChannel();
        ReadableByteChannel inChannel = Channels.newChannel(bodyPart.getInputStream());
        ByteBuffer buffer = ByteBuffer.allocate(4096);

        while (true) {
            if (inChannel.read(buffer) == -1) {
                break;
            }
            buffer.flip();
            outChannel.write(buffer);
            buffer.clear();
        }

        inChannel.close();
        outChannel.close();

        return result;
    }

}
