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
import com.sun.mail.pop3.POP3Folder;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.apache.james.protocols.pop3.core.CapaCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import java.util.Collection;
import java.util.Set;

public class Pop3GatewayUidlCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability {
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayUidlCmdHandler.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("UIDL");
    private static final Set<String> CAPS = ImmutableSet.of("UIDL");

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-uidl", () -> doUidl(session, request));
    }

    private String getUid(int messageId, POP3Session session) throws Exception {
        Message msg = ((Pop3GatewaySession) session).getPop3ClientFolder().getMessage(messageId);
        POP3Folder uidFolder = (POP3Folder) ((Pop3GatewaySession) session).getPop3ClientFolder();
        return uidFolder.getUID(msg);
    }

    private Response doUidl(POP3Session session, Request request) {
        ((Pop3GatewaySession) session).log("uidl begins");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                if (request.getArgument() != null) {
                    int messageId = Integer.parseInt(request.getArgument());
                    POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, messageId + " " + getUid(messageId, session));
                    ((Pop3GatewaySession) session).log("uidl ends");
                    return response;
                } else {
                    int length = ((Pop3GatewaySession) session).getPop3ClientFolder().getMessageCount();
                    POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, length == 0 ? "0 Messages" : "");
                    if (length == 0) {
                        response.appendLine(".");
                    } else {
                        StringBuilder contentBuilder = new StringBuilder();
                        for (int i = 0; i < length; i++) {
                            if (contentBuilder.length() > 0) {
                                contentBuilder.append("\r\n");
                            }
                            contentBuilder.append(i + 1).append(" ").append(getUid(i + 1, session));

                        }
                        contentBuilder.append("\r\n.");
                        response.appendLine(contentBuilder.toString());
                    }
                    ((Pop3GatewaySession) session).log("uidl ends");
                    return response;
                }
            } catch (Exception e) {
                log.error("error on process uidl command", e);
                ((Pop3GatewaySession) session).log("uidl ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("uidl ends - error");
            return POP3Response.ERR;
        }
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        return CAPS;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
