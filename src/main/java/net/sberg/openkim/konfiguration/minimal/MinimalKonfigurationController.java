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

import net.sberg.openkim.common.AbstractWebController;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konnektor.Konnektor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
public class MinimalKonfigurationController extends AbstractWebController {

    private static final Logger log = LoggerFactory.getLogger(MinimalKonfigurationController.class);

    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/minimalkonfiguration/lade", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String lade(Model model) throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        MinimalKonfiguration minimalKonfiguration = new MinimalKonfiguration();

        minimalKonfiguration.setMandantId(konfiguration.getMandantId());
        minimalKonfiguration.setClientSystemId(konfiguration.getClientSystemId());
        minimalKonfiguration.setWorkplaceId(konfiguration.getWorkplaceId());
        minimalKonfiguration.setKonnektorCount(konfiguration.getKonnektoren().size());

        if (!konfiguration.getKonnektoren().isEmpty() && konfiguration.getKonnektoren().size() == 1) {
            Konnektor konnektor = konfiguration.getKonnektoren().get(0);
            minimalKonfiguration.setKonnektorUuid(konnektor.getUuid());
            minimalKonfiguration.setKonnektorIp(konnektor.getIp());
            minimalKonfiguration.setKonnektorName(konnektor.getName());
            minimalKonfiguration.setKonnektorSdsUrl(konnektor.getSdsUrl());
            minimalKonfiguration.setKonnektorAuthMethod(konnektor.getKonnektorAuthMethod());
            minimalKonfiguration.setKonnektorBasicAuthUser(konnektor.getBasicAuthUser());
            minimalKonfiguration.setKonnektorBasicAuthPwd(konnektor.getBasicAuthPwd());
            minimalKonfiguration.setKonnektorClientCertFilename(konnektor.getClientCertFilename());
            minimalKonfiguration.setKonnektorClientCertAuthPwd(konnektor.getClientCertAuthPwd());
        } else if (konfiguration.getKonnektoren().isEmpty()) {
            minimalKonfiguration.setKonnektorUuid(UUID.randomUUID().toString());
        }

        model.addAttribute("konfig", minimalKonfiguration);
        return "minimalkonfiguration/minimalKonfigFormular";
    }

    @RequestMapping(value = "/minimalkonfiguration/speichern", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String speichern(
        @ModelAttribute MinimalKonfiguration konfiguration,
        @RequestParam(name = "konnektorClientCertFile", required = false) MultipartFile konnektorClientCertFile) throws Exception {

        konfiguration.setKonnektorClientCertFile(konnektorClientCertFile);
        konfigurationService.speichern(konfiguration);
        return "ok";
    }

}
