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

public enum EnumErrorCode {
    CODE_4001("4001", "Empfänger entfernt, wegen falscher KIM-Version"),
    CODE_4002("4002", "Anhang konnte nicht zum KOM-LE-Attachment-Service übertragen werden"),
    CODE_4003("4003", "keine eindeutige Telematik-ID mit Verschlüsselungszertifikat gefunden"),
    CODE_4004("4004", "Nachricht nicht für alle Empfänger verschlüsselbar"),
    CODE_4005("4005", "Für einen Empfänger existieren mehrere Verschlüsselungszertifikate mit unterschiedlichen Telematik-IDs"),
    CODE_4006("4006", "Anhang konnte nicht vom KOM-LE-Attachment-Service geladen werden"),
    CODE_4007("4007", "beim Entschlüsseln eines Anhangs ist ein Fehler aufgetreten"),
    CODE_4008("4008", "Das verwendete Client-Modul unterstützt die in der Mail verwendete Version nicht"),
    CODE_4009("4009", "Die KIM-Nachricht konnte auf Grund eines nicht verfügbaren Schlüssels nicht entschlüsselt werden"),
    CODE_4010("4010", "Die KIM-Nachricht konnte aufgrund des falschen Formats nicht entschlüsselt werden"),
    CODE_4011("4011", "Der Konnektor steht für die Entschlüsselung nicht zur Verfügung"),
    CODE_4012("4012", "Die Prüfsumme des Anhangs stimmt nicht mit der dem Anhang beigefügten Prüfsumme überein. Der empfangene Anhang entspricht eventuell nicht dem originalen Anhang"),
    CODE_4013("4013", "Anhang konnte nicht heruntergeladen werden, da durch zu häufigen Zugriff der KOM-LE-Attachment-Service den Abruf verweigert."),
    CODE_4014("4014", "Die Prüfung der Nachricht hat ergeben, dass die Nachricht nach dem Verschlüsseln manipuliert wurde. Möglicherweise " +
                      "wurde die verschlüsselte Nachricht auch an einen nicht empfangsberechtigten Personenkreis versendet."),
    CODE_4015("4015", "Die Prüfung der Signatur der Nachricht hat ergeben, dass die Nachricht manipuliert wurde, um einem anderen " +
                      "Nutzer das Entschlüsseln der Nachricht mit einem Schlüssel, der nicht in seinem Besitz ist, zu ermöglichen"),
    CODE_4016("4016", "Bei der Aktualisierung der PKCS#12-Datei ist ein Fehler aufgetreten"),
    CODE_4017("4017", "Die KIM-Version des Client-Moduls ist kleiner als die im Verzeichnisdienst zu seinem Eintrag hinterlegte Version"),
    CODE_4112("4112", "Die digitale Signatur konnte aufgrund des falschen Formats nicht geprüft werden"),
    CODE_4115("4115", "Die Integrität der Nachricht wurde verletzt"),
    CODE_4206("4206", "Der Zertifizierungspfad des Signaturzertifikats kann nicht validiert werden"),
    CODE_4253("4253", "Die digitale Signatur ist nicht vorhanden"),
    CODE_4264("4264", "Die digitale Signatur ist mathematisch korrekt, der Zertifikatsstatus des Signaturzertifikats konnte aber nicht geprüft werden"),
    CODE_X001("X001", "Die digitale Signatur ist mathematisch korrekt und der Zertifikatsstatus des Signaturzertifikats konnte erfolgreich " +
                      "geprüft werden, aber beim Vergleich der Header-Elemente from, sender, reply-to, to und cc der äußeren Nachricht mit denen der inneren Nachricht " +
                      "wurden Abweichungen festgestellt."),
    CODE_X002("X002", "Die digitale Signatur konnte aufgrund eines nicht zuordenbaren Fehlercodes des Konnektors nicht geprüft werden"),
    CODE_X003("X003", "Die digitale Signatur ist mathematisch korrekt und der Zertifikatsstatus des Signaturzertifikats " +
                      "konnte erfolgreich geprüft werden, aber das recipient-emails-Attribut aus signerInfos enthält nicht die gleichen Werte wie das " +
                      "recipient-emails-Attribut aus dem enveloped-data CMS-Objekt"),
    CODE_X004("X004", "Der Gematik-Verzeichnisdienst seht nicht zur Verfügung"),
    CODE_X005("X005", "Für einen Empfänger existiert kein Verschlüsselungszertifikat"),
    CODE_X006("X006", "Für den Absender existiert kein Verschlüsselungszertifikat"),
    CODE_X007("X007", "Für den Absender existieren mehrere Verschlüsselungszertifikate mit unterschiedlichen Telematik-IDs"),
    CODE_X008("X008", "Für den Absender ist eine höhere KIM-Version im Gematik-Verzeichnisdienst hinterlegt als die Version des KIM-Clientmoduls"),
    CODE_X009("X009", "Bei der Signierung der Mail ist ein Fehler aufgetreten"),
    CODE_X010("X010", "Fehler bei der Selektion der Karte zum Signieren. Entweder steht der Konnektor nicht zur Verfügung oder der PIN-STATUS der SMC-B ist nicht verifiziert"),
    CODE_X011("X011", "Bei der Verschlüsselung der Mail ist ein Fehler aufgetreten"),
    CODE_X012("X012", "Beim Fertigstellen der signierten und verschlüsselten Mail ist ein Fehler aufgetreten"),
    CODE_X013("X013", "Beim Überprüfen der zu versendenden Mail ist ein Fehler aufgetreten"),
    CODE_X014("X014", "Header X-KOM-LE-Version mit der entsprechenden Version (1.0 oder 1.5) nicht gesetzt"),
    CODE_X015("X015", "Subject nicht auf KOM-LE-Nachricht gesetzt"),
    CODE_X016("X016", "Content-Type nicht auf application/pkcs7-mime gesetzt"),
    CODE_X017("X017", "EnvelopedData im falschen Format, fehlerhafte OID: 1.2.840.113549.1.7.3 statt 1.2.840.113549.1.9.16.1.23"),
    CODE_X018("X018", "EncryptedRecipientInfos nicht verfügbar"),
    CODE_X019("X019", "EncryptedRecipientEmails nicht verfügbar"),
    CODE_X020("X020", "Beim Überprüfen des Encrypt-Formats der Mail ist ein unbekannter Fehler aufgetreten"),
    CODE_X021("X021", "Die Karte für das Entschlüsseln konnte nicht gefunden werden"),
    CODE_X022("X022", "CertIssuerAndSerialNumber in ContentInfo konnte für die Empfänger-Adresse nicht gefunden werden"),
    CODE_X023("X023", "Verschlüsselte Mail konnte nicht extrahiert werden und der signed Inhalt nicht geparst werden"),
    CODE_X024("X024", "Der SMTP-Befehl RCPT-TO für den Empfänger ist fehlgeschlagen");

    private final String hrText;
    private String id;

    EnumErrorCode(String id, String hrText) {
        this.hrText = hrText;
    }

    public String getHrText() {
        return hrText;
    }

    public String getId() {
        return id;
    }
}
