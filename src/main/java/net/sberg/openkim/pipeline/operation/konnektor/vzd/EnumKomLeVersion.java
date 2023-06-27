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
package net.sberg.openkim.pipeline.operation.konnektor.vzd;

public enum EnumKomLeVersion {
    V1_0("1.0", "1.0"),
    V1_5("1.5", "1.5"),
    V1_5plus("1.5.1", "1.5+");

    private String innerVersion;
    private String officalVersion;

    private EnumKomLeVersion(String innerVersion, String officalVersion) {
        this.innerVersion = innerVersion;
        this.officalVersion = officalVersion;
    }

    public String getInnerVersion() {
        return innerVersion;
    }

    public String getOfficalVersion() {
        return officalVersion;
    }

    public static final EnumKomLeVersion get(String officialVersion) {
        switch (officialVersion) {
            case "1.0": return EnumKomLeVersion.V1_0;
            case "1.5": return EnumKomLeVersion.V1_5;
            case "1.5+": return EnumKomLeVersion.V1_5plus;
            default: throw new IllegalStateException("unknown official version: "+officialVersion);
        }

    }
}
