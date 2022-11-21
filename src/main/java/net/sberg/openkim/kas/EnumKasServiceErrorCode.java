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
package net.sberg.openkim.kas;

public enum EnumKasServiceErrorCode {
    technical("Technischer Fehler"),
    notAttachmentSizeExceded("Größe der Mail ohne Attachments zu groß"),
    kasQuotaReached("Quota-Überschreitung auf dem Kas-Server für den Account"),
    readAttachmentFromMail("Fehler beim Lesen des Attachments von der Mail"),
    encryptAttachment("Fehler beim Verschlüsseln des Anhangs"),
    sendAttachment("Fehler beim Senden des Anhangs"),
    sendAttachmentBadRequest("Fehler beim Senden des Anhangs - BAD_REQUEST"),
    sendAttachmentUnauthorized("Fehler beim Senden des Anhangs - UNAUTHORIZED"),
    sendAttachmentPayloadTooLarge("Fehler beim Senden des Anhangs - PAYLOAD_TOO_LARGE"),
    sendAttachmentInternalServerError("Fehler beim Senden des Anhangs - INTERNAL_SERVER_ERROR"),
    sendAttachmentInsufficientStorage("Fehler beim Senden des Anhangs - INSUFFICIENT_STORAGE"),
    hashPlainAttachment("Fehler beim Hashen des Plain-Anhangs"),
    creatingXkasMimebodypart("Fehler beim Erstellen des x-kas MimeBodyParts"),
    readAttachment("Fehler beim Herunterladen des Anhangs"),
    readAttachmentForbidden("Fehler beim Herunterladen des Anhangs - FORBIDDEN"),
    readAttachmentNotFound("Fehler beim Herunterladen des Anhangs - NOT_FOUND"),
    readAttachmentTooManyRequests("Fehler beim Herunterladen des Anhangs - TOO_MANY_REQUESTS"),
    readAttachmentInternalServerError("Fehler beim Herunterladen des Anhangs - INTERNAL_SERVER_ERROR"),
    creatingOriginalMimebodypart("Fehler beim Erstellen des originalen MimeBodyParts"),
    decryptAttachment("Fehler beim Entschlüsseln des Anhangs"),
    checkHashPlainAttachment("Fehler beim Checken und Vergleichen des Hashwertes des Plain-Anhangs"),
    unknown("unbekannt");

    private final String hrText;

    EnumKasServiceErrorCode(String hrText) {
        this.hrText = hrText;
    }

    public String getHrText() {
        return hrText;
    }
}
