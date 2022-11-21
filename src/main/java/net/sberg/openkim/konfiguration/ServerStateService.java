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
package net.sberg.openkim.konfiguration;

import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

@Service
public class ServerStateService {

    public List<ServerState> check(Konfiguration konfiguration, boolean smtpGatewayStarted, boolean pop3GatewayStarted) throws Exception {
        List<ServerState> states = new ArrayList<>();

        if (!smtpGatewayStarted) {
            states.add(new ServerState(
                "smtpgateway",
                "SMTP-Gateway",
                konfiguration.getGatewayHost(),
                konfiguration.getSmtpGatewayPort(),
                false,
                true
            ));
        } else {
            states.add(checkServer(
                "smtpgateway",
                "SMTP-Gateway",
                konfiguration.getGatewayHost(),
                konfiguration.getSmtpGatewayPort(),
                true
            ));
        }

        if (!pop3GatewayStarted) {
            states.add(new ServerState(
                "pop3gateway",
                "POP3-Gateway",
                konfiguration.getGatewayHost(),
                konfiguration.getPop3GatewayPort(),
                false,
                true
            ));
        } else {
            states.add(checkServer(
                "pop3gateway",
                "POP3-Gateway",
                konfiguration.getGatewayHost(),
                konfiguration.getPop3GatewayPort(),
                true
            ));
        }

        return states;
    }

    public ServerState checkServer(String id, String name, String host, String port, boolean active) {
        try {
            if (active) {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, Integer.parseInt(port)), 2000);
                socket.close();
                return new ServerState(id, name, host, port, true, active);
            } else {
                return new ServerState(id, name, host, port, false, active);
            }
        } catch (Exception e) {
            return new ServerState(id, name, host, port, false, active);
        }
    }
}
