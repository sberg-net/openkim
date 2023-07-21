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

import com.google.common.base.Preconditions;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoopGroup;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.netty.*;

import javax.inject.Inject;
import java.util.Optional;

public class GatewayNettyServer extends AbstractAsyncServer {
    public static class Factory {
        private Protocol protocol;
        private boolean proxyRequired;
        private Optional<Encryption> secure;
        private Optional<ChannelHandlerFactory> frameHandlerFactory;

        @Inject
        public Factory() {
            secure = Optional.empty();
            frameHandlerFactory = Optional.empty();
        }

        public GatewayNettyServer.Factory protocol(Protocol protocol) {
            Preconditions.checkNotNull(protocol, "'protocol' is mandatory");
            this.protocol = protocol;
            return this;
        }

        public GatewayNettyServer.Factory secure(Encryption secure) {
            this.secure = Optional.ofNullable(secure);
            return this;
        }

        public GatewayNettyServer.Factory proxyRequired(boolean proxyRequired) {
            this.proxyRequired = proxyRequired;
            return this;
        }

        public GatewayNettyServer.Factory frameHandlerFactory(ChannelHandlerFactory frameHandlerFactory) {
            this.frameHandlerFactory = Optional.ofNullable(frameHandlerFactory);
            return this;
        }

        public GatewayNettyServer build() {
            Preconditions.checkState(protocol != null, "'protocol' is mandatory");
            return new GatewayNettyServer(protocol,
                secure.orElse(null),
                proxyRequired,
                frameHandlerFactory.orElse(new LineDelimiterBasedChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH)));
        }
    }

    protected final Encryption secure;
    protected final Protocol protocol;
    private final ChannelHandlerFactory frameHandlerFactory;
    private int maxCurConnections;
    private int maxCurConnectionsPerIP;
    private boolean proxyRequired;

    private GatewayNettyServer(Protocol protocol, Encryption secure, boolean proxyRequired, ChannelHandlerFactory frameHandlerFactory) {
        this.protocol = protocol;
        this.secure = secure;
        this.proxyRequired = proxyRequired;
        this.frameHandlerFactory = frameHandlerFactory;
    }

    public void setMaxConcurrentConnections(int maxCurConnections) {
        if (isBound()) {
            throw new IllegalStateException("Server running already");
        }
        this.maxCurConnections = maxCurConnections;
    }

    public void setMaxConcurrentConnectionsPerIP(int maxCurConnectionsPerIP) {
        if (isBound()) {
            throw new IllegalStateException("Server running already");
        }
        this.maxCurConnectionsPerIP = maxCurConnectionsPerIP;
    }

    protected ChannelInboundHandlerAdapter createCoreHandler() {
        return new GatewayBasicChannelInboundHandler(new ProtocolMDCContextFactory.Standard(), protocol, secure, proxyRequired);
    }

    @Override
    public synchronized void bind() throws Exception {
        super.bind();
    }

    private ChannelHandlerFactory getFrameHandlerFactory() {
        return frameHandlerFactory;
    }

    @Override
    protected AbstractChannelPipelineFactory createPipelineFactory() {
        return new AbstractSSLAwareChannelPipelineFactory(
            getTimeout(),
            maxCurConnections,
            maxCurConnectionsPerIP,
            proxyRequired,
            secure,
            getFrameHandlerFactory(),
            new DefaultEventLoopGroup(16)
        ) {
          @Override
         protected ChannelInboundHandlerAdapter createHandler() {
                return createCoreHandler();
            }
        };

    }
}
