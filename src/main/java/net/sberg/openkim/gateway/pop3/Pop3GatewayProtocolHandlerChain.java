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
package net.sberg.openkim.gateway.pop3;

import net.sberg.openkim.mail.MailService;
import net.sberg.openkim.gateway.pop3.cmdhandler.*;
import net.sberg.openkim.kas.KasService;
import net.sberg.openkim.konnektor.dns.DnsService;
import org.apache.james.protocols.api.handler.*;
import org.apache.james.protocols.pop3.POP3Session;

import java.util.ArrayList;
import java.util.List;

public class Pop3GatewayProtocolHandlerChain extends ProtocolHandlerChainImpl {

    public Pop3GatewayProtocolHandlerChain(KasService kasService, DnsService dnsService, MailService mailService) throws WiringException {
        addAll(initDefaultHandlers(kasService, dnsService, mailService));
        wireExtensibleHandlers();
    }

    protected List<ProtocolHandler> initDefaultHandlers(KasService kasService, DnsService dnsService, MailService mailService) {
        List<ProtocolHandler> handlers = new ArrayList<>();

        handlers.add(new Pop3GatewayPassCmdHandler(dnsService));
        handlers.add(new Pop3GatewayCapaCmdHandler());
        handlers.add(new Pop3GatewayAuthCmdHandler(dnsService));
        handlers.add(new Pop3GatewayUserCmdHandler());
        handlers.add(new Pop3GatewayListCmdHandler());
        handlers.add(new Pop3GatewayUidlCmdHandler());
        handlers.add(new Pop3GatewayRsetCmdHandler());
        handlers.add(new Pop3GatewayDeleCmdHandler());
        handlers.add(new Pop3GatewayNoopCmdHandler());
        handlers.add(new Pop3GatewayRetrCmdHandler(kasService, mailService));
        handlers.add(new Pop3GatewayTopCmdHandler());
        handlers.add(new Pop3GatewayStatCmdHandler());
        handlers.add(new Pop3GatewayQuitCmdHandler());
        handlers.add(new Pop3GatewayStlsCmdHandler());
        handlers.add(new Pop3GatewayWelcomeMessageHandler());
        handlers.add(new Pop3GatewayUnknownCmdHandler());
        handlers.add(new CommandDispatcher<POP3Session>());
        handlers.add(new CommandHandlerResultLogger());

        return handlers;
    }
}
