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

import javax.mail.Flags;
import javax.mail.Message;
import java.util.Collection;
import java.util.Iterator;

public class Pop3GatewayRsetCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("RSET");
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayRsetCmdHandler.class);

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-rset", () -> rset(session));
    }

    private Response rset(POP3Session session) {
        ((Pop3GatewaySession) session).log("rset begins");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                for (Iterator<Integer> iterator = ((Pop3GatewaySession) session).getDelMsgs().iterator(); iterator.hasNext(); ) {
                    int messageId = iterator.next();
                    Message message = ((Pop3GatewaySession) session).getPop3ClientFolder().getMessage(messageId);
                    message.setFlag(Flags.Flag.DELETED, false);
                }
                ((Pop3GatewaySession) session).getDelMsgs().clear();
                ((Pop3GatewaySession) session).log("rset ends");
                return POP3Response.OK;
            } catch (Exception e) {
                log.error("error on process rset command", e);
                ((Pop3GatewaySession) session).log("rset ends - error");
                return POP3Response.ERR;
            }
        } else {
            ((Pop3GatewaySession) session).log("rset ends - error");
            return POP3Response.ERR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
