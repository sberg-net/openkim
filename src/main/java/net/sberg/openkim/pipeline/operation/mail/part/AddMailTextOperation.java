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
package net.sberg.openkim.pipeline.operation.mail.part;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class AddMailTextOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(AddMailTextOperation.class);
    public static final String NAME = "AddMailText";

    public static final String ENV_MSG = "message";
    public static final String ENV_TEXT = "text";
    public static final String ENV_RESULT_MSG = "resultMsg";

    private AnalyzeMailPartsOperation analyzeMailPartsOperation;

    public void setAnalyzeMailPartsOperation(AnalyzeMailPartsOperation analyzeMailPartsOperation) {
        this.analyzeMailPartsOperation = analyzeMailPartsOperation;
    }

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

            MimeMessage mimeMessage = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MSG);
            String text = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_TEXT);

            analyzeMailPartsOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    try {
                        MailPartContent result = (MailPartContent) defaultPipelineOperationContext.getEnvironmentValue(AnalyzeMailPartsOperation.NAME, AnalyzeMailPartsOperation.ENV_RESULT);
                        List<MimeBodyPart> parts = result.getBodyParts("text/*");
                        if (parts.isEmpty() && mimeMessage.getContent() instanceof String) {
                            String content = (String) mimeMessage.getContent();
                            content = content + "\n" + text;
                            mimeMessage.setContent(content, mimeMessage.getContentType());
                        }
                        else if (!parts.isEmpty()) {
                            for (Iterator<MimeBodyPart> iterator = parts.iterator(); iterator.hasNext(); ) {
                                MimeBodyPart bodyPart = iterator.next();
                                addText(bodyPart, text);
                            }
                        }
                        mimeMessage.saveChanges();
                        defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_RESULT_MSG, mimeMessage);
                    }
                    catch (Exception e) {
                        defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_EXCEPTION, e);
                    }
                },
                (context, e) -> {
                    defaultPipelineOperationContext.setEnvironmentValue(AnalyzeMailPartsOperation.NAME, ENV_EXCEPTION, e);
                }
            );

            timeMetric.stopAndPublish();

            if (hasError(defaultPipelineOperationContext, new String[] {NAME,analyzeMailPartsOperation.getName()})) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("failed state"));
            }
            else {
                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the AddMailTextOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }

    private MimeBodyPart addText(MimeBodyPart mimeBodyPart, String text) throws Exception {
        if (mimeBodyPart.isMimeType("text/html")) {
            String html = text.replaceAll("\n", "<br>");
            String content = (String) mimeBodyPart.getContent();
            int idx = content.toLowerCase().lastIndexOf("</body>");
            if (idx != -1) {
                String b = content.substring(0, idx);
                String e = content.substring(idx + "</body>".length());
                content = b + html + "</body>" + e;
            }
            mimeBodyPart.setContent(content, mimeBodyPart.getContentType());
        } else {
            String content = (String) mimeBodyPart.getContent();
            content = content + "\n" + text;
            mimeBodyPart.setContent(content, mimeBodyPart.getContentType());
        }
        return mimeBodyPart;
    }
}
