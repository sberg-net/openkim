/*
 * Copyright 2022 sberg it-systeme GmbH
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
import org.apache.james.core.Username;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.apache.james.protocols.pop3.core.CapaCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

public class Pop3GatewayUserCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability {

    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayUserCmdHandler.class);

    private static final Collection<String> COMMANDS = ImmutableSet.of("USER");
    private static final Set<String> CAPS = ImmutableSet.of("USER");

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        return CAPS;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-user", () -> user(session, request));
    }

    private Response user(POP3Session session, Request request) {
        ((Pop3GatewaySession) session).log("user begins");
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.AUTHENTICATION_READY && parameters != null) {
            try {
                ((Pop3GatewaySession) session).getLogger().parseUsername(parameters);
                session.setUsername(Username.of(((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername()));
                session.setHandlerState(POP3Session.AUTHENTICATION_USERSET);
                ((Pop3GatewaySession) session).log("user ends");
                return POP3Response.OK;
            } catch (Exception e) {
                log.error("error on separate user details: " + parameters, e);
                ((Pop3GatewaySession) session).log("user ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Invalid command arguments").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("user ends - error");
            return POP3Response.ERR;
        }
    }
}
