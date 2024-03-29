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
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.pop3.EnumPop3GatewayState;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.konfiguration.EnumGatewayTIMode;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.mail.CreateDsnOperation;
import net.sberg.openkim.pipeline.operation.mail.CreateEmbeddedMessageRfc822Operation;
import net.sberg.openkim.pipeline.operation.mail.DecryptVerifyMailOperation;
import net.sberg.openkim.pipeline.operation.mail.MailUtils;
import net.sberg.openkim.pipeline.operation.mail.kas.KasIncomingMailOperation;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.POP3StreamResponse;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.apache.james.protocols.pop3.core.CRLFTerminatedInputStream;
import org.apache.james.protocols.pop3.core.ExtraDotInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class Pop3GatewayRetrCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("RETR");

    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayRetrCmdHandler.class);

    private PipelineService pipelineService;

    public Pop3GatewayRetrCmdHandler(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
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
            DecryptVerifyMailOperation decryptVerifyMailOperation = (DecryptVerifyMailOperation) pipelineService.getOperation(DecryptVerifyMailOperation.BUILTIN_VENDOR+"."+DecryptVerifyMailOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            defaultPipelineOperationContext.setEnvironmentValue(DecryptVerifyMailOperation.NAME, DecryptVerifyMailOperation.ENV_ENCRYPTED_MSG, encryptedMsg);
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
                return (byte[])defaultPipelineOperationContext.getEnvironmentValue(DecryptVerifyMailOperation.NAME, DecryptVerifyMailOperation.ENV_RESULT_MSG_BYTES);
            }
            else {
                throw new IllegalStateException("error on decrypting and verifying mail");
            }
        } catch (Exception e) {
            log.error("error on mail decrypting and verifying for the konnektor: " + konnektor.getIp(), e);
            throw e;
        }
    }

    private Response doRetr(POP3Session session, Request request) {

        Pop3GatewaySession pop3GatewaySession = (Pop3GatewaySession) session;
        pop3GatewaySession.log("retr begins");
        DefaultLogger logger = pop3GatewaySession.getLogger();

        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                MimeMessage message = (MimeMessage) pop3GatewaySession.getPop3ClientFolder().getMessage(Integer.parseInt(request.getArgument()));

                if (!MailUtils.checkAddressMapping(logger, message, false)) {
                    throw new IllegalStateException("error on checking of address mapping");
                }

                pop3GatewaySession.setGatewayState(EnumPop3GatewayState.PROCESS);

                byte[] pop3msg = null;
                if (logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.NO_TI)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    message.writeTo(baos);
                    pop3msg = baos.toByteArray();
                    baos.reset();
                    baos.close();
                }
                else {
                    if (logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.FULLSTACK)) {
                        KasIncomingMailOperation kasIncomingMailOperation = (KasIncomingMailOperation) pipelineService.getOperation(KasIncomingMailOperation.BUILTIN_VENDOR+"."+KasIncomingMailOperation.NAME);
                        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
                        defaultPipelineOperationContext.setEnvironmentValue(KasIncomingMailOperation.NAME, KasIncomingMailOperation.ENV_MSG, message);
                        defaultPipelineOperationContext.setEnvironmentValue(KasIncomingMailOperation.NAME, KasIncomingMailOperation.ENV_POP3_GATEWAY_SESSION, pop3GatewaySession);

                        kasIncomingMailOperation.execute(
                            defaultPipelineOperationContext,
                            context -> {
                                log.info("handle kas finished");
                            },
                            (context, e) -> {
                                log.error("error on handling of kas", e);
                            }
                        );
                        boolean valid = (boolean)defaultPipelineOperationContext.getEnvironmentValue(KasIncomingMailOperation.NAME, KasIncomingMailOperation.ENV_VALID_RESULT);
                        if (!valid) {
                            //embedded message
                            throw new IllegalStateException("error on handling of kas: "+pop3GatewaySession.getSessionID());
                        }
                        message = (MimeMessage) defaultPipelineOperationContext.getEnvironmentValue(KasIncomingMailOperation.NAME, KasIncomingMailOperation.ENV_RESULT_MSG);
                    }

                    String userMailAddress = pop3GatewaySession.getLogger().getDefaultLoggerContext().getMailServerUsername();
                    if (!logger.getDefaultLoggerContext().getKonfiguration().getGatewayTIMode().equals(EnumGatewayTIMode.FULLSTACK) && logger.getDefaultLoggerContext().getSenderAddressMapping().containsKey(userMailAddress)) {
                        userMailAddress = logger.getDefaultLoggerContext().getSenderAddressMapping().get(userMailAddress);
                    }

                    pop3msg = decryptVerify(pop3GatewaySession.getLogger(), userMailAddress, message);
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
                    }
                    else if (!logger.getDefaultLoggerContext().getMailDecryptErrorContext().isEmpty()) {
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
                pop3GatewaySession.log("retr ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            pop3GatewaySession.log("retr ends - error");
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
