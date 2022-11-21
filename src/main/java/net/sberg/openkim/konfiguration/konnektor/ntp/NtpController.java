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
package net.sberg.openkim.konfiguration.konnektor.ntp;

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

@Controller
public class NtpController {

    @Autowired
    private NtpService ntpService;
    @Autowired
    private LogService logService;
    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/ntp/testen/{konnektorId}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String testen(Model model, @PathVariable String konnektorId) throws Exception {

        Konnektor dbKonnektor = konfigurationService.getKonnektor(konnektorId, true);

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(defaultLoggerContext.buildHtmlMode(true).buildKonnektor(dbKonnektor));

        try {

            model.addAttribute("konnektor", dbKonnektor);

            NtpResult ntpResult = ntpService.request(logger, dbKonnektor);

            String resultBuilder = "Zeit von " + dbKonnektor.getIp() + ": " + ntpResult.getKonnektorTime()
                                   + "<br/>"
                                   + "Zeit vom System : " + ntpResult.getSystemTime()
                                   + "<br/>";

            model.addAttribute("ergebnis", resultBuilder);
            model.addAttribute("fehler", false);
        } catch (Exception e) {
            model.addAttribute("fehler", true);
            model.addAttribute("ergebnis", "");
        }

        model.addAttribute("logs", logger.getLogContentAsStr());
        logService.removeLogger(logger.getId());

        return "konnntp/ntpUebersicht";
    }
}
