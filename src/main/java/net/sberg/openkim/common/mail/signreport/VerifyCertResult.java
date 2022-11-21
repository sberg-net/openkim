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
package net.sberg.openkim.common.mail.signreport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.security.cert.X509Certificate;

@Data
public class VerifyCertResult {
    private String chainingResult;
    private String validityPeriodResult;
    private String extensionResult;
    private String signResult;
    private String signAlg;
    private X509Certificate certificate;
    private VerifyOCSPResult verifyOCSPResult;

    @JsonIgnore
    public String getChainingResultHr() {
        if (chainingResult == null) {
            return "Unbekannt";
        }
        switch (chainingResult) {
            case VerifyResult.VALID:
                return "Gültig";
            case VerifyResult.INVALID:
                return "Ungültig";
            case VerifyResult.INDETERMINED:
                return "Unbestimmt";
            default:
                return "Unbekannt";
        }
    }

    @JsonIgnore
    public String getValidityPeriodResultHr() {
        if (validityPeriodResult == null) {
            return "Unbekannt";
        }
        switch (validityPeriodResult) {
            case VerifyResult.VALID:
                return "Gültig";
            case VerifyResult.INVALID:
                return "Ungültig";
            case VerifyResult.INDETERMINED:
                return "Unbestimmt";
            default:
                return "Unbekannt";
        }
    }

    @JsonIgnore
    public String getExtensionResultHr() {
        if (extensionResult == null) {
            return "Unbekannt";
        }
        switch (extensionResult) {
            case VerifyResult.VALID:
                return "Gültig";
            case VerifyResult.INVALID:
                return "Ungültig";
            case VerifyResult.INDETERMINED:
                return "Unbestimmt";
            default:
                return "Unbekannt";
        }
    }

    @JsonIgnore
    public String getSignResultHr() {
        if (signResult == null) {
            return "Unbekannt";
        }
        switch (signResult) {
            case VerifyResult.VALID:
                return "Gültig";
            case VerifyResult.INVALID:
                return "Ungültig";
            case VerifyResult.INDETERMINED:
                return "Unbestimmt";
            default:
                return "Unbekannt";
        }
    }

    @JsonIgnore
    public String extractSigAlg() {
        try {
            return signAlg.split("#")[1].split("-")[0].toUpperCase();
        } catch (Exception e) {
            return signAlg;
        }
    }
}
