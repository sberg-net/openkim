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
package net.sberg.openkim.gateway.smtp;

import net.sberg.openkim.gateway.smtp.cmdhandler.*;
import net.sberg.openkim.pipeline.PipelineService;
import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChainImpl;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.*;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SmtpGatewayProtocolHandlerChain extends ProtocolHandlerChainImpl {

    public SmtpGatewayProtocolHandlerChain(boolean addDefault, PipelineService pipelineService) {
        if (addDefault) {
            addAll(initDefaultHandlers(pipelineService));
        }
    }

    protected List<ProtocolHandler> initDefaultHandlers(PipelineService pipelineService) {
        List<ProtocolHandler> defaultHandlers = new ArrayList<>();
        defaultHandlers.add(new CommandDispatcher<SMTPSession>());
        defaultHandlers.add(new ExpnCmdHandler());
        defaultHandlers.add(new SmtpGatewayEhloCmdHandler());
        defaultHandlers.add(new SmtpGatewayHeloCmdHandler());
        defaultHandlers.add(new HelpCmdHandler());
        defaultHandlers.add(new SmtpGatewayMailCmdHandler());
        defaultHandlers.add(new SmtpGatewayNoopCmdHandler());
        defaultHandlers.add(new SmtpGatewayQuitCmdHandler());
        defaultHandlers.add(new SmtpGatewayRcptCmdHandler(pipelineService));
        defaultHandlers.add(new SmtpGatewayRsetCmdHandler());
        defaultHandlers.add(new VrfyCmdHandler());
        defaultHandlers.add(new SmtpGatewayDataCmdHandler());
        defaultHandlers.add(new MailSizeEsmtpExtension());
        defaultHandlers.add(new SmtpGatewayWelcomeMessageHandler());
        defaultHandlers.add(new PostmasterAbuseRcptHook());
        defaultHandlers.add(new ReceivedDataLineFilter());
        defaultHandlers.add(new SmtpGatewayAuthCmdHandler(pipelineService));
        defaultHandlers.add(new DataLineMessageHookHandler());
        defaultHandlers.add(new SmtpGatewayStartTlsCmdHandler());
        defaultHandlers.add(new SmtpGatewayUnknownCmdHandler());
        defaultHandlers.add(new CommandHandlerResultLogger());
        return defaultHandlers;
    }

    private synchronized boolean checkForAuth(ProtocolHandler handler) {
        if (isReadyOnly()) {
            throw new UnsupportedOperationException("Read-Only");
        }
        return true;
    }

    @Override
    public boolean add(ProtocolHandler handler) {
        checkForAuth(handler);
        return super.add(handler);
    }

    @Override
    public boolean addAll(Collection<? extends ProtocolHandler> c) {
        return c.stream().allMatch(this::checkForAuth) && super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends ProtocolHandler> c) {
        return c.stream().allMatch(this::checkForAuth) && super.addAll(index, c);
    }

    @Override
    public void add(int index, ProtocolHandler element) {
        checkForAuth(element);
        super.add(index, element);
    }
}
