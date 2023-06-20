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
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.IErrorContext;
import net.sberg.openkim.log.error.MailSignVerifyErrorContext;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class CreateDsnOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(CreateDsnOperation.class);
    public static final String NAME = "CreateDsn";

    public static final String ENV_ERROR_CONTEXT = "errorContext";
    public static final String ENV_ORIGIN_MSG = "originMessage";
    public static final String ENV_DSN_MSG_BYTES = "dsnMessage";

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

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            IErrorContext errorContext = (IErrorContext) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ERROR_CONTEXT);
            MimeMessage originMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ORIGIN_MSG);

            MimeMessage mimeMessage = null;

            if (errorContext instanceof MailSignVerifyErrorContext) {

                List<EnumErrorCode> codes = ((MailSignVerifyErrorContext) errorContext).getErrorCodes();

                StringBuilder contentBuilder = new StringBuilder();
                contentBuilder.append("Es sind Fehler beim Verifizieren der Mail-Signatur aufgetreten.\r\n");
                for (Iterator<EnumErrorCode> iterator = codes.iterator(); iterator.hasNext(); ) {
                    EnumErrorCode code = iterator.next();
                    contentBuilder.append(code.getId()).append(" - ").append(code.getHrText()).append("\r\n");
                }
                mimeMessage = DsnHelper.createMessage(
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
                    mimeMessage.addHeader(MailUtils.X_KIM_INTEGRITY_CHECK_RESULT, code.getId());
                }
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(byteArrayOutputStream);
            byte[] result = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.close();

            timeMetric.stopAndPublish();
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_DSN_MSG_BYTES, result);
            okConsumer.accept(defaultPipelineOperationContext);
        }
        catch (Exception e) {
            log.error("error on executing the CreateDsnOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
