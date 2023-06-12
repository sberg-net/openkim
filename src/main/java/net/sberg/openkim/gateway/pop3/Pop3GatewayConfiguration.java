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

import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.log.LogService;
import org.apache.james.protocols.pop3.POP3Configuration;

public class Pop3GatewayConfiguration extends POP3Configuration {

    private final Konfiguration konfiguration;
    private final LogService logService;

    public Pop3GatewayConfiguration(Konfiguration konfiguration, LogService logService) {
        this.konfiguration = konfiguration;
        this.logService = logService;
    }

    @Override
    public String getGreeting() {
        return "KOM-LE Clientmodul POP3";
    }

    public LogService getLogService() {
        return logService;
    }

    public Konfiguration getKonfiguration() {
        return konfiguration;
    }
}
