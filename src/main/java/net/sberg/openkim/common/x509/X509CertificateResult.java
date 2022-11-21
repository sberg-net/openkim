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
import net.sberg.openkim.konfiguration.konnektor.vzd.VzdResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class X509CertificateResult {
    private String mailAddress;
    private TelematikIdResult telematikIdResult;
    private List<VzdResult> vzdResults;
    private List<byte[]> certs = new ArrayList<>();
    private List<byte[]> rsaCerts = new ArrayList<>();
    private EnumX509ErrorCode errorCode = EnumX509ErrorCode.OK;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        X509CertificateResult that = (X509CertificateResult) o;
        return Objects.equals(mailAddress, that.mailAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mailAddress);
    }
}
