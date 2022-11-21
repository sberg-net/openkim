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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.sun.mail.pop3.POP3Message;
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

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Pop3GatewayTopCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability {
    private static final Collection<String> COMMANDS = ImmutableList.of("TOP");
    private static final Set<String> CAPS = ImmutableSet.of("TOP");

    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayTopCmdHandler.class);

    private static final Response SYNTAX_ERROR = new POP3Response(POP3Response.ERR_RESPONSE, "Usage: TOP [mail number] [line count]").immutable();

    private class Args {
        private final int messageNumber;
        private final Optional<Integer> lineCount;

        private Args(int messsageNumber, Optional<Integer> lineCount) {
            this.messageNumber = messsageNumber;
            this.lineCount = lineCount;
        }
    }

    private Optional<Args> fromRequest(Request request) {
        String parameters = request.getArgument();
        if (parameters == null) {
            return Optional.empty();
        }

        try {
            List<Integer> args = Splitter.on(' ')
                .omitEmptyStrings()
                .trimResults()
                .splitToList(parameters)
                .stream()
                .map(Integer::parseInt)
                .collect(ImmutableList.toImmutableList());

            if (args.size() == 2) {
                return Optional.of(new Args(args.get(0), Optional.of(args.get(1))));
            } else if (args.size() == 1) {
                return Optional.of(new Args(args.get(0), Optional.empty()));
            } else {
                return Optional.empty();
            }
        } catch (NumberFormatException nfe) {
            return Optional.empty();
        }
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-top", () -> doTop(session, request));
    }

    private Response doTop(POP3Session session, Request request) {
        ((Pop3GatewaySession) session).log("top begins");
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            try {
                Optional<Args> optionalArgs = fromRequest(request);
                if (optionalArgs.isEmpty() || optionalArgs.get().lineCount.isEmpty()) {
                    return SYNTAX_ERROR;
                }
                Args args = optionalArgs.get();
                POP3Message message = (POP3Message) ((Pop3GatewaySession) session).getPop3ClientFolder().getMessage(args.messageNumber);
                InputStream stream = message.top(0);
                String msg = new String(stream.readAllBytes());
                POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, "");
                response.appendLine(msg + ".");
                ((Pop3GatewaySession) session).log("top ends");
                return response;
            } catch (Exception e) {
                log.error("error on process top command", e);
                ((Pop3GatewaySession) session).log("top ends - error");
                return new POP3Response(POP3Response.ERR_RESPONSE, "Technical error").immutable();
            }
        } else {
            ((Pop3GatewaySession) session).log("top ends - error");
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
