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
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;

import java.util.Collection;

public class SmtpGatewayHeloCmdHandler extends AbstractGatewayHookableCmdHandler<HeloHook> {

    private static final String COMMAND_NAME = "HELO";
    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of(COMMAND_NAME);

    private static final Response DOMAIN_REQUIRED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
        DSNStatus.getStatus(DSNStatus.PERMANENT,
            DSNStatus.DELIVERY_INVALID_ARG)
        + " Domain address required: " + COMMAND_NAME).immutable();

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command,
                                 String parameters) {
        session.setAttachment(SMTPSession.CURRENT_HELO_MODE, COMMAND_NAME, ProtocolSession.State.Connection);
        StringBuilder response = new StringBuilder();
        response.append(session.getConfiguration().getHelloName())
            .append(" Hello ")
            .append(parameters)
            .append(" [")
            .append(session.getRemoteAddress().getAddress().getHostAddress())
            .append("])");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, response);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String parameters) {
        session.resetState();

        if (parameters == null) {
            return DOMAIN_REQUIRED;
        } else {
            // store provided name
            session.setAttachment(SMTPSession.CURRENT_HELO_NAME, parameters, ProtocolSession.State.Connection);
            return null;
        }
    }

    @Override
    protected Class<HeloHook> getHookInterface() {
        return HeloHook.class;
    }


    @Override
    protected HookResult callHook(HeloHook rawHook, SMTPSession session, String parameters) {
        return rawHook.doHelo(session, parameters);
    }
}
