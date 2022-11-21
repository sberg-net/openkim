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
package net.sberg.openkim.gateway.smtp.hook;

import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.QuitHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmtpGatewayQuitHook implements QuitHook {

    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayQuitHook.class);

    @Override
    public HookResult doQuit(SMTPSession smtpSession) {
        try {
            ((SmtpGatewaySession) smtpSession).log("quit hook begins");
            if (((SmtpGatewaySession) smtpSession).getSmtpClient() == null || ((SmtpGatewaySession) smtpSession).getSmtpClient().logout()) {
                ((SmtpGatewaySession) smtpSession).setSmtpClient(null);
                ((SmtpGatewaySession) smtpSession).log("quit hook ends");
                return HookResult.OK;
            }
            ((SmtpGatewaySession) smtpSession).log("quit hook ends - error");
            return HookResult.DENY;
        } catch (Exception e) {
            log.error("error on doQuit smtp gateway quit hook - " + smtpSession.getSessionID(), e);
            ((SmtpGatewaySession) smtpSession).log("quit hook ends - error");
            return HookResult.DENY;
        }
    }
}
