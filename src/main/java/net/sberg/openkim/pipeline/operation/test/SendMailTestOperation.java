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

import net.sberg.openkim.common.EnumMailAuthMethod;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.mail.MailUtils;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class SendMailTestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(SendMailTestOperation.class);
    public static final String NAME = "SendMailTest";

    public static final String ENV_FROM = "from";
    public static final String ENV_TO = "to";
    public static final String ENV_CC = "cc";
    public static final String ENV_BCC = "bcc";
    public static final String ENV_SUBJECT = "subject";
    public static final String ENV_BODY = "body";
    public static final String ENV_USER_NAME = "userName";
    public static final String ENV_PWD = "pwd";
    public static final String ENV_ADDRESS_MAPPING = "addressMapping";

    @Override
    public boolean isTestable() {
        return true;
    }

    @Override
    public String getHrText() {
        return "Senden einer Mail über das OpenKIM-SMTP-Gateway";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            context.getLogger().logLine("Die Mail wurde erfolgreich versendet");
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            Properties props = new Properties();
            props = MailUtils.fillSmtpMailProps(
                props,
                konfiguration.getSmtpGatewayConnectionSec(),
                EnumMailAuthMethod.NORMALPWD,
                konfiguration.getGatewayHost(),
                konfiguration.getSmtpGatewayPort(),
                konfiguration.getSmtpGatewayIdleTimeoutInSeconds() * 1000
            );
            Session session = Session.getInstance(props);

            MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setText((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_BODY));
            mimeMessage.setSubject((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SUBJECT));
            mimeMessage.setFrom((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_FROM));

            String to = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_TO);
            if (to != null && !to.isEmpty()) {
                mimeMessage.setRecipients(Message.RecipientType.TO, to);
            }
            String cc = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CC);
            if (cc != null && !cc.isEmpty()) {
                mimeMessage.setRecipients(Message.RecipientType.CC, to);
            }
            String bcc = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_BCC);
            if (bcc != null && !bcc.isEmpty()) {
                mimeMessage.setRecipients(Message.RecipientType.BCC, to);
            }

            String addressMapping = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ADDRESS_MAPPING);
            if (addressMapping != null && !addressMapping.isEmpty()) {
                mimeMessage.addHeader(MailUtils.X_OPENKIM_ADDRESS_MAPPING, addressMapping);
            }
            String uuid = UUID.randomUUID().toString();
            logger.logLine("Die Mail wird mit UUID "+uuid+" im Header versendet! Diese UUID können Sie bei der Test-Operation 'Empfangen einer Mail über das OpenKIM-POP3-Gateway' angeben, um nur diese Mail zu erhalten");
            mimeMessage.addHeader(MailUtils.X_OPENKIM_TEST_ID, uuid);

            Transport.send(mimeMessage, (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_USER_NAME), (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_PWD));

            timeMetric.stopAndPublish();

            okConsumer.accept(defaultPipelineOperationContext);
        } catch (Exception e) {
            log.error("error on executing the SendMailOperation", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
