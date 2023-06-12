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
package net.sberg.openkim.fachdienst;

public enum EnumFachdienst {
    ARVATO(
        "Arvato",
        "mail.arv.kim.telematik",
        "arv.kim.telematik"
    ),
    TSYSTEMS(
        "T-Systems",
        "lb-mail.tsi.kim.telematik",
        "tsi.kim.telematik"
    ),
    CGM(
        "CGM",
        "mail.tm.kim.telematik",
        "tm.kim.telematik"
    ),
    IBM(
        "IBM",
        "mail.ibm.kim.telematik",
        "ibm.kim.telematik"
    ),
    RISE(
        "RISE",
        "mail.bitmarck.kim.telematik",
        "bitmarck.kim.telematik"
    ),
    AKQUINET(
        "Akquinet",
        "mail.akquinet.kim.telematik",
        "akquinet.kim.telematik"
    );

    private final String name;
    private final String domain;
    private final String domainSuffix;

    EnumFachdienst(
        String name,
        String domain,
        String domainSuffix
    ) {
        this.name = name;

        this.domain = domain;
        this.domainSuffix = domainSuffix;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public String getDomainSuffix() {
        return domainSuffix;
    }
}
