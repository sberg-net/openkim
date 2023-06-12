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

import java.util.Collection;

public class Pop3GatewayStatCmdHandler extends AbstractPOP3CommandHandler {

    private static final Collection<String> COMMANDS = ImmutableSet.of("STAT");
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayStatCmdHandler.class);

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-stat", () -> stat(session));
    }

    private Response stat(POP3Session session) {
        ((Pop3GatewaySession) session).log("stat begins");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                int count = ((Pop3GatewaySession) session).getPop3ClientFolder().getMessageCount();
                int size = ((Pop3GatewaySession) session).getPop3ClientFolder().getMessageCount();
                ((Pop3GatewaySession) session).log("stat ends");
                return new POP3Response(POP3Response.OK_RESPONSE, count + " " + size);
            } catch (Exception e) {
                log.error("error on process stat command", e);
                ((Pop3GatewaySession) session).log("stat ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("stat ends - error");
            return POP3Response.ERR;
        }
    }
}
