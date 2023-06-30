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
import net.sberg.openkim.common.EnumMailConnectionSecurity;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsRequestOperation;
import net.sberg.openkim.pipeline.operation.mail.MailUtils;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class ReadMailTestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(ReadMailTestOperation.class);
    public static final String NAME = "ReadMailTest";

    public static final String ENV_USER_NAME = "userName";
    public static final String ENV_PWD = "pwd";
    public static final String ENV_DELETE_MESSAGE = "deleteMessage";
    public static final String ENV_UUID = "uuid";
    public static final String ENV_MESSAGEID = "messageId";

    @Autowired
    private DnsRequestOperation dnsRequestOperation;

    @Override
    public boolean isTestable() {
        return true;
    }

    @Override
    public String getHrText() {
        return "Empfangen einer Mail über das OpenKIM-POP3-Gateway";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            context.getLogger().logLine("Die Mail(s) wurden erfolgreich empfangen");
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

        Store store = null;
        Folder inbox = null;

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            String userName = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_USER_NAME);
            String password = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_PWD);
            boolean deleteMessage = Boolean.valueOf((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_DELETE_MESSAGE));
            String uuid = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_UUID);
            String messageId = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_MESSAGEID);

            Properties props = new Properties();
            Session pop3ClientSession = MailUtils.createPop3ClientSession(
                props,
                EnumMailConnectionSecurity.NONE,
                EnumMailAuthMethod.NORMALPWD,
                konfiguration.getGatewayHost(),
                konfiguration.getPop3GatewayPort(),
                konfiguration.getPop3GatewayIdleTimeoutInSeconds() * 1000,
                null,
                null,
                false
            );

            store = pop3ClientSession.getStore("pop3");
            store.connect(userName, password);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.getMessages();

            for (Message message : messages) {
                String[] headerOpenkimTestId = message.getHeader(MailUtils.X_OPENKIM_TEST_ID);
                boolean readMail = false;
                if (headerOpenkimTestId != null && headerOpenkimTestId.length > 0) {
                    if (uuid == null || uuid.trim().isEmpty()) {
                        logger.logLine(((MimeMessage) message).getMessageID() + " is a test message: uuid = " + headerOpenkimTestId[0]);
                    }
                    else {
                        if (headerOpenkimTestId[0].equals(uuid)) {
                            readMail = true;
                        }
                    }
                }
                else {
                    if (uuid == null || uuid.trim().isEmpty()) {
                        logger.logLine(((MimeMessage) message).getMessageID() + " no test message");
                    }
                    if (messageId != null
                        &&
                        !messageId.trim().isEmpty()
                        &&
                        message.getHeader("Message-ID") != null
                        &&
                        message.getHeader("Message-ID").length > 0
                        &&
                        messageId.equals(message.getHeader("Message-ID")[0])) {
                        readMail = true;
                    }
                }

                if (readMail) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    message.writeTo(byteArrayOutputStream);
                    byte[] result = byteArrayOutputStream.toByteArray();
                    logger.logLine(new String(result), true);
                    if (deleteMessage) {
                        message.setFlag(Flags.Flag.DELETED, true);
                    }
                }
            }

            timeMetric.stopAndPublish();
            okConsumer.accept(defaultPipelineOperationContext);

        }
        catch (Exception e) {
            log.error("error on executing the ReadMailTestOperation", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
        finally {
            try {
                if (inbox != null) {
                    inbox.close(true);
                }
            } catch (Exception e) {
                log.error("error on read messages: close inbox", e);
                logger.logLine("error on read messages: close inbox");
            }
            try {
                if (store != null) {
                    store.close();
                }
            } catch (Exception e) {
                log.error("error on read messages: close store", e);
                logger.logLine("error on read messages: close store");
            }
        }
    }
}
