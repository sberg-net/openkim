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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import net.sberg.openkim.dashboard.KonnektorMonitoringResult;
import net.sberg.openkim.konfiguration.EnumTIEnvironment;
import net.sberg.openkim.konfiguration.ServerState;
import net.sberg.openkim.konfiguration.fachdienst.Fachdienst;
import net.sberg.openkim.konfiguration.konnektor.card.KonnektorCard;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Konnektor {
    private String uuid;
    private String ip;
    private EnumTIEnvironment tiEnvironment = EnumTIEnvironment.PU;
    private String name = "unbekannt";
    private String sdsUrl;
    private String productTypeVersion;
    private String hwVersion;
    private String fwVersion;
    private String productName;
    private String productType;
    private boolean activated = true;
    private int timeoutInSeconds = 60;
    private EnumKonnektorAuthMethod konnektorAuthMethod = EnumKonnektorAuthMethod.UNKNOWN;
    private String basicAuthUser;
    private String basicAuthPwd;
    private String clientCertAuthPwd;
    private String clientCertFilename;

    private boolean konnektorServiceBeansLoaded = false;

    @JsonIgnore
    private List<Fachdienst> fachdienste = new ArrayList<>();
    @JsonIgnore
    private boolean connectedWithTI;
    @JsonIgnore
    private boolean connectedWithSIS;
    @JsonIgnore
    private boolean eccEncryptionAvailable;
    @JsonIgnore
    private List<KonnektorCard> cards = new ArrayList<>();
    @JsonIgnore
    private List<KonnektorServiceBean> konnektorServiceBeans = new ArrayList<>();
    @JsonIgnore
    private ServerState vzdLdapServerState;
    @JsonIgnore
    private ServerState tlsPortServerState;
    @JsonIgnore
    private MultipartFile clientCertFile;
    @JsonIgnore
    private MultipartFile serverCertFile;
    @JsonIgnore
    private Date konnektorTime;
    @JsonIgnore
    private Date systemTime;
    @JsonIgnore
    private long diffSystemKonnektorTime;
    @JsonIgnore
    private KonnektorMonitoringResult konnektorMonitoringResult;

    public KonnektorServiceBean extractKonnektorServiceBean(EnumKonnektorServiceBeanType webServiceBeanType, boolean throwException) throws Exception {
        for (Iterator<KonnektorServiceBean> iterator = getKonnektorServiceBeans().iterator(); iterator.hasNext(); ) {
            KonnektorServiceBean konnektorServiceBean = iterator.next();
            if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(webServiceBeanType)) {
                return konnektorServiceBean;
            }
        }
        if (throwException) {
            throw new IllegalStateException("unknow webservice for: " + webServiceBeanType);
        }
        return null;
    }
}
