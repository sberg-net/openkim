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

import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.common.mail.EnumMailAuthMethod;
import net.sberg.openkim.common.mail.EnumMailConnectionSecurity;
import net.sberg.openkim.common.mail.MailUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.pop3.EnumPop3GatewayState;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsResult;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsResultContainer;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsService;
import org.apache.james.core.Username;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.bouncycastle.util.IPAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Type;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import java.util.Collection;
import java.util.Properties;

public class Pop3GatewayPassCmdHandler extends AbstractPOP3CommandHandler {
    private static final Collection<String> COMMANDS = ImmutableSet.of("PASS");
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayPassCmdHandler.class);

    private final DnsService dnsService;

    public Pop3GatewayPassCmdHandler(DnsService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        return gatewayMetricFactory.decorateSupplierWithTimerMetric("pop3-pass", () -> doAuth(session, request));
    }

    private Response doAuth(POP3Session session, Request request) {
        ((Pop3GatewaySession) session).log("pass begins");
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.AUTHENTICATION_USERSET && parameters != null) {
            return doAuth(session, session.getUsername(), parameters);
        } else {
            session.setHandlerState(POP3Session.AUTHENTICATION_READY);
            ((Pop3GatewaySession) session).log("pass ends - error");
            return new POP3Response(POP3Response.ERR_RESPONSE, "Authentication progress in false state").immutable();
        }
    }

    protected final Response doAuth(POP3Session session, Username user, String pass) {
        if ((user == null) || (pass == null)) {
            ((Pop3GatewaySession) session).log("pass ends - error");
            return new POP3Response(POP3Response.ERR_RESPONSE, "Could not decode parameters for AUTH USER/PASS");
        }

        try {

            String mailServerIpAddress = null;
            if (!IPAddress.isValid(((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost())) {

                ((Pop3GatewaySession) session).log("make a dns request for: " + ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost());
                DnsResult dnsResult = null;
                DnsResultContainer dnsResultContainer = dnsService.request(
                    ((Pop3GatewaySession) session).getLogger(),
                    ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost(),
                    Type.string(Type.A)
                );
                if (!dnsResultContainer.isError() && dnsResultContainer.getResult().size() >= 1) {
                    dnsResult = dnsResultContainer.getResult().get(0);
                }

                if (dnsResult == null) {
                    throw new IllegalStateException("ip-address for domain " + ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost() + " not found");
                }

                mailServerIpAddress = dnsResult.getAddress();
            } else {
                mailServerIpAddress = ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost();
                ((Pop3GatewaySession) session).log("DO NOT make a dns request. is an ip-address: " + mailServerIpAddress);
            }

            ((Pop3GatewaySession) session).log("connect to " + mailServerIpAddress);

            Konfiguration konfiguration = ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getKonfiguration();
            Properties props = new Properties();
            Session pop3ClientSession = MailUtils.createPop3ClientSession(
                props,
                EnumMailConnectionSecurity.SSLTLS,
                EnumMailAuthMethod.NORMALPWD,
                mailServerIpAddress,
                ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerPort(),
                konfiguration
            );

            Store store = pop3ClientSession.getStore("pop3");
            store.connect(((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername(), pass);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            ((Pop3GatewaySession) session).setPop3ClientFolder(inbox);
            ((Pop3GatewaySession) session).setPop3ClientStore(store);

            ((Pop3GatewaySession) session).setGatewayState(EnumPop3GatewayState.PROXY);
            session.setHandlerState(POP3Session.TRANSACTION);

            ((Pop3GatewaySession) session).log("pass ends");

            return new POP3Response(POP3Response.OK_RESPONSE, "Logged in.").immutable();
        } catch (Exception e) {
            log.error("error on authenticating the mta", e);
            ((Pop3GatewaySession) session).log("pass ends - error");
            return new POP3Response(POP3Response.ERR_RESPONSE, "Authentication credentials invalid or Temporary authentication failure").immutable();
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
