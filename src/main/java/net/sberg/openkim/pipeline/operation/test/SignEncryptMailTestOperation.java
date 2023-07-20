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
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.LoadVzdCertsOperation;
import net.sberg.openkim.pipeline.operation.mail.SignEncryptMailOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@PipelineOperation
@Component
public class SignEncryptMailTestOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(SignEncryptMailTestOperation.class);
    public static final String NAME = "SignEncryptMailTest";

    public static final String ENV_FROM = "from";
    public static final String ENV_TO = "to";
    public static final String ENV_CC = "cc";
    public static final String ENV_BCC = "bcc";
    public static final String ENV_SUBJECT = "subject";
    public static final String ENV_BODY = "body";

    @Autowired
    private LoadVzdCertsOperation loadVzdCertsOperation;
    @Autowired
    private SignEncryptMailOperation signEncryptMailOperation;

    @Override
    public boolean isTestable() {
        return true;
    }

    @Override
    public String getHrText() {
        return "Signieren und Verschlüsseln einer Mail über einen Konnektor Ihrer Wahl";
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            context.getLogger().logLine("Die Mail wurde erfolgreich signiert und verschlüsselt");
        };
    }

    private boolean checkMailAddresses(DefaultLogger logger, Map<String, X509CertificateResult> certMap, List<String> mailAddresses, boolean senderAddresses, boolean rcptAddresses) {
        try {
            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_ADDRESSES, mailAddresses);
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_SEARCH_BASE, logger.getDefaultLoggerContext().getKonnektor().getVzdSearchBase());
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_LOAD_SENDER_ADRESSES, senderAddresses);
            defaultPipelineOperationContext.setEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_LOAD_RCPT_ADRESSES, rcptAddresses);

            loadVzdCertsOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("loading certs for mailAddresses finished: "+mailAddresses.stream().collect(Collectors.joining(",")));
                },
                (context, e) -> {
                    log.error("error on loading certs for mailAddresses: "+mailAddresses.stream().collect(Collectors.joining(",")), e);
                }
            );

            List<X509CertificateResult> certs = (List)defaultPipelineOperationContext.getEnvironmentValue(LoadVzdCertsOperation.NAME, LoadVzdCertsOperation.ENV_VZD_CERTS);
            certs.stream().forEach(o -> certMap.put(o.getMailAddress(), o));

            return true;
        } catch (Exception e) {
            log.error("error on loading certs for: " + mailAddresses.stream().collect(Collectors.joining(",")), e);
            logger.logLine("error on loading certs for: " + mailAddresses.stream().collect(Collectors.joining(",")));
            return false;
        }
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer(NAME);

            MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
            mimeMessage.setText((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_BODY));
            mimeMessage.setSubject((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SUBJECT));
            mimeMessage.setFrom((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_FROM));

            String to = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_TO);
            if (to != null && !to.isEmpty()) {
                logger.getDefaultLoggerContext().getRecipientAddresses(true).addAll(Arrays.asList(to.split(",")));
                mimeMessage.setRecipients(Message.RecipientType.TO, to);
            }
            String cc = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CC);
            if (cc != null && !cc.isEmpty()) {
                logger.getDefaultLoggerContext().getRecipientAddresses(true).addAll(Arrays.asList(cc.split(",")));
                mimeMessage.setRecipients(Message.RecipientType.CC, to);
            }
            String bcc = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_BCC);
            if (bcc != null && !bcc.isEmpty()) {
                logger.getDefaultLoggerContext().getRecipientAddresses(true).addAll(Arrays.asList(bcc.split(",")));
                mimeMessage.setRecipients(Message.RecipientType.BCC, to);
            }

            //check sender
            List<String> senderAddresses = List.of((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_FROM));
            if (!checkMailAddresses(logger, logger.getDefaultLoggerContext().getSenderCerts(), senderAddresses, true, false)) {
                logger.logLine("load sender certs ends - error");
                throw new IllegalStateException("load sender certs ends - error");
            }

            //check recipients
            if (!checkMailAddresses(logger, logger.getDefaultLoggerContext().getRecipientCerts(), logger.getDefaultLoggerContext().getRecipientAddresses(true), false, true)) {
                logger.logLine("load recipient certs ends - error");
                throw new IllegalStateException("load recipient certs ends - error");
            }

            defaultPipelineOperationContext.setEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_ORIGIN_MIMEMESSAGE, mimeMessage);
            AtomicInteger failedCounter = new AtomicInteger();
            signEncryptMailOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    log.info("sign and encrypt mail finished");
                },
                (context, e) -> {
                    log.error("error on mail signing and encrypting", e);
                    failedCounter.incrementAndGet();
                }
            );

            if (failedCounter.get() == 0) {
                byte[] signEncryptMail = (byte[])defaultPipelineOperationContext.getEnvironmentValue(SignEncryptMailOperation.NAME, SignEncryptMailOperation.ENV_RESULT_MSG_BYTES);
                logger.logLine(new String(signEncryptMail), true);
            }
            else {
                throw new IllegalStateException("error on mail signing and encrypting");
            }

            timeMetric.stopAndPublish();

            okConsumer.accept(defaultPipelineOperationContext);
        } catch (Exception e) {
            log.error("error on executing the SignEncryptMailTestOperation", e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
