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

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.CMSUtils;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.mail.part.AnalyzeMailPartsOperation;
import net.sberg.openkim.pipeline.operation.mail.part.MailPartContent;
import org.apache.james.metrics.api.TimeMetric;
import org.bouncycastle.asn1.cms.AuthEnvelopedData;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.EnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimePart;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class CheckEncryptedMailFormatOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(CheckEncryptedMailFormatOperation.class);
    public static final String NAME = "CheckEncryptedMailFormat";

    public static final String ENV_ENCRYPTED_MSG = "encryptedMsg";
    public static final String ENV_DECRYPT_MODE = "decryptMode";
    public static final String ENV_VALID_RESULT = "validResult";
    public static final String ENV_ENCRYPTED_CONTENT_INFO = "encryptedContentInfo";
    public static final String ENV_ENCRYPTED_PART = "encryptedPart";

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

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            MimeMessage encryptedMsg = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ENCRYPTED_MSG);
            boolean decryptMode = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_DECRYPT_MODE);
            boolean valid = true;

            //header
            String[] header = encryptedMsg.getHeader(MailUtils.X_KOM_LE_VERSION);
            if (header == null || header.length != 1 || !MailUtils.VALID_KIM_VERSIONS.contains(header[0])) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X014);
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4008);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X014 + " - " + EnumErrorCode.CODE_X014.getHrText());
                logger.logLine("Fehler: " + EnumErrorCode.CODE_4008 + " - " + EnumErrorCode.CODE_4008.getHrText());
                valid = false;
            }

            //subject
            if (valid && (encryptedMsg.getSubject() == null || !encryptedMsg.getSubject().equals(MailUtils.SUBJECT_KOM_LE_NACHRICHT))) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X015);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X015 + " - " + EnumErrorCode.CODE_X015.getHrText());
                valid = false;
            }

            if (valid) {
                AtomicInteger errorCounter = new AtomicInteger();
                analyzeMailPartsOperation.execute(
                        defaultPipelineOperationContext,
                        context -> {
                            try {
                                MailPartContent encryptMailPartContent = (MailPartContent) defaultPipelineOperationContext.getEnvironmentValue(AnalyzeMailPartsOperation.NAME, AnalyzeMailPartsOperation.ENV_RESULT);
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
                                    errorCounter.incrementAndGet();
                                }
                            } catch (Exception e) {
                                errorCounter.incrementAndGet();
                            }
                        },
                        (context, e) -> {
                            errorCounter.incrementAndGet();
                        }
                );
                if (errorCounter.get() > 0) {
                    valid = false;
                }
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

            //1.2.840.113549.1.9.16.1.23
            if (valid && encryptedContentInfo.getContentType().getId().equals(CMSUtils.ENVELOPED_DATA_OID)
                    && !encryptedContentInfo.getContentType().getId().equals(CMSUtils.AUTH_ENVELOPED_DATA_OID)
            ) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X017);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X017 + " - " + EnumErrorCode.CODE_X017.getHrText());
                valid = false;
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
                if (valid && !CMSUtils.encryptedRecipientInfosAvailable(cmsEnvelopedData)) {
                    logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X018);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_X018 + " - " + EnumErrorCode.CODE_X018.getHrText());
                    valid = false;
                }
            } catch (Exception e) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X018);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X018 + " - " + EnumErrorCode.CODE_X018.getHrText());
                valid = false;
            }

            //encryptedRecipientEmailsAvailable
            try {
                if (valid && !CMSUtils.encryptedRecipientEmailsAvailable(encryptedContentInfo)) {
                    logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X019);
                    logger.logLine("Fehler: " + EnumErrorCode.CODE_X019 + " - " + EnumErrorCode.CODE_X019.getHrText());
                    valid = false;
                }
            } catch (Exception e) {
                logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X019);
                logger.logLine("Fehler: " + EnumErrorCode.CODE_X019 + " - " + EnumErrorCode.CODE_X019.getHrText());
                valid = false;
            }

            timeMetric.stopAndPublish();
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, valid);

            if (valid && decryptMode) {
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_ENCRYPTED_CONTENT_INFO, encryptedContentInfo);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_ENCRYPTED_PART, encryptedPart);
            }

            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the CheckEncryptedMailFormatOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }

            logger.getDefaultLoggerContext().getMailEncryptFormatErrorContext().getErrorCodes().add(EnumErrorCode.CODE_X020);
            logger.logLine("Fehler: " + EnumErrorCode.CODE_X020 + " - " + EnumErrorCode.CODE_X020.getHrText());

            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VALID_RESULT, false);

            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
