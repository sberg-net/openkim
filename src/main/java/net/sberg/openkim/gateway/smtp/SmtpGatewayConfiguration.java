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
package net.sberg.openkim.gateway.smtp;

import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.log.LogService;
import org.apache.james.protocols.smtp.SMTPConfigurationImpl;

public class SmtpGatewayConfiguration extends SMTPConfigurationImpl {

    private final Konfiguration konfiguration;
    private final LogService logService;

    public SmtpGatewayConfiguration(Konfiguration konfiguration, LogService logService) {
        this.konfiguration = konfiguration;
        this.logService = logService;
    }

    @Override
    public String getGreeting() {
        return "KOM-LE Clientmodul ESMTP";
    }

    public LogService getLogService() {
        return logService;
    }

    public Konfiguration getKonfiguration() {
        return konfiguration;
    }
}
