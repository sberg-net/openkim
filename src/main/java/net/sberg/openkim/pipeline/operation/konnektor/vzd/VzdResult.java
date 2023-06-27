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

import lombok.Data;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class VzdResult {
    private String uid;
    private String cn;
    private String sn;
    private String displayName;
    private String givenName;
    private Map<String, VzdMailResult> mailResults = new HashMap<>();
    private String personalEntry;
    private String changeDateTime;
    private String countryCode;
    private String dataFromAuthority;
    private String domainID;
    private String entryType;
    private String l;
    private String organization;
    private String otherName;
    private String postalCode;
    private String professionOID;
    private String specialization;
    private String st;
    private String street;
    private String telematikID;
    private String title;
    private List<X509Certificate> certs = new ArrayList<>();
    private List<byte[]> certBytes = new ArrayList<>();
    private String certSummary;
    private EnumVzdErrorCode errorCode = EnumVzdErrorCode.OK;

    public String createMailStr() {
        if (mailResults.isEmpty()) {
            return "";
        }
        return mailResults.keySet().stream().map(s -> mailResults.get(s).toString()).collect(Collectors.joining(";"));
    }
}
