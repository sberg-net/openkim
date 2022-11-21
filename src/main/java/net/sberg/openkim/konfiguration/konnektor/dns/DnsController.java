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
package net.sberg.openkim.konfiguration.konnektor.dns;

import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;

@Controller
public class DnsController {

    @Autowired
    private DnsService dnsService;
    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private LogService logService;

    @RequestMapping(value = "/dns/testen/{konnektorId}/{recordType}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String suche(Model model, @PathVariable String konnektorId, @PathVariable String recordType, String domain) throws Exception {

        Konnektor dbKonnektor = konfigurationService.getKonnektor(konnektorId, false);
        if (dbKonnektor == null) {
            throw new IllegalStateException("Die Konnektor-Konfiguration konnte nicht geladen werden mit der Id: " + konnektorId);
        }

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(defaultLoggerContext.buildHtmlMode(true).buildKonnektor(dbKonnektor));

        try {
            model.addAttribute("konnektor", dbKonnektor);

            if (domain != null && !domain.trim().isEmpty()) {
                DnsResultContainer dnsResultContainer = dnsService.request(logger, domain, recordType);
                if (dnsResultContainer.isError()) {
                    model.addAttribute("fehler", true);
                    model.addAttribute("eintraege", new ArrayList<>());
                } else {
                    model.addAttribute("fehler", false);
                    model.addAttribute("eintraege", dnsResultContainer.getResult());
                }
            } else {
                model.addAttribute("fehler", false);
                model.addAttribute("eintraege", new ArrayList<>());
            }
        } catch (Exception e) {
            model.addAttribute("fehler", true);
            model.addAttribute("eintraege", new ArrayList<>());
        }

        model.addAttribute("logs", logger.getLogContentAsStr());
        logService.removeLogger(logger.getId());

        return "konndns/dnsEintragUebersicht";
    }
}
