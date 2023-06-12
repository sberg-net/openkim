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
import net.sberg.openkim.konfiguration.EnumTIEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class KonnektorMonitoringResult {

    private String ip;
    private EnumTIEnvironment tiEnvironment;
    private boolean connectedWithTI;
    private boolean connectedWithSIS;
    private boolean eccEncryptionAvailable;
    private Date konnektorTime;
    private Date systemTime;
    private long diffSystemKonnektorTime;
    private boolean vzdAlive;
    private boolean tlsPortAlive;
    private List<KonnektorMonitoringFachdienstResult> fachdienstResults = new ArrayList<>();
    private List<KonnektorMonitoringWebserviceResult> webserviceResults = new ArrayList<>();
    private List<KonnektorMonitoringCardResult> cardResults = new ArrayList<>();

    public String getKonnektorHeadline() {
        return ip + " - " + tiEnvironment.getHrText();
    }

}
