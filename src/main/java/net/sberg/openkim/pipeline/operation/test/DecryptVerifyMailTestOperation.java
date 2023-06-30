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
package net.sberg.openkim.pipeline.operation.test;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.mail.DecryptVerifyMailOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class DecryptVerifyMailTestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(DecryptVerifyMailTestOperation.class);
    public static final String NAME = "DecryptVerifyMailTest";

    public static final String ENV_ENCRYPTED_MSG = "encryptedMsg";
    public static final String ENV_USER_MAIL_ADDRESS = "userMailAddress";

    @Autowired
    private DecryptVerifyMailOperation decryptVerifyMailOperation;

    @Override
    public boolean isTestable() {
        return true;
    }

    @Override
    public String getHrText() {
        return "Entschlüsseln und Signaturüberprüfung einer Mail über einen Konnektor Ihrer Wahl";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            context.getLogger().logLine("Die Mail wurde erfolgreich entschlüsselt und die Verifizierung der Signatur war ebenfalls erfolgreich");
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            String encryptedMsgStr = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ENCRYPTED_MSG);
            String userMailAddress = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_USER_MAIL_ADDRESS);

            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(encryptedMsgStr.getBytes()));

            defaultPipelineOperationContext.setEnvironmentValue(DecryptVerifyMailOperation.NAME, DecryptVerifyMailOperation.ENV_ENCRYPTED_MSG, mimeMessage);
            defaultPipelineOperationContext.setEnvironmentValue(DecryptVerifyMailOperation.NAME, DecryptVerifyMailOperation.ENV_USER_MAIL_ADDRESS, userMailAddress);

            AtomicInteger failedCounter = new AtomicInteger();
            decryptVerifyMailOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("decrypt and verify mail finished");
                },
                (context, e) -> {
                    log.error("error on decrypting and verifying mail", e);
                    failedCounter.incrementAndGet();
                }
            );

            if (failedCounter.get() == 0) {
                byte[] decryptVerifiedMail = (byte[])defaultPipelineOperationContext.getEnvironmentValue(DecryptVerifyMailOperation.NAME, DecryptVerifyMailOperation.ENV_RESULT_MSG_BYTES);
                logger.logLine(new String(decryptVerifiedMail), true);
            }
            else {
                throw new IllegalStateException("error on decrypting and verifying mail");
            }

            timeMetric.stopAndPublish();

            okConsumer.accept(defaultPipelineOperationContext);
        } catch (Exception e) {
            log.error("error on executing the DecryptVerifyMailTestOperation", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
