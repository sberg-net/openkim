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
package net.sberg.openkim.konfiguration.konnektor;

import lombok.Data;

@Data
public class KonnektorServiceBean {
    private String id;
    private String version;
    private String endpoint;
    private String endpointTls;
    private EnumKonnektorServiceBeanType enumKonnektorServiceBeanType;
    private boolean alive;

    public String createClassPackageName() {
        return enumKonnektorServiceBeanType.getPackageName() + ".v" + version.replaceAll("\\.", "_");
    }

    public String createSoapAction(String operation) {
        //bsp.: "http://ws.gematik.de/conn/EventService/v7.2#Subscribe"
        String[] arr = version.split("\\.");
        return enumKonnektorServiceBeanType.getSoapActionPrefix()
               + "v"
               + arr[0]
               + "."
               + arr[1]
               + "#"
               + operation;
    }
}
