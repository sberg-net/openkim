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
package net.sberg.openkim.gateway.smtp.cmdhandler;

import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.gateway.smtp.AbstractGatewayHookableCmdHandler;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.UnknownCommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.UnknownHook;

import java.util.Collection;

public class SmtpGatewayUnknownCmdHandler extends AbstractGatewayHookableCmdHandler<UnknownHook> {

    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of(UnknownCommandHandler.COMMAND_IDENTIFIER);
    private static final String MISSING_CURR_COMMAND = "";
    public static final ProtocolSession.AttachmentKey<String> CURR_COMMAND = ProtocolSession.AttachmentKey.of("CURR_COMMAND", String.class);

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command, String parameters) {
        StringBuilder result = new StringBuilder();
        result.append(DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_CMD))
            .append(" Command ")
            .append(command)
            .append(" unrecognized.");
        return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, result);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command, String parameters) {
        session.setAttachment(CURR_COMMAND, command, ProtocolSession.State.Transaction);
        return null;
    }

    @Override
    protected HookResult callHook(UnknownHook rawHook, SMTPSession session, String parameters) {
        return rawHook.doUnknown(session, session.getAttachment(CURR_COMMAND, ProtocolSession.State.Transaction).orElse(MISSING_CURR_COMMAND));
    }

    @Override
    protected Class<UnknownHook> getHookInterface() {
        return UnknownHook.class;
    }

    @Override
    protected TimeMetric timer(Request request) {
        return gatewayMetricFactory.timer("smtp-unknown");
    }
}
