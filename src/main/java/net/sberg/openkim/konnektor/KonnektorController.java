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

import jakarta.servlet.http.HttpServletResponse;
import net.sberg.openkim.konfiguration.EnumTIEnvironment;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
public class KonnektorController {

    @Autowired
    private KonfigurationService konfigurationService;

    @Profile("dev")
    @RequestMapping(value = "/dev/konnektor/herunterladen/sdsdescr", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public void uebersichtCsvExport(HttpServletResponse response) throws Exception {
        response.setHeader("Content-Disposition", "inline; filename=test.xml");
        response.setContentType("text/xml");

        IOUtils.copy(getClass().getResourceAsStream("/test_sds_descr/connector_sds.xml"), response.getOutputStream());
        response.flushBuffer();
    }

    @RequestMapping(value = "/konnektor/lade/{uuid}/{refresh}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String lade(Model model, @PathVariable String uuid, @PathVariable boolean refresh) throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        model.addAttribute("contextInfoAvailable", konfiguration.checkContextInfo());
        model.addAttribute("konnektorContext",
            "Mandant = " + konfiguration.getMandantId()
            + " , Workplace = " + konfiguration.getWorkplaceId()
            + ", Clientsystem = " + konfiguration.getClientSystemId()
        );
        if (uuid.equals("-1")) {
            Konnektor konnektor = new Konnektor();
            konnektor.setUuid(UUID.randomUUID().toString());
            model.addAttribute("konnektor", konnektor);
        } else {
            Konnektor konnektor = konfigurationService.getKonnektor(uuid, false);
            if (konnektor != null) {
                if (refresh) {
                    konnektor = konfigurationService.executeKonnektor(konnektor);
                }
                model.addAttribute("konnektor", konnektor);
            } else {
                throw new IllegalStateException("Die Konnektor-Konfiguration konnte nicht geladen werden");
            }
        }
        return "konnektor/konnektorFormular";
    }

    @RequestMapping(value = "/konnektor/loeschen/{uuid}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String loeschen(@PathVariable String uuid) throws Exception {
        return konfigurationService.loeschenKonnektor(uuid);
    }

    @RequestMapping(value = "/konnektor/speichern", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String speichern(
        @RequestParam(name = "uuid") String uuid,
        @RequestParam(name = "ip") String ip,
        @RequestParam(name = "name") String name,
        @RequestParam(name = "sdsUrl") String sdsUrl,
        @RequestParam(name = "tiEnvironment") EnumTIEnvironment tiEnvironment,
        @RequestParam(name = "activated") boolean activated,
        @RequestParam(name = "timeout") int timeout,
        @RequestParam(name = "konnektorAuthMethod") EnumKonnektorAuthMethod konnektorAuthMethod,
        @RequestParam(name = "basicAuthUser", required = false) String basicAuthUser,
        @RequestParam(name = "basicAuthPwd", required = false) String basicAuthPwd,
        @RequestParam(name = "clientCertAuthPwd", required = false) String clientCertAuthPwd,
        @RequestParam(name = "clientCertFile", required = false) MultipartFile clientCertFile,
        @RequestParam(name = "serverCertFile", required = false) MultipartFile serverCertFile
    ) throws Exception {
        Konnektor konnektor = new Konnektor();
        konnektor.setIp(ip);
        konnektor.setUuid(uuid);
        konnektor.setName(name);
        konnektor.setActivated(activated);
        konnektor.setTiEnvironment(tiEnvironment);
        konnektor.setTimeoutInSeconds(timeout);
        konnektor.setSdsUrl(sdsUrl);
        konnektor.setKonnektorAuthMethod(konnektorAuthMethod);
        konnektor.setBasicAuthPwd(basicAuthPwd);
        konnektor.setBasicAuthUser(basicAuthUser);
        konnektor.setClientCertAuthPwd(clientCertAuthPwd);
        konnektor.setClientCertFile(clientCertFile);
        konnektor.setServerCertFile(serverCertFile);
        return konfigurationService.speichernKonnektor(konnektor);
    }
}
