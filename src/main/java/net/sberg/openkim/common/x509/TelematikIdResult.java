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
package net.sberg.openkim.common.x509;

import lombok.Data;

@Data
public class TelematikIdResult {
    private String email;
    private String icssn;
    private String telematikId;
    private EnumX509ErrorCode errorCode = EnumX509ErrorCode.OK;

    public String toErrorText() {
        StringBuilder resultBuilder = new StringBuilder();
        if (email != null && !email.trim().isEmpty()) {
            resultBuilder.append("Email: ").append(email);
        }
        if (icssn != null && !icssn.trim().isEmpty()) {
            if (resultBuilder.length() > 0) {
                resultBuilder.append(", ");
            }
            resultBuilder.append("Icssn: ").append(icssn);
        }
        if (telematikId != null && !telematikId.trim().isEmpty()) {
            if (resultBuilder.length() > 0) {
                resultBuilder.append(", ");
            }
            resultBuilder.append("TelematikId: ").append(telematikId);
        }
        return resultBuilder.toString();
    }
}
