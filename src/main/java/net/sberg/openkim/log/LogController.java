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
package net.sberg.openkim.log;

import net.sberg.openkim.common.AbstractWebController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@Controller
public class LogController extends AbstractWebController {

    @Autowired
    private LogService logService;

    @RequestMapping(value = "/pop3log", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String entryPointPop3() throws Exception {
        return "log/pop3log";
    }

    @RequestMapping(value = "/smtplog", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String entryPointSmtp() throws Exception {
        return "log/smtplog";
    }

    @RequestMapping(value = "/log/uebersicht/{typ}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String uebersicht(Model model, @PathVariable EnumLogTyp typ) throws Exception {
        List<Log> logs = logService.lade(null, typ);
        logs.sort((log1, log2) -> -1 * log1.getGeaendert().compareTo(log2.getGeaendert()));
        model.addAttribute("logs", logs);
        model.addAttribute("typ", typ);
        return "log/logUebersicht";
    }

    @RequestMapping(value = "/log/uebersicht/{typ}/{logId}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String uebersicht4Id(Model model, @PathVariable EnumLogTyp typ, @PathVariable String logId) throws Exception {
        model.addAttribute("logs", logService.lade(logId, typ));
        model.addAttribute("typ", typ);
        return "log/logUebersicht";
    }

    @RequestMapping(value = "/log/lade/{typ}/{logId}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String lade(Model model, @PathVariable EnumLogTyp typ, @PathVariable String logId) throws Exception {
        String logs = logService.ladeInhalt(logId, typ);
        model.addAttribute("typ", typ);
        model.addAttribute("logId", logId);
        model.addAttribute("logs", logs);
        return "log/logDetails";
    }

}
