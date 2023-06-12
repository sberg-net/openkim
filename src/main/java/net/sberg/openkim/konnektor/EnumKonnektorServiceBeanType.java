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
package net.sberg.openkim.konnektor;

public enum EnumKonnektorServiceBeanType {
    EncryptionService("de.gematik.ws.conn.encryptionservice", "http://ws.gematik.de/conn/EncryptionService/"),
    SignatureService("de.gematik.ws.conn.signatureservice", "http://ws.gematik.de/conn/SignatureService/"),
    CardService("de.gematik.ws.conn.cardservice", "http://ws.gematik.de/conn/CardService/"),
    CardTerminalService("de.gematik.ws.conn.cardterminalservice", "http://ws.gematik.de/conn/CardTerminalService/"),
    AuthSignatureService("de.gematik.ws.conn.authsignatureservice", "http://ws.gematik.de/conn/SignatureService/"),
    CertificateService("de.gematik.ws.conn.certificateservice", "http://ws.gematik.de/conn/CertificateService/"),
    EventService("de.gematik.ws.conn.eventservice", "http://ws.gematik.de/conn/EventService/");

    private final String packageName;
    private final String soapActionPrefix;

    EnumKonnektorServiceBeanType(String packageName, String soapActionPrefix) {
        this.packageName = packageName;
        this.soapActionPrefix = soapActionPrefix;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSoapActionPrefix() {
        return soapActionPrefix;
    }
}
