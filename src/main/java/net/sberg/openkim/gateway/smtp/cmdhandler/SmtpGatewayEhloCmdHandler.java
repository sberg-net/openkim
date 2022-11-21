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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.gateway.smtp.AbstractGatewayHookableCmdHandler;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.esmtp.EhloExtension;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HeloHook;
import org.apache.james.protocols.smtp.hook.HookResult;

import java.util.Collection;
import java.util.List;

public class SmtpGatewayEhloCmdHandler extends AbstractGatewayHookableCmdHandler<HeloHook> implements EhloExtension {

    /**
     * The name of the command handled by the command handler
     */
    private static final String COMMAND_NAME = "EHLO";
    private static final Collection<String> COMMANDS = ImmutableSet.of(COMMAND_NAME);
    // see http://issues.apache.org/jira/browse/JAMES-419
    private static final List<String> ESMTP_FEATURES = ImmutableList.of("SIZE 35882577", "AUTH LOGIN PLAIN", "8BITMIME", "ENHANCEDSTATUSCODES");
    private static final Response DOMAIN_ADDRESS_REQUIRED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Domain address required: " + COMMAND_NAME).immutable();

    private List<EhloExtension> ehloExtensions;

    /**
     * Handler method called upon receipt of a EHLO command. Responds with a
     * greeting and informs the client whether client authentication is
     * required.
     *
     * @param session  SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    private Response doEHLO(SMTPSession session, String argument) {
        SMTPResponse resp = new SMTPResponse(SMTPRetCode.MAIL_OK, new StringBuilder(session.getConfiguration().getHelloName()).append(" Hello ").append(argument)
            .append(" [")
            .append(session.getRemoteAddress().getAddress().getHostAddress()).append("])"));

        session.setAttachment(SMTPSession.CURRENT_HELO_MODE,
            COMMAND_NAME, ProtocolSession.State.Connection);

        processExtensions(session, resp);

        return resp;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = super.getMarkerInterfaces();
        classes.add(EhloExtension.class);
        return classes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) {
        super.wireExtensions(interfaceName, extension);
        if (EhloExtension.class.equals(interfaceName)) {
            this.ehloExtensions = (List<EhloExtension>) extension;
        }
    }

    /**
     * Process the ehloExtensions
     *
     * @param session SMTPSession
     * @param resp    SMTPResponse
     */
    private void processExtensions(SMTPSession session, SMTPResponse resp) {
        if (ehloExtensions != null) {
            for (EhloExtension ehloExtension : ehloExtensions) {
                List<String> lines = ehloExtension.getImplementedEsmtpFeatures(session);
                if (lines != null) {
                    for (String line : lines) {
                        resp.appendLine(line);
                    }
                }
            }
        }
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command,
                                 String parameters) {
        return doEHLO(session, parameters);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String parameters) {
        session.resetState();

        if (parameters == null) {
            return DOMAIN_ADDRESS_REQUIRED;
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

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        return ImmutableList.<String>builder()
            .addAll(ESMTP_FEATURES)
            .addAll(getHooks().stream()
                .flatMap(heloHook -> heloHook.implementedEsmtpFeatures().stream())
                .collect(ImmutableList.toImmutableList()))
            .build();
    }
}
