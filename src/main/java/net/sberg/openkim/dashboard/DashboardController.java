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
package net.sberg.openkim.dashboard;

import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/dashboard/uebersicht", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String execute(Model model) throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        List<KonnektorMonitoringResult> result = new ArrayList<>();
        try {
            for (Iterator<Konnektor> iterator = konfiguration.getKonnektoren().iterator(); iterator.hasNext(); ) {
                Konnektor konnektor = iterator.next();
                result.add(konnektor.getKonnektorMonitoringResult());
            }
            model.addAttribute("fehler", false);
        } catch (Exception e) {
            log.error("error on dashboard uebersicht", e);
            model.addAttribute("fehler", true);
        }
        model.addAttribute("result", result);
        return "dashboard/dashboardUebersicht";
    }

    @RequestMapping(value = "/dashboard/uebersicht/aktualisieren", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public boolean executeRefresh() throws Exception {
        konfigurationService.executeKonnektoren();
        return true;
    }

    @RequestMapping(value = "/api/dashboard/uebersicht", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public List apiExecute() throws Exception {
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        List<KonnektorMonitoringResult> result = new ArrayList<>();
        for (Iterator<Konnektor> iterator = konfiguration.getKonnektoren().iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            result.add(konnektor.getKonnektorMonitoringResult());
        }
        return result;
    }
}
