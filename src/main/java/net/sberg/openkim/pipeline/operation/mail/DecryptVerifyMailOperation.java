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

import de.gematik.ws.conn.connectorcommon.v5.DocumentType;
import de.gematik.ws.conn.encryptionservice.v6.DecryptDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7.VerifyDocumentResponse;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.gateway.pop3.signreport.SignReportService;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.EnumKomLeVersion;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.DecryptDocumentOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.VerifySignedDocumentOperation;
import net.sberg.openkim.pipeline.operation.mail.part.AddMailAttachmentOperation;
import net.sberg.openkim.pipeline.operation.mail.part.AddMailTextOperation;
import oasis.names.tc.dss_x._1_0.profiles.verificationreport.schema_.VerificationReportType;
import org.apache.james.metrics.api.TimeMetric;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class DecryptVerifyMailOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(DecryptVerifyMailOperation.class);
    public static final String NAME = "DecryptVerifyMail";

    public static final String ENV_ENCRYPTED_MSG = "encryptedMsg";
    public static final String ENV_USER_MAIL_ADDRESS = "userMailAddress";
    public static final String ENV_RESULT_MSG_BYTES = "resultMsgBytes";

    @Autowired
    private CheckEncryptedMailFormatOperation checkEncryptedMailFormatOperation;
    @Autowired
    private GetDecryptCardHandleOperation getDecryptCardHandleOperation;
    @Autowired
    private DecryptDocumentOperation decryptDocumentOperation;
    @Autowired
    private VerifySignedDocumentOperation verifySignedDocumentOperation;
    @Autowired
    private AddMailAttachmentOperation addMailAttachmentOperation;
    @Autowired
    private AddMailTextOperation addMailTextOperation;
    @Autowired
    private SignReportService signReportService;

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

            MimeMessage encryptedMsg = (MimeMessage)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ENCRYPTED_MSG);
            String userMailAddress = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_USER_MAIL_ADDRESS);

            //Header X-KOM-LE-Version available -> not encrypted
            if (encryptedMsg.getHeader(MailUtils.X_KOM_LE_VERSION) == null || encryptedMsg.getHeader(MailUtils.X_KOM_LE_VERSION).length == 0) {
                logger.logLine("Header " + MailUtils.X_KOM_LE_VERSION + " not available");
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                encryptedMsg.writeTo(byteArrayOutputStream);
                byte[] result = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG_BYTES, result);
            }
            else {
                logger.logLine("Header " + MailUtils.X_KOM_LE_VERSION + " available");

                //unterstützte versionen nicht verfügbar
                String[] komLeVersionHeader = encryptedMsg.getHeader(MailUtils.X_KOM_LE_VERSION);
                if (!MailUtils.VALID_KIM_VERSIONS.contains(komLeVersionHeader[0])) {
                    logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X014);
                    logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_X014 + " - " + EnumErrorCode.CODE_X014.getHrText());
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());
                    throw new IllegalStateException("kim version is wrong");
                }

                //unterstützte version kleiner als die version der mail
                String komLeVersion = komLeVersionHeader[0];
                if (StringUtils.isNewVersionHigher(logger.getDefaultLoggerContext().getKonfiguration().getXkimPtShortVersion().getInnerVersion(), EnumKomLeVersion.get(komLeVersion).getInnerVersion())) {
                    logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());
                    throw new IllegalStateException("kim version is wrong: < kim version in mail");
                }

                //analyze mail
                log.info("analyze mail encrypt format");
                logger.logLine("analyze mail encrypt format");

                defaultPipelineOperationContext.setEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_MSG, encryptedMsg);
                defaultPipelineOperationContext.setEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_DECRYPT_MODE, true);

                checkEncryptedMailFormatOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("check encrypted mailformat finished");
                    },
                    (context, e) -> {
                        log.error("error on checking encrypted mailformat", e);
                    }
                );

                boolean valid = (boolean) defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_VALID_RESULT);
                if (!valid) {
                    log.error("mail encrypt format is wrong");
                    logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4010);
                    throw new IllegalStateException("mail encrypt format is wrong");
                }

                log.info("analyze mail encrypt format finished");
                logger.logLine("analyze mail encrypt format finished");

                byte[] encryptedPart = (byte[]) defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_PART);
                ContentInfo contentInfo = (ContentInfo) defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_CONTENT_INFO);

                //card handle
                log.info("getDecryptCardHandle");
                logger.logLine("getDecryptCardHandle");

                defaultPipelineOperationContext.setEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_CONTENT_INFO, contentInfo);
                defaultPipelineOperationContext.setEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_USER_MAIL_ADDRESS, userMailAddress);

                getDecryptCardHandleOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("loading of decrypt card handle finished");
                    },
                    (context, e) -> {
                        log.error("error on loading of decrypt card handle", e);
                    }
                );

                boolean cardHandleFound = (boolean) defaultPipelineOperationContext.getEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_RESULT_CARD_HANDLE_FOUND);
                if (!cardHandleFound) {
                    throw new IllegalStateException("card handle not found");
                }
                String decryptCardHandle = (String) defaultPipelineOperationContext.getEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_RESULT_CARD_HANDLE);

                log.info("getDecryptCardHandle finished: " + decryptCardHandle);
                logger.logLine("getDecryptCardHandle finished: " + decryptCardHandle);

                //decrypt
                defaultPipelineOperationContext.setEnvironmentValue(DecryptDocumentOperation.NAME, DecryptDocumentOperation.ENV_DOCUMENT_BYTES, encryptedPart);
                defaultPipelineOperationContext.setEnvironmentValue(DecryptDocumentOperation.NAME, DecryptDocumentOperation.ENV_CARDHANDLE, decryptCardHandle);

                AtomicInteger failedCounter = new AtomicInteger();
                decryptDocumentOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("decrypt mail finished");
                    },
                    (context, e) -> {
                        log.error("error on decrypting of mail", e);
                        failedCounter.incrementAndGet();
                        logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());
                    }
                );

                byte[] decryptedMsgBytes = null;
                byte[] signedContent = null;
                if (failedCounter.get() == 0) {
                    DecryptDocumentResponse decryptDocumentResponse = (DecryptDocumentResponse) defaultPipelineOperationContext.getEnvironmentValue(DecryptDocumentOperation.NAME, DecryptDocumentOperation.ENV_DECRYPT_DOCUMENT_RESPONSE);
                    if (!decryptDocumentResponse.getStatus().getResult().equals("OK")) {
                        throw new IllegalStateException("decrypt response not ok for the konnektor: " + konnektor.getIp() + " - " + decryptDocumentResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + decryptDocumentResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
                    }
                    DocumentType documentType = decryptDocumentResponse.getDocument();
                    if (documentType == null) {
                        throw new IllegalStateException("decrypt response document empty for the konnektor: " + konnektor.getIp());
                    }
                    if (documentType.getBase64Data().getValue() == null) {
                        throw new IllegalStateException("decrypt response document empty for the konnektor: " + konnektor.getIp());
                    }

                    //extract decrypted message and parse signed content
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

                        throw new IllegalStateException("error on extract decrypted message and parse signed content for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                    }
                } else {
                    throw new IllegalStateException("error on extract decrypted message and parse signed content for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                }

                //verify document
                File signReportFile = null;

                defaultPipelineOperationContext.setEnvironmentValue(VerifySignedDocumentOperation.NAME, VerifySignedDocumentOperation.ENV_SIGNED_CONTENT, decryptedMsgBytes);
                defaultPipelineOperationContext.setEnvironmentValue(VerifySignedDocumentOperation.NAME, VerifySignedDocumentOperation.ENV_SIGNED_DATA_AS_BASE64, false);

                AtomicInteger verifyFailedCounter = new AtomicInteger();
                verifySignedDocumentOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("verify mail finished");
                    },
                    (context, e) -> {
                        log.error("error on verifying of mail", e);
                        verifyFailedCounter.incrementAndGet();
                    }
                );

                if (failedCounter.get() == 0) {
                    VerifyDocumentResponse verifyDocumentResponse = (VerifyDocumentResponse) defaultPipelineOperationContext.getEnvironmentValue(VerifySignedDocumentOperation.NAME, VerifySignedDocumentOperation.ENV_VERIFY_DOCUMENT_RESPONSE);

                    if (!verifyDocumentResponse.getStatus().getResult().equals("OK")) {
                        logger.logLine("verify response not ok for the konnektor: " + konnektor.getIp() + " - " + verifyDocumentResponse.getStatus().getError().getTrace().get(0).getErrorText() + " - " + verifyDocumentResponse.getStatus().getError().getTrace().get(0).getDetail().getValue());
                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4115);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4115 + " - " + EnumErrorCode.CODE_4115.getHrText());

                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());

                        throw new IllegalStateException("error on verifying signed message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
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

                            logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                            logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());

                            throw new IllegalStateException("error on verifying signed message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                        }

                        signReportFile = signReportService.execute(logger, verificationReportType);
                        if (signReportFile == null) {
                            logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                            logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());

                            throw new IllegalStateException("error on verifying signed message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                        }
                    } else {
                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4253);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4253 + " - " + EnumErrorCode.CODE_4253.getHrText());

                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4011);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4011 + " - " + EnumErrorCode.CODE_4011.getHrText());

                        throw new IllegalStateException("error on verifying signed message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                    }
                } else {
                    throw new IllegalStateException("error on verifying signed message for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                }

                //extracted decrypted and verified message
                byte[] preHeader = "Content-Type: message/rfc822\r\n\r\n".getBytes(StandardCharsets.UTF_8);
                int preHeaderLength = preHeader.length;
                byte[] signedContentPayload = Arrays.copyOfRange(signedContent, preHeaderLength, signedContent.length);
                MimeMessage decryptedAndVerifiedMessage = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(signedContentPayload));

                MailUtils.checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, MailUtils.FROM);
                MailUtils.checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, MailUtils.SENDER);
                MailUtils.checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, MailUtils.REPLY_TO);
                MailUtils.checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, MailUtils.TO);
                MailUtils.checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, MailUtils.CC);
                MailUtils.checkHeader(logger, konnektor, encryptedMsg, decryptedAndVerifiedMessage, MailUtils.BCC);

                //set header Return-Path, Received
                logger.logLine("set " + MailUtils.RETURN_PATH);
                String[] returnPath = encryptedMsg.getHeader(MailUtils.RETURN_PATH);
                if (returnPath != null && returnPath.length > 0) {
                    decryptedAndVerifiedMessage.setHeader(MailUtils.RETURN_PATH, returnPath[0]);
                }
                logger.logLine("set " + MailUtils.RECEIVED);
                String[] received = encryptedMsg.getHeader(MailUtils.RECEIVED);
                if (received != null && received.length > 0) {
                    for (int i = 0; i < received.length; i++) {
                        decryptedAndVerifiedMessage.addHeader(MailUtils.RECEIVED, received[i]);
                    }
                }
                logger.logLine("set " + MailUtils.REPLY_TO);
                String[] replyTo = encryptedMsg.getHeader(MailUtils.REPLY_TO);
                if (replyTo != null && replyTo.length > 0) {
                    decryptedAndVerifiedMessage.setHeader(MailUtils.REPLY_TO, replyTo[0]);
                }

                //add signature report
                defaultPipelineOperationContext.setEnvironmentValue(AddMailAttachmentOperation.NAME, AddMailAttachmentOperation.ENV_ATTACHMENT, signReportFile);
                defaultPipelineOperationContext.setEnvironmentValue(AddMailAttachmentOperation.NAME, AddMailAttachmentOperation.ENV_CONTENT_TYPE, "application/pdf");
                defaultPipelineOperationContext.setEnvironmentValue(AddMailAttachmentOperation.NAME, AddMailAttachmentOperation.ENV_ATTACHMENT_NAME, "Signaturpruefbericht.pdf");
                defaultPipelineOperationContext.setEnvironmentValue(AddMailAttachmentOperation.NAME, AddMailAttachmentOperation.ENV_MSG, decryptedAndVerifiedMessage);

                AtomicInteger addAtachmentFailedCounter = new AtomicInteger();
                addMailAttachmentOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("add signatur file finished");
                    },
                    (context, e) -> {
                        log.error("error on adding of signatur file", e);
                        addAtachmentFailedCounter.incrementAndGet();
                    }
                );

                if (addAtachmentFailedCounter.get() == 0) {
                    decryptedAndVerifiedMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(AddMailAttachmentOperation.NAME, AddMailAttachmentOperation.ENV_RESULT_MSG);
                } else {
                    throw new IllegalStateException("error on adding signature file for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                }

                //add signature text
                defaultPipelineOperationContext.setEnvironmentValue(AddMailTextOperation.NAME, AddMailTextOperation.ENV_TEXT, "----------------------------------\n!!!Die Signatur wurde erfolgreich geprueft!!!\n----------------------------------");
                defaultPipelineOperationContext.setEnvironmentValue(AddMailTextOperation.NAME, AddMailTextOperation.ENV_MSG, decryptedAndVerifiedMessage);

                AtomicInteger addTextFailedCounter = new AtomicInteger();
                addMailTextOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        log.info("add text finished");
                    },
                    (context, e) -> {
                        log.error("error on adding of text", e);
                        addTextFailedCounter.incrementAndGet();
                    }
                );

                if (addTextFailedCounter.get() == 0) {
                    decryptedAndVerifiedMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(AddMailTextOperation.NAME, AddMailTextOperation.ENV_RESULT_MSG);
                } else {
                    throw new IllegalStateException("error on adding text for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
                }

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                decryptedAndVerifiedMessage.writeTo(byteArrayOutputStream);
                byte[] result = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG_BYTES, result);
            }

            timeMetric.stopAndPublish();
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the DecryptVerifyMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
