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
package net.sberg.openkim.konfiguration.minimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import net.sberg.openkim.konnektor.EnumKonnektorAuthMethod;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MinimalKonfiguration {

    private String mandantId;
    private String clientSystemId;
    private String workplaceId;

    private int konnektorCount;
    private String konnektorUuid;
    private String konnektorIp;
    private String konnektorName = "unbekannt";
    private String konnektorSdsUrl;
    private EnumKonnektorAuthMethod konnektorAuthMethod = EnumKonnektorAuthMethod.UNKNOWN;
    private String konnektorBasicAuthUser;
    private String konnektorBasicAuthPwd;
    private String konnektorClientCertAuthPwd;
    private String konnektorClientCertFilename;
    @JsonIgnore
    private MultipartFile konnektorClientCertFile;

    @JsonIgnore
    public boolean isComplete() {

        if (mandantId == null || mandantId.trim().isEmpty()) {
            return false;
        }
        if (clientSystemId == null || clientSystemId.trim().isEmpty()) {
            return false;
        }
        if (workplaceId == null || workplaceId.trim().isEmpty()) {
            return false;
        }

        if (konnektorUuid == null || konnektorUuid.trim().isEmpty()) {
            return false;
        }
        if (konnektorIp == null || konnektorIp.trim().isEmpty()) {
            return false;
        }
        if (konnektorName == null || konnektorName.trim().isEmpty()) {
            return false;
        }
        if (konnektorSdsUrl == null || konnektorSdsUrl.trim().isEmpty()) {
            return false;
        }

        if (konnektorAuthMethod.equals(EnumKonnektorAuthMethod.UNKNOWN)) {
            return false;
        }
        if (konnektorAuthMethod.equals(EnumKonnektorAuthMethod.BASICAUTH)) {
            if (konnektorBasicAuthUser == null || konnektorBasicAuthUser.trim().isEmpty()) {
                return false;
            }
            if (konnektorBasicAuthPwd == null || konnektorBasicAuthPwd.trim().isEmpty()) {
                return false;
            }
        }
        if (konnektorAuthMethod.equals(EnumKonnektorAuthMethod.CERT)) {
            if (konnektorClientCertAuthPwd == null || konnektorClientCertAuthPwd.trim().isEmpty()) {
                return false;
            }
            return konnektorClientCertFilename != null && !konnektorClientCertFilename.trim().isEmpty();
        }
        return true;
    }

}
