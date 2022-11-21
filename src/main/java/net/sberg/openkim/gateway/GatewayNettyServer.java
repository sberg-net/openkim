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
package net.sberg.openkim.gateway;

import com.google.common.base.Preconditions;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.netty.*;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.util.HashedWheelTimer;

import javax.inject.Inject;
import java.util.Optional;

public class GatewayNettyServer extends AbstractAsyncServer {
    protected final Encryption secure;
    protected final Protocol protocol;
    private final ChannelHandlerFactory frameHandlerFactory;
    private final HashedWheelTimer hashedWheelTimer;
    private ExecutionHandler eHandler;
    private ChannelUpstreamHandler coreHandler;
    private int maxCurConnections;
    private int maxCurConnectionsPerIP;

    private GatewayNettyServer(Protocol protocol, Encryption secure, ChannelHandlerFactory frameHandlerFactory, HashedWheelTimer hashedWheelTimer) {
        this.protocol = protocol;
        this.secure = secure;
        this.frameHandlerFactory = frameHandlerFactory;
        this.hashedWheelTimer = hashedWheelTimer;
    }

    protected ChannelUpstreamHandler createCoreHandler() {
        return new GatewayBasicChannelUpstreamHandler(new ProtocolMDCContextFactory.Standard(), this.protocol, this.secure);
    }

    public synchronized void bind() throws Exception {
        this.coreHandler = this.createCoreHandler();
        super.bind();
    }

    private ChannelHandlerFactory getFrameHandlerFactory() {
        return this.frameHandlerFactory;
    }

    protected ChannelPipelineFactory createPipelineFactory(ChannelGroup group) {
        return new AbstractSSLAwareChannelPipelineFactory(
            this.getTimeout(),
            this.maxCurConnections,
            this.maxCurConnectionsPerIP,
            group,
            this.secure,
            this.eHandler,
            this.getFrameHandlerFactory(),
            this.hashedWheelTimer
        ) {
            protected ChannelUpstreamHandler createHandler() {
                return GatewayNettyServer.this.coreHandler;
            }
        };
    }

    public static class Factory {
        private final HashedWheelTimer hashedWheelTimer;
        private Protocol protocol;
        private Optional<Encryption> secure;
        private Optional<ChannelHandlerFactory> frameHandlerFactory;

        @Inject
        public Factory(HashedWheelTimer hashedWheelTimer) {
            this.hashedWheelTimer = hashedWheelTimer;
            this.secure = Optional.empty();
            this.frameHandlerFactory = Optional.empty();
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

        public GatewayNettyServer.Factory frameHandlerFactory(ChannelHandlerFactory frameHandlerFactory) {
            this.frameHandlerFactory = Optional.ofNullable(frameHandlerFactory);
            return this;
        }

        public GatewayNettyServer build() {
            Preconditions.checkState(this.protocol != null, "'protocol' is mandatory");
            return new GatewayNettyServer(
                this.protocol,
                this.secure.orElse(null),
                this.frameHandlerFactory.orElse(new LineDelimiterBasedChannelHandlerFactory(8192)),
                this.hashedWheelTimer
            );
        }
    }
}
