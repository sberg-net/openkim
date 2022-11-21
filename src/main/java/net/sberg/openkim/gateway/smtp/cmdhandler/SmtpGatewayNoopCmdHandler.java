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
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;

import java.util.Collection;

public class SmtpGatewayNoopCmdHandler implements CommandHandler<SMTPSession> {

    /**
     * The name of the command handled by the command handler
     */
    private static final Collection<String> COMMANDS = ImmutableSet.of("NOOP");

    private static final Response NOOP = new SMTPResponse(SMTPRetCode.MAIL_OK, DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.UNDEFINED_STATUS) + " OK").immutable();

    /**
     * Handler method called upon receipt of a NOOP command.
     * Just sends back an OK and logs the command.
     */
    @Override
    public Response onCommand(SMTPSession session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((SmtpGatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("smto-auth", () -> NOOP);
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
