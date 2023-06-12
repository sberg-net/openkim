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

import org.apache.james.protocols.api.ProtocolConfiguration;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.apache.james.protocols.pop3.POP3Protocol;

public class Pop3GatewayProtocol extends POP3Protocol {


    public Pop3GatewayProtocol(ProtocolHandlerChain chain, ProtocolConfiguration config) {
        super(chain, config);
    }

    @Override
    public ProtocolSession newSession(ProtocolTransport transport) {
        return new Pop3GatewaySession(transport, (Pop3GatewayConfiguration) this.getConfiguration());
    }
}
