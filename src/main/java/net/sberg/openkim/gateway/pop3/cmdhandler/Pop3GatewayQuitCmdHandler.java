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
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class Pop3GatewayQuitCmdHandler extends AbstractPOP3CommandHandler {

    private static final Collection<String> COMMANDS = ImmutableSet.of("QUIT");
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayQuitCmdHandler.class);
    private static final Response SIGN_OFF;

    static {
        POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, "");
        response.setEndSession(true);
        SIGN_OFF = response.immutable();
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-quit", () -> quit(session));
    }

    private Response quit(POP3Session session) {
        ((Pop3GatewaySession) session).log("quit begins");
        try {
            if (((Pop3GatewaySession) session).getPop3ClientFolder() != null) {
                ((Pop3GatewaySession) session).getPop3ClientFolder().close(true);
                ((Pop3GatewaySession) session).getPop3ClientStore().close();
                ((Pop3GatewaySession) session).setPop3ClientFolder(null);
                ((Pop3GatewaySession) session).setPop3ClientStore(null);
                ((Pop3GatewaySession) session).getDelMsgs().clear();
            }
            ((Pop3GatewaySession) session).log("quit ends");
            return SIGN_OFF;
        } catch (Exception e) {
            log.error("error on quit the pop3 client", e);
            POP3Response response = new POP3Response(POP3Response.ERR_RESPONSE, "");
            response.immutable();
            ((Pop3GatewaySession) session).log("quit ends - error");
            return response;
        }
    }
}
