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
import net.sberg.openkim.mail.MailService;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.pop3.EnumPop3GatewayState;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.kas.KasService;
import net.sberg.openkim.log.DefaultLogger;
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
import java.io.InputStream;
import java.util.Collection;

public class Pop3GatewayRetrCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("RETR");

    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayRetrCmdHandler.class);

    private final KasService kasService;
    private final MailService mailService;

    public Pop3GatewayRetrCmdHandler(KasService kasService, MailService mailService) {
        this.mailService = mailService;
        this.kasService = kasService;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-retr", () -> doRetr(session, request));
    }

    private Response doRetr(POP3Session session, Request request) {

        ((Pop3GatewaySession) session).log("retr begins");
        DefaultLogger logger = ((Pop3GatewaySession) session).getLogger();

        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                MimeMessage message = (MimeMessage) ((Pop3GatewaySession) session).getPop3ClientFolder().getMessage(Integer.parseInt(request.getArgument()));
                //message = kasService.executeIncoming(((Pop3GatewaySession)session).getLogger(), message, (Pop3GatewaySession)session);
                ((Pop3GatewaySession) session).setGatewayState(EnumPop3GatewayState.PROCESS);

                byte[] pop3msg = mailService.decryptVerify(((Pop3GatewaySession) session).getLogger(), ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername(), message);
                if (!logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().isEmpty()) {
                    pop3msg = mailService.createDsn((Pop3GatewaySession) session, logger.getDefaultLoggerContext().getMailSignVerifyErrorContext(), message);
                } else if (!logger.getDefaultLoggerContext().getMailDecryptErrorContext().isEmpty()) {
                    pop3msg = mailService.createEmbeddedMessageRfc822(((Pop3GatewaySession) session).getLogger(), logger.getDefaultLoggerContext().getMailDecryptErrorContext(), message);
                }

                InputStream in = new CRLFTerminatedInputStream(new ExtraDotInputStream(new ByteArrayInputStream(pop3msg)));
                POP3StreamResponse response = new POP3StreamResponse(POP3Response.OK_RESPONSE, "Message follows", in);
                ((Pop3GatewaySession) session).setGatewayState(EnumPop3GatewayState.PROXY);
                ((Pop3GatewaySession) session).log("retr ends");
                return response;
            } catch (Exception e) {
                log.error("error on process retr command", e);
                ((Pop3GatewaySession) session).log("retr ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("retr ends - error");
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
