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

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.isismtt.x509.Admissions;
import org.bouncycastle.asn1.isismtt.x509.ProfessionInfo;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class X509CertificateUtils {

    private static final Logger log = LoggerFactory.getLogger(X509CertificateUtils.class);

    public static final TelematikIdResult extractTelematikId(byte[] certBytes, TelematikIdResult telematikIdResult) {
        try {
            if (!telematikIdResult.getErrorCode().equals(EnumX509ErrorCode.OK)) {
                return telematikIdResult;
            }
            X509CertificateHolder certificateHolder = new X509CertificateHolder(certBytes);
            Extension extension = certificateHolder.getExtension(new ASN1ObjectIdentifier("1.3.36.8.3.3"));
            AdmissionSyntax admissionSyntax = AdmissionSyntax.getInstance(extension.getParsedValue());
            Admissions[] admissions = admissionSyntax.getContentsOfAdmissions();
            for (Admissions ad : admissions) {
                ProfessionInfo[] professionInfos = ad.getProfessionInfos();
                for (ProfessionInfo professionInfo : professionInfos) {
                    String telematikId = professionInfo.getRegistrationNumber();
                    if (telematikIdResult.getTelematikId() == null || telematikIdResult.getTelematikId().trim().isEmpty()) {
                        telematikIdResult.setTelematikId(telematikId);
                    } else if (!telematikIdResult.getTelematikId().equals(telematikId)) {
                        telematikIdResult.setErrorCode(EnumX509ErrorCode.MORE_THAN_ONE_TELEMATIKID);
                        return telematikIdResult;
                    }
                }
            }
        } catch (Exception e) {
            log.error("error on extracting telematik id: " + telematikIdResult.toErrorText(), e);
            telematikIdResult.setErrorCode(EnumX509ErrorCode.OTHER);
        }
        return telematikIdResult;
    }
}
