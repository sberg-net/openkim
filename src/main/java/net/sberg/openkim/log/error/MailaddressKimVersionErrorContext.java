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
package net.sberg.openkim.log.error;

import lombok.Data;
import net.sberg.openkim.common.x509.X509CertificateResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MailaddressKimVersionErrorContext implements IErrorContext {
    private Map<EnumErrorCode, List<X509CertificateResult>> errorCerts = new HashMap<>();
    private Map<String, List<EnumErrorCode>> addressErrors = new HashMap<>();

    private List<String> rcptAddresses = new ArrayList<>();
    private String senderAddress;

    public boolean isEmpty() {
        return errorCerts.isEmpty();
    }

    public void add(X509CertificateResult x509CertificateResult, EnumErrorCode errorCode, boolean sender) {
        if (!errorCerts.containsKey(errorCode)) {
            errorCerts.put(errorCode, new ArrayList<>());
        }
        if (!errorCerts.get(errorCode).contains(x509CertificateResult)) {
            errorCerts.get(errorCode).add(x509CertificateResult);
        }
        fill(x509CertificateResult.getMailAddress(), errorCode, sender);
    }

    private void fill(String address, EnumErrorCode errorCode, boolean sender) {
        if (!addressErrors.containsKey(address)) {
            addressErrors.put(address, new ArrayList<>());
        }
        if (!addressErrors.get(address).contains(errorCode)) {
            addressErrors.get(address).add(errorCode);
        }

        if (sender) {
            if (senderAddress == null) {
                senderAddress = address;
            }
        } else {
            if (!rcptAddresses.contains(address)) {
                rcptAddresses.add(address);
            }
        }
    }

    public boolean isError(String address) {
        return addressErrors.containsKey(address) && !addressErrors.get(address).isEmpty();
    }
}
