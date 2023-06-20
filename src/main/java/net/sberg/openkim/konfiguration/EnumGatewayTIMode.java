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
package net.sberg.openkim.konfiguration;

public enum EnumGatewayTIMode {
    FULLSTACK("Konnektor + KIM-Fachdienste werden benutzt"),
    KONNEKTOR("Konnektor wird benutzt. Ansonsten kann wird mit Internet/Intranet-Mailservern gearbeitet"),
    NO_TI("Konnektor wird NICHT benutzt und es wird mit Internet/Intranet-Mailservern gearbeitet");

    private final String hrText;

    EnumGatewayTIMode(String hrText) {
        this.hrText = hrText;
    }

    public String getHrText() {
        return hrText;
    }
}