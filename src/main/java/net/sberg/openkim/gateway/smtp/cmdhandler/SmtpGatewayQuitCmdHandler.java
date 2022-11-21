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
package net.sberg.openkim.gateway.smtp.cmdhandler;

import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.gateway.smtp.AbstractGatewayHookableCmdHandler;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.QuitHook;

import java.util.Collection;

public class SmtpGatewayQuitCmdHandler extends AbstractGatewayHookableCmdHandler<QuitHook> {

    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of("QUIT");

    private static final Response SYNTAX_ERROR;

    static {
        SMTPResponse response = new SMTPResponse(
            SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED,
            DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Unexpected argument provided with QUIT command"
        );
        response.setEndSession(true);
        SYNTAX_ERROR = response.immutable();
    }

    /**
     * Handler method called upon receipt of a QUIT command. This method informs
     * the client that the connection is closing.
     *
     * @param session  SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    private Response doQUIT(SMTPSession session, String argument) {
        if ((argument == null) || (argument.length() == 0)) {
            StringBuilder response = new StringBuilder();
            response.append(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.UNDEFINED_STATUS))
                .append(" ")
                .append(session.getConfiguration().getHelloName())
                .append(" Service closing transmission channel");
            SMTPResponse ret = new SMTPResponse(SMTPRetCode.SYSTEM_QUIT, response);
            ret.setEndSession(true);
            return ret;
        } else {
            return SYNTAX_ERROR;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command, String parameters) {
        return doQUIT(session, parameters);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command, String parameters) {
        return null;
    }

    @Override
    protected Class<QuitHook> getHookInterface() {
        return QuitHook.class;
    }

    @Override
    protected HookResult callHook(QuitHook rawHook, SMTPSession session, String parameters) {
        return rawHook.doQuit(session);
    }

}
