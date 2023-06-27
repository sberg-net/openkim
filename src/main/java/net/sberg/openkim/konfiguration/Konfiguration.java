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
package net.sberg.openkim.konfiguration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.EnumMailConnectionSecurity;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.EnumKomLeVersion;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Konfiguration {

    private String gatewayHost;
    private EnumGatewayTIMode gatewayTIMode = EnumGatewayTIMode.FULLSTACK;
    private String smtpGatewayPort;
    private EnumMailConnectionSecurity smtpGatewayConnectionSec = EnumMailConnectionSecurity.SSLTLS;
    private String pop3GatewayPort;
    private EnumMailConnectionSecurity pop3GatewayConnectionSec = EnumMailConnectionSecurity.SSLTLS;
    private int smtpGatewayIdleTimeoutInSeconds = 300;
    private int pop3GatewayIdleTimeoutInSeconds = 300;
    private int smtpClientIdleTimeoutInSeconds = 300;
    private int pop3ClientIdleTimeoutInSeconds = 300;

    private boolean writeSmtpCmdLogFile = true;
    private boolean writePop3CmdLogFile = true;

    private int ttlEncCertInHours = 12;
    private int ttlEmailIccsnInDays = 30;
    private int ttlProtsInDays = 30;

    private int mailSizeLimitInMB = 15;
    private boolean logPersonalInformations = false;
    private boolean logKonnektorExecute = false;

    private String mandantId;
    private String clientSystemId;
    private String workplaceId;

    //Fachdienst konfigs
    private int fachdienstKasTimeOutInSeconds = 30;
    private String fachdienstCertAuthPwd;
    private String fachdienstCertFilename;
    @JsonIgnore
    private MultipartFile fachdienstCertFile;

    private String xkimCmVersion = "OpenKIM_0.15.1";
    private String xkimPtVersion = "1.5.0-2";
    private EnumKomLeVersion xkimPtShortVersion = EnumKomLeVersion.V1_5plus;

    private List<Konnektor> konnektoren = new ArrayList<>();

    public Konnektor extractKonnektor(String konnId, boolean throwException) throws Exception {
        for (Iterator<Konnektor> iterator = getKonnektoren().iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            if (konnektor.getUuid().equals(konnId)) {
                return konnektor;
            }
        }
        if (throwException) {
            throw new IllegalStateException("unknow konnektor for: " + konnId);
        }
        return null;
    }

    public void synchronize(Konfiguration dbKonfiguration) {
        if (gatewayHost == null) {
            setGatewayHost(dbKonfiguration.getGatewayHost());
        }
        if (smtpGatewayPort == null) {
            setSmtpGatewayPort(dbKonfiguration.getSmtpGatewayPort());
        }
        if (smtpGatewayConnectionSec == null) {
            setSmtpGatewayConnectionSec(dbKonfiguration.getSmtpGatewayConnectionSec());
        }
        if (pop3GatewayPort == null) {
            setPop3GatewayPort(dbKonfiguration.getPop3GatewayPort());
        }
        if (pop3GatewayConnectionSec == null) {
            setPop3GatewayConnectionSec(dbKonfiguration.getPop3GatewayConnectionSec());
        }

        setKonnektoren(dbKonfiguration.getKonnektoren());
    }

    public boolean checkContextInfo() {
        return
            getMandantId() != null && !getMandantId().trim().isEmpty() &&
            getWorkplaceId() != null && !getWorkplaceId().trim().isEmpty() &&
            getClientSystemId() != null && !getClientSystemId().trim().isEmpty();
    }

    public void encryptPwds(String encryptionKeys) throws Exception {
        if (fachdienstCertAuthPwd != null && !fachdienstCertAuthPwd.trim().isEmpty()) {
            fachdienstCertAuthPwd = StringUtils.xor(fachdienstCertAuthPwd, encryptionKeys.split(","));
            fachdienstCertAuthPwd = new String(Base64.getEncoder().encode(fachdienstCertAuthPwd.getBytes()));
        }
        for (Iterator<Konnektor> iterator = konnektoren.iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            konnektor.encryptPwds(encryptionKeys);
        }
    }

    public void decryptPwds(String encryptionKeys) throws Exception {
        if (fachdienstCertAuthPwd != null && !fachdienstCertAuthPwd.trim().isEmpty()) {
            try {
                fachdienstCertAuthPwd = new String(Base64.getDecoder().decode(fachdienstCertAuthPwd.getBytes()));
                fachdienstCertAuthPwd = StringUtils.xor(fachdienstCertAuthPwd, encryptionKeys.split(","));
            }
            catch (Exception e) {}
        }
        for (Iterator<Konnektor> iterator = konnektoren.iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            konnektor.decryptPwds(encryptionKeys);
        }
    }

}
