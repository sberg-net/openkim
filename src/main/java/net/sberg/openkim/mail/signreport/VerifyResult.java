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
package net.sberg.openkim.mail.signreport;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class VerifyResult {

    public static final String VALID = "urn:oasis:names:tc:dss:1.0:detail:valid";
    public static final String INVALID = "urn:oasis:names:tc:dss:1.0:detail:invalid";
    public static final String INDETERMINED = "urn:oasis:names:tc:dss:1.0:detail:indetermined";

    private String verificationTime;
    private String signingTime;
    private String formatResult;
    private String signResult;
    private String signAlg;
    private String pathValiditySummary;
    private String issuer;
    private String serialNumber;
    private List<VerifyCertResult> certResults = new ArrayList<>();

    @JsonIgnore
    public String extractSigAlg() {
        try {
            return signAlg.split("#")[1].split("-")[0].toUpperCase();
        } catch (Exception e) {
            return signAlg;
        }
    }

    @JsonIgnore
    public String extractHashAlg() {
        try {
            return signAlg.split("#")[1].split("-")[1].toUpperCase();
        } catch (Exception e) {
            return signAlg;
        }
    }

    @JsonIgnore
    public String getFormatResultHr() {
        if (formatResult == null) {
            return "Unbekannt";
        }
        switch (formatResult) {
            case VALID:
                return "Gültig";
            case INVALID:
                return "Ungültig";
            case INDETERMINED:
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
            case VALID:
                return "Gültig";
            case INVALID:
                return "Ungültig";
            case INDETERMINED:
                return "Unbestimmt";
            default:
                return "Unbekannt";
        }
    }

    @JsonIgnore
    public String getPathValiditySummaryHr() {
        if (pathValiditySummary == null) {
            return "Unbekannt";
        }
        switch (pathValiditySummary) {
            case VALID:
                return "Gültig";
            case INVALID:
                return "Ungültig";
            case INDETERMINED:
                return "Unbestimmt";
            default:
                return "Unbekannt";
        }
    }

    @JsonIgnore
    public String getCertSummary() {
        StringBuilder contentBuilder = new StringBuilder();
        int idx = 1;
        for (Iterator<VerifyCertResult> iterator = certResults.iterator(); iterator.hasNext(); ) {
            VerifyCertResult certResult = iterator.next();
            contentBuilder.append("<b>Zertifikat " + idx + "</b><br/>");
            contentBuilder.append("Pfad-Gültigkeit: " + certResult.getChainingResultHr() + "<br/>");
            contentBuilder.append("Extension-Gültigkeit: " + certResult.getExtensionResultHr() + "<br/>");
            contentBuilder.append("Zeitraum-Gültigkeit: " + certResult.getValidityPeriodResultHr() + "<br/>");
            contentBuilder.append("Sign-Gültigkeit: " + certResult.getSignResultHr() + "<br/>");

            if (certResult.getCertificate() != null) {
                contentBuilder.append("Version: " + certResult.getCertificate().getVersion() + "<br/>");
                contentBuilder.append("Seriennummer: " + certResult.getCertificate().getSerialNumber() + "<br/>");
                contentBuilder.append("Sign-Alg: " + certResult.getCertificate().getSigAlgName() + "<br/>");
                contentBuilder.append("Subject: " + certResult.getCertificate().getSubjectDN() + "<br/>");
                contentBuilder.append("Herausgeber: " + certResult.getCertificate().getIssuerDN() + "<br/>");
                contentBuilder.append("Gültig Von: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(certResult.getCertificate().getNotBefore()) + "<br/>");
                contentBuilder.append("Gültig Bis: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(certResult.getCertificate().getNotAfter()) + "<br/>");
            }

            if (certResult.getVerifyOCSPResult() != null) {
                contentBuilder.append("<b>OCSP-Angaben zum Zertifikat</b><br/>");
                contentBuilder.append("Revoked: " + (certResult.getVerifyOCSPResult().isRevoked() ? "Ja" : "Nein") + "<br/>");
                contentBuilder.append("Unbekannter Status: " + (certResult.getVerifyOCSPResult().isUnknown() ? "Ja" : "Nein") + "<br/>");
                contentBuilder.append("Revoke-Zeit: " + (certResult.getVerifyOCSPResult().getRevokeTime() != null ? certResult.getVerifyOCSPResult().getRevokeTime() : "-") + "<br/>");
                contentBuilder.append("Aufruf-Zeit: " + (certResult.getVerifyOCSPResult().getProduceTime() != null ? certResult.getVerifyOCSPResult().getProduceTime() : "-") + "<br/>");

                int count = 1;
                for (Iterator<X509Certificate> verifyCertResultIterator = certResult.getVerifyOCSPResult().getCerts().iterator(); verifyCertResultIterator.hasNext(); ) {
                    X509Certificate cert = verifyCertResultIterator.next();
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Version: " + cert.getVersion() + "<br/>");
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Seriennummer: " + cert.getSerialNumber() + "<br/>");
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Sign-Alg: " + cert.getSigAlgName() + "<br/>");
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Subject: " + cert.getSubjectDN() + "<br/>");
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Herausgeber: " + cert.getIssuerDN() + "<br/>");
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Gültig Von: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(cert.getNotBefore()) + "<br/>");
                    contentBuilder.append("<i>Zeritifikat " + count + "</i> - Gültig Bis: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(cert.getNotAfter()) + "<br/>");
                    count++;
                }
            } else {
                contentBuilder.append("<b>Keine OCSP-Angaben zum Zertifikat vorhanden</b><br/>");
            }
            idx++;
        }
        return contentBuilder.toString();
    }
}
