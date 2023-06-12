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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.SMTPStartTlsResponse;
import org.apache.james.protocols.smtp.core.esmtp.EhloExtension;
import org.apache.james.protocols.smtp.dsn.DSNStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SmtpGatewayStartTlsCmdHandler implements CommandHandler<SMTPSession>, EhloExtension {
    /**
     * The name of the command handled by the command handler
     */
    private static final String COMMAND_NAME = "STARTTLS";
    private static final Collection<String> COMMANDS = ImmutableSet.of(COMMAND_NAME);
    private static final List<String> FEATURES = ImmutableList.of(COMMAND_NAME);

    private static final Response TLS_ALREADY_ACTIVE = new SMTPResponse(
        "500",
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_CMD) + " TLS already active RFC2487 5.2"
    ).immutable();
    private static final Response READY_FOR_STARTTLS = new SMTPStartTlsResponse(
        "220",
        DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.UNDEFINED_STATUS) + " Ready to start TLS"
    ).immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(
        "501",
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Syntax error (no parameters allowed) with STARTTLS command"
    ).immutable();
    private static final Response ALREADY_AUTH_ERROR = new SMTPResponse(
        "501",
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Syntax error (invalid command in this state) Already authenticated..."
    ).immutable();
    private static final Response NOT_SUPPORTED = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_CMD) + " Command " + COMMAND_NAME + " unrecognized."
    ).immutable();

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    /**
     * Handler method called upon receipt of a STARTTLS command. Resets
     * message-specific, but not authenticated user, state.
     */
    @Override
    public Response onCommand(SMTPSession session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((SmtpGatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("smto-starttls", () -> doSTARTTLS(session, request));
    }

    private Response doSTARTTLS(SMTPSession session, Request request) {
        if (session.isStartTLSSupported()) {
            if (session.isTLSStarted()) {
                return TLS_ALREADY_ACTIVE;
            } else {
                if (session.getUsername() != null) {
                    // Prevents session fixation as described in https://www.usenix.org/system/files/sec21-poddebniak.pdf
                    // Session 6.2
                    return ALREADY_AUTH_ERROR;
                }
                String parameters = request.getArgument();
                if ((parameters == null) || (parameters.length() == 0)) {
                    return READY_FOR_STARTTLS;
                } else {
                    return SYNTAX_ERROR;
                }
            }

        } else {
            return NOT_SUPPORTED;
        }
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        // SMTP STARTTLS
        if (!session.isTLSStarted() && session.isStartTLSSupported()) {
            return FEATURES;
        } else {
            return Collections.emptyList();
        }

    }

}
