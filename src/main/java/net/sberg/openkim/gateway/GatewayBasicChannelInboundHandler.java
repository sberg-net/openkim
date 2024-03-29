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
package net.sberg.openkim.gateway;

import io.netty.channel.ChannelHandlerContext;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.netty.BasicChannelInboundHandler;
import org.apache.james.protocols.netty.Encryption;
import org.apache.james.protocols.netty.ProtocolMDCContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayBasicChannelInboundHandler extends BasicChannelInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayBasicChannelInboundHandler.class);

    public GatewayBasicChannelInboundHandler(ProtocolMDCContextFactory mdcContextFactory, Protocol protocol, Encryption secure, boolean proxyRequired) {
        super(mdcContextFactory, protocol, secure, proxyRequired);
    }

    protected void cleanup(ChannelHandlerContext ctx) {
        ProtocolSession session = (ProtocolSession) ctx.channel().attr(SESSION_ATTRIBUTE_KEY).getAndSet(null);
        if (session != null) {
            if (session instanceof SmtpGatewaySession) {
                try {
                    if (((SmtpGatewaySession) session).getSmtpClient() != null) {
                        ((SmtpGatewaySession) session).getSmtpClient().logout();
                        ((SmtpGatewaySession) session).setSmtpClient(null);
                    }
                    ((SmtpGatewaySession) session).cleanup();
                } catch (Exception e) {
                    log.error("error on logout the smtp client", e);
                    ((SmtpGatewaySession) session).cleanup();
                }
            } else if (session instanceof Pop3GatewaySession) {
                try {
                    if (((Pop3GatewaySession) session).getPop3ClientFolder() != null) {
                        ((Pop3GatewaySession) session).getPop3ClientFolder().close(true);
                        ((Pop3GatewaySession) session).getPop3ClientStore().close();
                        ((Pop3GatewaySession) session).setPop3ClientFolder(null);
                        ((Pop3GatewaySession) session).setPop3ClientStore(null);
                        ((Pop3GatewaySession) session).getDelMsgs().clear();
                    }
                    ((Pop3GatewaySession) session).cleanup();
                } catch (Exception e) {
                    log.error("error on logout the smtp client", e);
                    ((SmtpGatewaySession) session).cleanup();
                }
            }
            session.resetState();
            session = null;
        }
        ctx.close();
    }
}
