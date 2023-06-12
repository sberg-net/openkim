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

import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konnektor.vzd.VzdService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.EnumVzdErrorCode;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.VzdResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;
import java.util.List;

@Controller
public class KonnektorVzdController {

    @Autowired
    private VzdService vzdService;
    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private LogService logService;

    @RequestMapping(value = "/vzd/suchen/{konnektorId}/{resultWithCertificates}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String suche(Model model, @PathVariable String konnektorId, @PathVariable boolean resultWithCertificates, String searchValue) throws Exception {

        Konnektor dbKonnektor = konfigurationService.getKonnektor(konnektorId, false);
        if (dbKonnektor == null) {
            throw new IllegalStateException("Die Konnektor-Konfiguration konnte nicht geladen werden mit der Id: " + konnektorId);
        }

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(defaultLoggerContext.buildHtmlMode(true).buildKonnektor(dbKonnektor));

        try {

            model.addAttribute("konnektor", dbKonnektor);

            if (searchValue != null && !searchValue.trim().isEmpty()) {
                List eintraege = vzdService.search(logger, searchValue, false, resultWithCertificates);
                if (eintraege.size() == 1 && ((VzdResult) eintraege.get(0)).getErrorCode().equals(EnumVzdErrorCode.NOT_FOUND)) {
                    eintraege.clear();
                }
                model.addAttribute("eintraege", eintraege);
            } else {
                model.addAttribute("eintraege", new ArrayList<>());
            }

            model.addAttribute("fehler", false);
        } catch (Exception e) {
            model.addAttribute("fehler", true);
            model.addAttribute("eintraege", new ArrayList<>());
        }

        model.addAttribute("logs", logger.getLogContentAsStr());
        logService.removeLogger(logger.getId());

        return "konnvzd/vzdEintragUebersicht";
    }
}
