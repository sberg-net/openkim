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
package net.sberg.openkim.konfiguration;

import net.sberg.openkim.common.AbstractWebController;
import net.sberg.openkim.gateway.GatewayKeystoreService;
import net.sberg.openkim.gateway.pop3.Pop3Gateway;
import net.sberg.openkim.gateway.smtp.SmtpGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class KonfigurationController extends AbstractWebController {

    private static final Logger log = LoggerFactory.getLogger(KonfigurationController.class);

    @Autowired
    private GatewayKeystoreService gatewayKeystoreService;
    @Autowired
    private SmtpGateway smtpGateway;
    @Autowired
    private Pop3Gateway pop3Gateway;
    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private ServerStateService serverStateService;

    @RequestMapping(value = "/konfiguration/init/dev", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String init() throws Exception {
        String res = konfigurationService.init();

        try {
            smtpGateway.restart();
            pop3Gateway.restart();
        } catch (Exception e) {
            log.error("error on restarting the smtp,pop3 gateway", e);
        }

        return res;
    }

    @RequestMapping(value = "/konfiguration/uebersicht", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String uebersicht(Model model) throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        model.addAttribute("konfig", konfiguration);
        return "konfiguration/konfigUebersicht";
    }

    @RequestMapping(value = "/konfiguration/serverstatus", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String serverstatus(Model model) throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        model.addAttribute("serverStatus", serverStateService.check(konfiguration, smtpGateway.isStartSucces(), pop3Gateway.isStartSucces()));
        model.addAttribute("openkimKeystoreExistiert", gatewayKeystoreService.keystoreAvailable());
        return "konfiguration/konfigServerStatus";
    }

    @RequestMapping(value = "/konfiguration/lade", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String lade(Model model) throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        model.addAttribute("konfig", konfiguration);
        return "konfiguration/konfigFormular";
    }

    @RequestMapping(value = "/konfiguration/speichern", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String speichern(
        @ModelAttribute Konfiguration konfiguration,
        @RequestParam(name = "fachdienstCertFile", required = false) MultipartFile fachdienstCertFile
    ) throws Exception {

        konfiguration.setFachdienstCertFile(fachdienstCertFile);
        konfigurationService.speichern(konfiguration);

        try {
            smtpGateway.restart();
            pop3Gateway.restart();
        } catch (Exception e) {
            log.error("error on restarting the smtp,pop3 gateway", e);
        }

        return "ok";
    }

}
