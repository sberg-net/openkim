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

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.smtp.EnumSmtpGatewayState;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpGatewayRsetCmdHandler extends RsetCmdHandler {

    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayRsetCmdHandler.class);

    private static final Response OK = new SMTPResponse(
        "250",
        DSNStatus.getStatus(2, "0.0") + " OK"
    ).immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(
        "500",
        DSNStatus.getStatus(5, "5.4") + " Unexpected argument provided with RSET command"
    ).immutable();

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((SmtpGatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("smto-rset", () -> doRSET(session, request));
    }

    private Response doRSET(SMTPSession session, Request request) {
        ((SmtpGatewaySession) session).log("rset begins");
        String argument = request.getArgument();
        if (argument != null && argument.length() != 0) {
            ((SmtpGatewaySession) session).log("rset ends - error");
            return SYNTAX_ERROR;
        } else {
            StringBuilder response = new StringBuilder();
            try {
                if (((SmtpGatewaySession) session).getSmtpClient() != null) {
                    int res = ((SmtpGatewaySession) session).getSmtpClient().rset();
                    if (!SMTPReply.isPositiveCompletion(res)) {
                        response.append(" rset in mta not allowed");
                        ((SmtpGatewaySession) session).log("rset ends - error");
                        return new SMTPResponse(String.valueOf(res), response);
                    } else {
                        ((SmtpGatewaySession) session).setGatewayState(EnumSmtpGatewayState.CONNECT);
                    }
                }
            } catch (Exception e) {
                log.error("error on doCoreCmd - rset in mta - " + session.getSessionID(), e);
                ((SmtpGatewaySession) session).log("rset ends - error");
                return new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, response);
            }
            session.resetState();
            ((SmtpGatewaySession) session).log("rset ends");
            return OK;
        }
    }
}
