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

import lombok.Data;

@Data
public class KonnektorCard {
    private String uuid;
    private String pinStatus;
    private String cardTerminal;
    private String cardSlot;
    private String cardHandle;
    private String cardType;
    private String pinTyp;
    private String iccsn;
    private String telematikId;
    private String expiredAt;
    private String konnId;
    private String wsId;
    private String verifyPinOpId;
}
