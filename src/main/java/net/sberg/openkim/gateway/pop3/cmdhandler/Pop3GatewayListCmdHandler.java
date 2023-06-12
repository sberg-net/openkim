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
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.internet.MimeMessage;
import java.util.Collection;

public class Pop3GatewayListCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("LIST");
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayListCmdHandler.class);

    @Override
    @SuppressWarnings("unchecked")
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-list", () -> doList(session, request));
    }

    private Response doList(POP3Session session, Request request) {
        ((Pop3GatewaySession) session).log("list begins");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                if (request.getArgument() != null) {
                    int messageId = Integer.parseInt(request.getArgument());
                    MimeMessage message = (MimeMessage) ((Pop3GatewaySession) session).getPop3ClientFolder().getMessage(messageId);
                    POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, messageId + " " + message.getSize());
                    ((Pop3GatewaySession) session).log("list ends");
                    return response;
                } else {
                    Message[] sizes = ((Pop3GatewaySession) session).getPop3ClientFolder().getMessages();
                    POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, sizes.length == 0 ? "0 Messages" : "");
                    if (sizes.length == 0) {
                        response.appendLine(".");
                    } else {
                        StringBuilder contentBuilder = new StringBuilder();
                        for (int i = 0; i < sizes.length; i++) {
                            if (contentBuilder.length() > 0) {
                                contentBuilder.append("\r\n");
                            }
                            contentBuilder.append(i + 1).append(" ").append(sizes[i]);
                        }
                        contentBuilder.append("\r\n.");
                        response.appendLine(contentBuilder.toString());
                    }
                    ((Pop3GatewaySession) session).log("list ends");
                    return response;
                }
            } catch (Exception e) {
                log.error("error on process list command", e);
                ((Pop3GatewaySession) session).log("list ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("list ends - error");
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
