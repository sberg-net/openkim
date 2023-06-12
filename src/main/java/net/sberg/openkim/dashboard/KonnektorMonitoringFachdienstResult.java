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
package net.sberg.openkim.dashboard;

import lombok.Data;
import net.sberg.openkim.fachdienst.EnumFachdienst;

@Data
public class KonnektorMonitoringFachdienstResult {
    private EnumFachdienst typ;

    private String smtpDomain;
    private String smtpIpAddress;

    private String pop3Domain;
    private String pop3IpAddress;

    private String kasDomain;
    private String kasIpAddress;
    private String kasPort;
    private String kasContextPath;

    private String accmgrDomain;
    private String accmgrIpAddress;
    private String accmgrPort;
    private String accmgrContextPath;

    private boolean errorOnCreating;
    private boolean accmgrInitialized;
    private boolean kasInitialized;
    private boolean timedOut;

    public String getKasApiUrl() {
        if (getKasIpAddress() != null) {
            return "https://" + getKasIpAddress() + ":" + getKasPort() + getKasContextPath() + "/attachments/v2.2";
        }
        return "-";
    }

    public String getAmApiUrl() {
        if (getAccmgrIpAddress() != null) {
            return "https://" + getAccmgrIpAddress() + ":" + getAccmgrPort() + getAccmgrContextPath();
        }
        return "-";
    }
}
