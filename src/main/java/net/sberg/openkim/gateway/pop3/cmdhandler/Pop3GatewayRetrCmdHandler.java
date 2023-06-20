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
package net.sberg.openkim.gateway.pop3.cmdhandler;

import com.google.common.collect.ImmutableSet;
import de.gematik.ws.conn.connectorcommon.DocumentType;
import de.gematik.ws.conn.encryptionservice.v6_1_1.DecryptDocumentResponse;
import de.gematik.ws.conn.signatureservice.v7_5_5.VerifyDocumentResponse;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.gateway.pop3.EnumPop3GatewayState;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.gateway.pop3.signreport.SignReportService;
import net.sberg.openkim.konfiguration.EnumGatewayTIMode;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.konnektor.GetDecryptCardHandleOperation;
import net.sberg.openkim.pipeline.operation.konnektor.KonnektorLoadAllCardInformationOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.DecryptDocumentOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.ReadCardCertificateOperation;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.VerifySignedDocumentOperation;
import net.sberg.openkim.pipeline.operation.mail.CheckEncryptedMailFormatOperation;
import net.sberg.openkim.pipeline.operation.mail.CreateDsnOperation;
import net.sberg.openkim.pipeline.operation.mail.CreateEmbeddedMessageRfc822Operation;
import net.sberg.openkim.pipeline.operation.mail.MailUtils;
import net.sberg.openkim.pipeline.operation.mail.part.AddMailAttachmentOperation;
import net.sberg.openkim.pipeline.operation.mail.part.AddMailTextOperation;
import net.sberg.openkim.pipeline.operation.mail.part.AnalyzeMailPartsOperation;
import oasis.names.tc.dss_x._1_0.profiles.verificationreport.schema_.VerificationReportType;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.POP3StreamResponse;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.apache.james.protocols.pop3.core.CRLFTerminatedInputStream;
import org.apache.james.protocols.pop3.core.ExtraDotInputStream;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class Pop3GatewayRetrCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("RETR");

    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayRetrCmdHandler.class);

    private PipelineService pipelineService;
    private SignReportService signReportService;

    public Pop3GatewayRetrCmdHandler(PipelineService pipelineService, SignReportService signReportService) {
        this.pipelineService = pipelineService;
        this.signReportService = signReportService;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-retr", () -> doRetr(session, request));
    }

    private byte[] decryptVerify(
        DefaultLogger logger,
        String userMailAddress,
        MimeMessage encryptedMsg
    ) throws Exception {
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {

            //Header X-KOM-LE-Version available
            if (encryptedMsg.getHeader(MailUtils.X_KOM_LE_VERSION) == null || encryptedMsg.getHeader(MailUtils.X_KOM_LE_VERSION).length == 0) {
                logger.logLine("Header " + MailUtils.X_KOM_LE_VERSION + " not available");
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                encryptedMsg.writeTo(byteArrayOutputStream);
                byte[] result = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.close();
                return result;
            }
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
            if (StringUtils.isNewVersionHigher(logger.getDefaultLoggerContext().getKonfiguration().getXkimPtShortVersion(), komLeVersion)) {
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());
                throw new IllegalStateException("kim version is wrong: < kim version in mail");
            }

            //analyze mail
            log.info("analyze mail encrypt format");
            logger.logLine("analyze mail encrypt format");

            CheckEncryptedMailFormatOperation checkEncryptedMailFormatOperation = (CheckEncryptedMailFormatOperation) pipelineService.getOperation(CheckEncryptedMailFormatOperation.BUILTIN_VENDOR+"."+CheckEncryptedMailFormatOperation.NAME);
            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);

            defaultPipelineOperationContext.setEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_MSG, encryptedMsg);
            defaultPipelineOperationContext.setEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_DECRYPT_MODE, true);

            AnalyzeMailPartsOperation analyzeMailPartsOperation = (AnalyzeMailPartsOperation) pipelineService.getOperation(AnalyzeMailPartsOperation.BUILTIN_VENDOR+"."+AnalyzeMailPartsOperation.NAME);
            checkEncryptedMailFormatOperation.setAnalyzeMailPartsOperation(analyzeMailPartsOperation);

            checkEncryptedMailFormatOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("check encrypted mailformat finished");
                },
                (context, e) -> {
                    log.error("error on checking encrypted mailformat", e);
                }
            );

            boolean valid = (boolean)defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_VALID_RESULT);
            if (!valid) {
                log.error("mail encrypt format is wrong");
                logger.getDefaultLoggerContext().getMailDecryptErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4010);
                throw new IllegalStateException("mail encrypt format is wrong");
            }

            log.info("analyze mail encrypt format finished");
            logger.logLine("analyze mail encrypt format finished");

            byte[] encryptedPart = (byte[])defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_PART);
            ContentInfo contentInfo = (ContentInfo)defaultPipelineOperationContext.getEnvironmentValue(CheckEncryptedMailFormatOperation.NAME, CheckEncryptedMailFormatOperation.ENV_ENCRYPTED_CONTENT_INFO);

            //card handle
            log.info("getDecryptCardHandle");
            logger.logLine("getDecryptCardHandle");

            GetDecryptCardHandleOperation getDecryptCardHandleOperation = (GetDecryptCardHandleOperation) pipelineService.getOperation(GetDecryptCardHandleOperation.BUILTIN_VENDOR+"."+GetDecryptCardHandleOperation.NAME);

            defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_CONTENT_INFO, contentInfo);
            defaultPipelineOperationContext.setEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_USER_MAIL_ADDRESS, userMailAddress);

            KonnektorLoadAllCardInformationOperation konnektorLoadAllCardInformationOperation = (KonnektorLoadAllCardInformationOperation) pipelineService.getOperation(KonnektorLoadAllCardInformationOperation.BUILTIN_VENDOR+"."+KonnektorLoadAllCardInformationOperation.NAME);
            getDecryptCardHandleOperation.setKonnektorLoadAllCardInformationOperation(konnektorLoadAllCardInformationOperation);

            ReadCardCertificateOperation readCardCertificateOperation = (ReadCardCertificateOperation) pipelineService.getOperation(ReadCardCertificateOperation.BUILTIN_VENDOR+"."+ReadCardCertificateOperation.NAME);
            getDecryptCardHandleOperation.setReadCardCertificateOperation(readCardCertificateOperation);

            getDecryptCardHandleOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("loading of decrypt card handle finished");
                },
                (context, e) -> {
                    log.error("error on loading of decrypt card handle", e);
                }
            );

            boolean cardHandleFound = (boolean)defaultPipelineOperationContext.getEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_RESULT_CARD_HANDLE_FOUND);
            if (!cardHandleFound) {
                throw new IllegalStateException("card handle not found");
            }
            String decryptCardHandle = (String)defaultPipelineOperationContext.getEnvironmentValue(GetDecryptCardHandleOperation.NAME, GetDecryptCardHandleOperation.ENV_RESULT_CARD_HANDLE);

            log.info("getDecryptCardHandle finished: " + decryptCardHandle);
            logger.logLine("getDecryptCardHandle finished: " + decryptCardHandle);

            //decrypt
            DecryptDocumentOperation decryptDocumentOperation = (DecryptDocumentOperation) pipelineService.getOperation(DecryptDocumentOperation.BUILTIN_VENDOR+"."+DecryptDocumentOperation.NAME);

            defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(DecryptDocumentOperation.NAME, DecryptDocumentOperation.ENV_DOCUMENT, encryptedPart);
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
            }
            else {
                throw new IllegalStateException("error on extract decrypted message and parse signed content for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
            }

            //verify document
            File signReportFile = null;

            VerifySignedDocumentOperation verifySignedDocumentOperation = (VerifySignedDocumentOperation) pipelineService.getOperation(VerifySignedDocumentOperation.BUILTIN_VENDOR+"."+VerifySignedDocumentOperation.NAME);

            defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
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
                VerifyDocumentResponse verifyDocumentResponse = (VerifyDocumentResponse)defaultPipelineOperationContext.getEnvironmentValue(VerifySignedDocumentOperation.NAME, VerifySignedDocumentOperation.ENV_VERIFY_DOCUMENT_RESPONSE);

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
            }
            else {
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
            AddMailAttachmentOperation addMailAttachmentOperation = (AddMailAttachmentOperation) pipelineService.getOperation(AddMailAttachmentOperation.BUILTIN_VENDOR+"."+AddMailAttachmentOperation.NAME);

            defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
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
                decryptedAndVerifiedMessage = (MimeMessage)defaultPipelineOperationContext.getEnvironmentValue(AddMailAttachmentOperation.NAME, AddMailAttachmentOperation.ENV_RESULT_MSG);
            }
            else {
                throw new IllegalStateException("error on adding signature file for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
            }

            //add signature text
            AddMailTextOperation addMailTextOperation = (AddMailTextOperation) pipelineService.getOperation(AddMailTextOperation.BUILTIN_VENDOR+"."+AddMailTextOperation.NAME);

            defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
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
                decryptedAndVerifiedMessage = (MimeMessage)defaultPipelineOperationContext.getEnvironmentValue(AddMailTextOperation.NAME, AddMailTextOperation.ENV_RESULT_MSG);
            }
            else {
                throw new IllegalStateException("error on adding text for konnektor: " + konnektor.getIp() + " and the user mail address: " + userMailAddress);
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            decryptedAndVerifiedMessage.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();

            return result;
        } catch (Exception e) {
            log.error("error on mail decrypting and verifying for the konnektor: " + konnektor.getIp(), e);
            throw e;
        }
    }

    private Response doRetr(POP3Session session, Request request) {

        ((Pop3GatewaySession) session).log("retr begins");
        DefaultLogger logger = ((Pop3GatewaySession) session).getLogger();

        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                MimeMessage message = (MimeMessage) ((Pop3GatewaySession) session).getPop3ClientFolder().getMessage(Integer.parseInt(request.getArgument()));
                //message = kasService.executeIncoming(((Pop3GatewaySession)session).getLogger(), message, (Pop3GatewaySession)session);
                ((Pop3GatewaySession) session).setGatewayState(EnumPop3GatewayState.PROCESS);

                byte[] pop3msg = null;
                if (logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.NO_TI)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    message.writeTo(baos);
                    pop3msg = baos.toByteArray();
                    baos.reset();
                    baos.close();
                }
                else {
                    pop3msg = decryptVerify(((Pop3GatewaySession) session).getLogger(), ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername(), message);
                    if (!logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().isEmpty()) {

                        CreateDsnOperation createDsnOperation = (CreateDsnOperation) pipelineService.getOperation(CreateDsnOperation.BUILTIN_VENDOR + "." + CreateDsnOperation.NAME);

                        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
                        defaultPipelineOperationContext.setEnvironmentValue(CreateDsnOperation.NAME, CreateDsnOperation.ENV_ORIGIN_MSG, message);
                        defaultPipelineOperationContext.setEnvironmentValue(CreateDsnOperation.NAME, CreateDsnOperation.ENV_ERROR_CONTEXT, logger.getDefaultLoggerContext().getMailSignVerifyErrorContext());

                        AtomicInteger failedCounter = new AtomicInteger();
                        createDsnOperation.execute(
                                defaultPipelineOperationContext,
                                context -> {
                                    log.info("create dsn finished");
                                },
                                (context, e) -> {
                                    log.error("error on creating of dsn", e);
                                    failedCounter.incrementAndGet();
                                }
                        );

                        if (failedCounter.get() == 0) {
                            pop3msg = (byte[]) defaultPipelineOperationContext.getEnvironmentValue(CreateDsnOperation.NAME, CreateDsnOperation.ENV_DSN_MSG_BYTES);
                        } else {
                            throw new IllegalStateException("error on creating dsn mail");
                        }
                    } else if (!logger.getDefaultLoggerContext().getMailDecryptErrorContext().isEmpty()) {
                        CreateEmbeddedMessageRfc822Operation createEmbeddedMessageRfc822Operation = (CreateEmbeddedMessageRfc822Operation) pipelineService.getOperation(CreateEmbeddedMessageRfc822Operation.BUILTIN_VENDOR + "." + CreateEmbeddedMessageRfc822Operation.NAME);

                        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
                        defaultPipelineOperationContext.setEnvironmentValue(CreateEmbeddedMessageRfc822Operation.NAME, CreateEmbeddedMessageRfc822Operation.ENV_ORIGIN_MSG, message);
                        defaultPipelineOperationContext.setEnvironmentValue(CreateEmbeddedMessageRfc822Operation.NAME, CreateEmbeddedMessageRfc822Operation.ENV_ERROR_CONTEXT, logger.getDefaultLoggerContext().getMailDecryptErrorContext());

                        AtomicInteger failedCounter = new AtomicInteger();
                        createEmbeddedMessageRfc822Operation.execute(
                                defaultPipelineOperationContext,
                                context -> {
                                    log.info("add embedded message finished");
                                },
                                (context, e) -> {
                                    log.error("error on embedding message", e);
                                    failedCounter.incrementAndGet();
                                }
                        );

                        if (failedCounter.get() == 0) {
                            pop3msg = (byte[]) defaultPipelineOperationContext.getEnvironmentValue(CreateEmbeddedMessageRfc822Operation.NAME, CreateEmbeddedMessageRfc822Operation.ENV_RESULT_MSG_BYTES);
                        } else {
                            throw new IllegalStateException("error on embedding message");
                        }
                    }
                }

                InputStream in = new CRLFTerminatedInputStream(new ExtraDotInputStream(new ByteArrayInputStream(pop3msg)));
                POP3StreamResponse response = new POP3StreamResponse(POP3Response.OK_RESPONSE, "Message follows", in);
                ((Pop3GatewaySession) session).setGatewayState(EnumPop3GatewayState.PROXY);
                ((Pop3GatewaySession) session).log("retr ends");
                return response;
            } catch (Exception e) {
                log.error("error on process retr command", e);
                ((Pop3GatewaySession) session).log("retr ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("retr ends - error");
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
