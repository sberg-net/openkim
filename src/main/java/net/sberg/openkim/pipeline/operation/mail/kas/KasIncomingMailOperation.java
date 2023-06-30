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
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
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
import javax.crypto.spec.SecretKeySpec;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class KasIncomingMailOperation implements IPipelineOperation {

    private static final Logger log = LoggerFactory.getLogger(KasIncomingMailOperation.class);
    public static final String NAME = "KasIncomingMail";

    public static final String ENV_MSG = "message";
    public static final String ENV_VALID_RESULT = "validResult";
    public static final String ENV_RESULT_MSG = "resultMsg";
    public static final String ENV_POP3_GATEWAY_SESSION = "pop3GatewaySession";

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

        File messageFileOutput = null;
        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            logger.logLine("start executeIncoming message");
            MimeMessage mimeMessage = (MimeMessage)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MSG);
            Pop3GatewaySession pop3GatewaySession = (Pop3GatewaySession) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_POP3_GATEWAY_SESSION);

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
            if (mailPartContent.getMimePartDispositionType().equals(EnumMailPartDispositionType.Xkas)) {
                String content = (String)mailPartContent.getContentPart();
                KasMetaObj kasMetaObj = new ObjectMapper().readValue(content, KasMetaObj.class);

                ResponseEntity<File> apiResult = logger.getDefaultLoggerContext().getFachdienst().getAttachmentsApi().readAttachmentWithHttpInfo(kasMetaObj.getLink(), pop3GatewaySession.getLogger().getDefaultLoggerContext().getMailServerUsername());
                if (apiResult.getStatusCode().equals(HttpStatus.OK)) {
                    logger.logLine("read attachment from kas-service: " + kasMetaObj.getLink() + " ends");
                }
                else if (apiResult.getStatusCode().equals(HttpStatus.FORBIDDEN)) {
                    logger.logLine("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.FORBIDDEN);
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.readAttachmentForbidden,
                        "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink()
                    );
                }
                else if (apiResult.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                    logger.logLine("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.NOT_FOUND);
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.readAttachmentNotFound,
                        "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink()
                    );
                }
                else if (apiResult.getStatusCode().equals(HttpStatus.TOO_MANY_REQUESTS)) {
                    logger.logLine("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.TOO_MANY_REQUESTS);
                }
                else if (apiResult.getStatusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
                    logger.logLine("read attachment from kas-service: " + kasMetaObj.getLink() + " - error " + HttpStatus.INTERNAL_SERVER_ERROR);
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.readAttachmentInternalServerError,
                        "error on reading the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink()
                    );
                }

                //decrypt
                SecretKey secretKey = null;
                try {
                    String tmpDir = System.getProperty("java.io.tmpdir")
                        + File.separator
                        + mimeMessage.getMessageID()
                        + System.currentTimeMillis()
                        + File.separator;
                    new File(tmpDir).mkdirs();

                    messageFileOutput = new File(tmpDir + "message.eml");
                    if (messageFileOutput.exists()) {
                        messageFileOutput.delete();
                    }
                    byte[] decKey = Base64.getDecoder().decode(kasMetaObj.getK().getBytes("UTF-8"));
                    secretKey = new SecretKeySpec(decKey, AesGcmHelper.ENCRYPT_ALGO);
                    AesGcmHelper.decryptWithStreamWithPrefixIV(messageFileOutput, apiResult.getBody(), secretKey);
                    logger.logLine("decrypt attachment from kas-service: " + kasMetaObj.getLink() + " ends");
                } catch (Exception e) {
                    log.error("error on decrypting the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(), e);
                    logger.logLine("decrypt attachment ends " + kasMetaObj.getLink() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.decryptAttachment,
                        "error on decrypting the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(),
                        e
                    );
                }

                //hash plain attachment -> check with origin
                String attachmentEncodedHash = null;
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    attachmentEncodedHash = Base64.getEncoder().encodeToString(FileUtils.getFileChecksum(digest, messageFileOutput));
                    if (!kasMetaObj.getHash().equals(attachmentEncodedHash)) {
                        logger.logLine("check hash attachment ends " + messageFileOutput.getAbsolutePath() + " - error");
                        throw new KasServiceException(
                            EnumKasServiceErrorCode.checkHashPlainAttachment,
                            "error on checking the hash of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + messageFileOutput.getAbsolutePath()
                        );
                    } else {
                        logger.logLine("check hash attachment: " + messageFileOutput.getAbsolutePath() + " ends");
                    }
                } catch (Exception e) {
                    log.error("error on hashing the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + messageFileOutput.getAbsolutePath(), e);
                    logger.logLine("hash attachment ends " + messageFileOutput.getAbsolutePath() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.hashPlainAttachment,
                        "error on hashing the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + messageFileOutput.getAbsolutePath(),
                        e
                    );
                }

                //create mimemessage
                try {
                    InputStream mailFileInputStream = new FileInputStream(messageFileOutput);
                    Properties props = new Properties();
                    Session session = Session.getDefaultInstance(props, null);
                    mimeMessage = new MimeMessage(session, mailFileInputStream);
                } catch (Exception e) {
                    log.error("error on creating original mimebodypart of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(), e);
                    logger.logLine("creating original mimebodypart attachment ends " + kasMetaObj.getLink() + " - error");
                    throw new KasServiceException(
                        EnumKasServiceErrorCode.creatingOriginalMimemessage,
                        "error on creating original mimebodypart of the attachment from the message: " + pop3GatewaySession.getSessionID() + " - " + kasMetaObj.getLink(),
                        e
                    );
                }
            }

            logger.logLine("ends executeIncoming message");
            timeMetric.stopAndPublish();

            if (messageFileOutput != null) {
                messageFileOutput.delete();
            }

            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, true);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG, mimeMessage);
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the KasIncomingMailOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            if (messageFileOutput != null) {
                messageFileOutput.delete();
            }

            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, false);
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}