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
package net.sberg.openkim.pipeline.operation.mail.kas;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.MailaddressCertErrorContext;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import net.sberg.openkim.log.error.MailaddressRcptToErrorContext;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.EnumKomLeVersion;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.VzdResult;
import net.sberg.openkim.pipeline.operation.mail.MailUtils;
import net.sberg.openkim.pipeline.operation.mail.part.AnalyzeMailPartsOperation;
import net.sberg.openkim.pipeline.operation.mail.part.EnumMailPartDispositionType;
import net.sberg.openkim.pipeline.operation.mail.part.MailPartContent;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.mail.Address;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class KasOutgoingMailOperation implements IPipelineOperation {

    private static final Logger log = LoggerFactory.getLogger(KasOutgoingMailOperation.class);
    public static final String NAME = "KasOutgoingMail";

    public static final String ENV_MSG = "message";
    public static final String ENV_SMTP_GATEWAY_SESSION = "smtpGatewaySession";
    public static final String ENV_VALID_RESULT = "validResult";
    public static final String ENV_RESULT_MSG = "resultMsg";

    @Autowired
    private AnalyzeMailPartsOperation analyzeMailPartsOperation;

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
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

        File messageFileInput = null;
        File messageFileOutput = null;

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            logger.logLine("start executeOutgoing message");
            MimeMessage mimeMessage = (MimeMessage)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MSG);
            SmtpGatewaySession smtpGatewaySession = (SmtpGatewaySession) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SMTP_GATEWAY_SESSION);

            AtomicInteger errorCounter = new AtomicInteger();
            defaultPipelineOperationContext.setEnvironmentValue(AnalyzeMailPartsOperation.NAME, AnalyzeMailPartsOperation.ENV_MSG, mimeMessage);
            analyzeMailPartsOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    logger.logLine("analyzing finished");
                },
                (context, e) -> {
                    log.error("error on analyzing finished", e);
                    errorCounter.incrementAndGet();
                }
            );

            if (errorCounter.get() > 0) {
                throw new IllegalStateException("error on analyzing mail");
            }
            MailPartContent mailPartContent = (MailPartContent) defaultPipelineOperationContext.getEnvironmentValue(AnalyzeMailPartsOperation.NAME, AnalyzeMailPartsOperation.ENV_RESULT);

            double totalSize = mailPartContent.sumTotalSize();
            logger.logLine("total mailsize " + totalSize + " in Bytes");

            if (totalSize > konfiguration.getMailSizeLimitInMB() * 1024 * 1024) {
                logger.logLine("kas used, mailsize > 15mb");

                //check rcpts kim version >= 1.5+
                boolean valid = false;
                MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = smtpGatewaySession.getLogger().getDefaultLoggerContext().getMailaddressKimVersionErrorContext();
                MailaddressRcptToErrorContext mailaddressRcptToErrorContext = smtpGatewaySession.getLogger().getDefaultLoggerContext().getMailaddressRcptToErrorContext();
                MailaddressCertErrorContext mailaddressCertErrorContext = smtpGatewaySession.getLogger().getDefaultLoggerContext().getMailaddressCertErrorContext();

                for (Iterator<String> iterator = logger.getDefaultLoggerContext().getRecipientCerts().keySet().iterator(); iterator.hasNext(); ) {
                    String rcptAddress = iterator.next();

                    if (mailaddressKimVersionErrorContext.isError(rcptAddress)
                        ||
                        mailaddressRcptToErrorContext.isError(rcptAddress)
                        ||
                        mailaddressCertErrorContext.isError(rcptAddress)
                    ) {
                        continue;
                    }


                    X509CertificateResult rcptX509CertificateResult = logger.getDefaultLoggerContext().getRecipientCerts().get(rcptAddress);
                    logger.logLine("check komle-version for rcpt: " + rcptX509CertificateResult.getMailAddress());
                    if (rcptX509CertificateResult.getVzdResults().isEmpty()) {
                        throw new IllegalStateException("no vzd results found for: "+rcptX509CertificateResult.getMailAddress());
                    }
                    if (rcptX509CertificateResult.getVzdResults().size() > 1) {
                        throw new IllegalStateException("more than on vzd result found for: "+rcptX509CertificateResult.getMailAddress());
                    }
                    VzdResult vzdResult = rcptX509CertificateResult.getVzdResults().get(0);
                    String cmVersion = vzdResult.getMailResults().get(rcptX509CertificateResult.getMailAddress()).getVersion().getInnerVersion();
                    if (StringUtils.isNewVersionHigher(cmVersion, EnumKomLeVersion.V1_5plus.getInnerVersion())) {
                        mailaddressKimVersionErrorContext.add(rcptX509CertificateResult, EnumErrorCode.CODE_4001, false);
                        logger.logLine("false check komle-version ending for rcpt: " + rcptX509CertificateResult.getMailAddress() + " - " + EnumKomLeVersion.V1_5plus.getInnerVersion() + " - " + cmVersion);
                    }
                    else {
                        valid = true;
                    }
                    logger.logLine("check komle-version ending for rcpt: " + rcptX509CertificateResult.getMailAddress());
                }

                if (logger.getDefaultLoggerContext().extractNoFailureKimVersionRcpts().isEmpty()) {
                    logger.logLine("all rcpts have no 1.5+ or greater");
                    throw new IllegalStateException("all rcpts have no 1.5+ or greater");
                }

                if (!valid) {
                    logger.logLine("handling of all rcpts have problems");
                    throw new IllegalStateException("handling of all rcpts have problems");
                }

                //check attachments -> content-type text/plain; charset=utf-8 and content-disposition: x-kas
                List<MailPartContent> xKasContent = new ArrayList<>();
                mailPartContent.collectAllXKasParts(xKasContent);
                if (!xKasContent.isEmpty()) {
                    logger.logLine("mail contains x-kas attachments");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.attachmentIncorrectTypeAndDisposition,
                        "mail contains x-kas attachments: " + smtpGatewaySession.getSessionID()
                    );
                }

                //encrypt mail and send to kas
                Address[] recipientsArr = mimeMessage.getAllRecipients();
                List<String> recipients = new ArrayList<>();
                for (int i = 0; i < recipientsArr.length; i++) {
                    recipients.add(recipientsArr[i].toString());
                }

                ZonedDateTime date = ZonedDateTime.now().plusDays(logger.getDefaultLoggerContext().getAccountLimit().getDataTimeToLive());
                String expires = DateTimeFormatter.RFC_1123_DATE_TIME.format(date);

                String tmpDir = System.getProperty("java.io.tmpdir")
                    + File.separator
                    + mimeMessage.getMessageID()
                    + System.currentTimeMillis()
                    + File.separator;

                try {
                    messageFileInput = MailUtils.writeToFileDirectory(mimeMessage, "openkim", mimeMessage.getMessageID(), tmpDir);
                    logger.logLine("read binaries: " + messageFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on reading the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(), e);
                    logger.logLine("read attachment ends " + messageFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.readBinariesFromMail,
                        "error on reading the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(),
                        e
                    );
                }

                //encrypt
                SecretKey secretKey = null;
                try {
                    secretKey = AesGcmHelper.getAESKey(AesGcmHelper.AES_KEY_BIT);
                    byte[] iv = AesGcmHelper.getRandomNonce(AesGcmHelper.IV_LENGTH_BYTE);

                    messageFileOutput = new File(tmpDir + "output_" + messageFileInput.getName());
                    if (messageFileOutput.exists()) {
                        messageFileOutput.delete();
                    }

                    AesGcmHelper.encryptWithStream(messageFileOutput, messageFileInput, secretKey, iv, true);
                    logger.logLine("encrypt attachment: " + messageFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on encrypting the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(), e);
                    logger.logLine("encrypt attachment ends " + messageFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.encryptAttachment,
                        "error on encrypting the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(),
                        e
                    );
                }

                //add attachment
                ResponseEntity<de.gematik.kim.kas.model.AddAttachment201Response> apiResult = null;
                try {
                    apiResult = logger.getDefaultLoggerContext().getFachdienst().getAttachmentsApi().addAttachmentWithHttpInfo(mimeMessage.getMessageID(), recipients, expires, messageFileInput);
                    if (apiResult.getStatusCode().equals(HttpStatus.CREATED)) {
                        logger.logLine("send attachment to kas-service: " + messageFileInput.getAbsolutePath() + " ends");
                    } else if (apiResult.getStatusCode().equals(HttpStatus.BAD_REQUEST)) {
                        logger.logLine("send attachment to kas-service: " + messageFileInput.getAbsolutePath() + " - error " + HttpStatus.BAD_REQUEST);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentBadRequest,
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
                        logger.logLine("send attachment to kas-service: " + messageFileInput.getAbsolutePath() + " - error " + HttpStatus.UNAUTHORIZED);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentUnauthorized,
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.PAYLOAD_TOO_LARGE)) {
                        logger.logLine("send attachment to kas-service: " + messageFileInput.getAbsolutePath() + " - error " + HttpStatus.PAYLOAD_TOO_LARGE);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentPayloadTooLarge,
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
                        logger.logLine("send attachment to kas-service: " + messageFileInput.getAbsolutePath() + " - error " + HttpStatus.INTERNAL_SERVER_ERROR);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentInternalServerError,
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath()
                        );
                    } else if (apiResult.getStatusCode().equals(HttpStatus.INSUFFICIENT_STORAGE)) {
                        logger.logLine("send attachment to kas-service: " + messageFileInput.getAbsolutePath() + " - error " + HttpStatus.INSUFFICIENT_STORAGE);
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.sendAttachmentInsufficientStorage,
                            "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath()
                        );
                    }
                }
                catch (KasServiceException e) {
                    throw e;
                }
                catch (Exception e) {
                    log.error("error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(), e);
                    logger.logLine("send attachment ends " + messageFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.sendAttachment,
                        "error on sending the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(),
                        e
                    );
                }

                //hash plain attachment
                String attachmentEncodedHash = null;
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    attachmentEncodedHash = Base64.getEncoder().encodeToString(FileUtils.getFileChecksum(digest, messageFileInput));
                    logger.logLine("hash attachment: " + messageFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on hashing the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(), e);
                    logger.logLine("hash attachment ends " + messageFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.hashPlainAttachment,
                        "error on hashing the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(),
                        e
                    );
                }

                //create x-kas mimebodypart
                try {
                    de.gematik.kim.kas.model.AddAttachment201Response body = apiResult.getBody();
                    KasMetaObj kasMetaObj = new KasMetaObj();
                    kasMetaObj.setHash(attachmentEncodedHash);
                    kasMetaObj.setK(Base64.getEncoder().encodeToString(secretKey.getEncoded()));
                    kasMetaObj.setLink(body.getSharedLink());
                    kasMetaObj.setSize(totalSize);

                    mimeMessage.setText(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(kasMetaObj), "utf-8");
                    mimeMessage.setDisposition(EnumMailPartDispositionType.Xkas.getName());
                    mimeMessage.addHeader(MailUtils.X_KIM_KAS_SIZE, String.valueOf(BigDecimal.valueOf(totalSize).intValue()));
                    mimeMessage.saveChanges();

                    logger.logLine("creating x-kas mimebodypart x-kas mimebodypart: " + messageFileInput.getAbsolutePath() + " ends");
                } catch (Exception e) {
                    log.error("error on creating x-kas mimebodypart of the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(), e);
                    logger.logLine("creating x-kas mimebodypart attachment ends " + messageFileInput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.creatingXkasMimebodypart,
                        "error on creating x-kas mimebodypart of the attachment from the message: " + smtpGatewaySession.getSessionID() + " - " + messageFileInput.getAbsolutePath(),
                        e
                    );
                }
            }

            logger.logLine("ends executeOutgoing message");
            timeMetric.stopAndPublish();

            if (messageFileInput != null) {
                messageFileInput.delete();
            }
            if (messageFileOutput != null) {
                messageFileOutput.delete();
            }

            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, true);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG, mimeMessage);
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the KasOutgoingMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            if (messageFileInput != null) {
                messageFileInput.delete();
            }
            if (messageFileOutput != null) {
                messageFileOutput.delete();
            }

            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, false);
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}