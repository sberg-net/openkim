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

import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.ntp.NtpRequestOperation;
import net.sberg.openkim.pipeline.operation.konnektor.ntp.NtpResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class KonnektorNtpController {

    @Autowired
    private LogService logService;
    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private PipelineService pipelineService;

    @RequestMapping(value = "/ntp/testen/{konnektorId}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String testen(Model model, @PathVariable String konnektorId) throws Exception {

        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        Konnektor dbKonnektor = konfiguration.extractKonnektor(konnektorId, true);

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(defaultLoggerContext.buildHtmlMode(true).buildKonnektor(dbKonnektor));

        try {

            model.addAttribute("konnektor", dbKonnektor);

            IPipelineOperation dnsPipelineOperation = pipelineService.getOperation(IPipelineOperation.BUILTIN_VENDOR+"."+ NtpRequestOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);

            dnsPipelineOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    NtpResult ntpResult = (NtpResult) context.getEnvironmentValue(NtpRequestOperation.NAME, NtpRequestOperation.ENV_NTP_RESULT);
                    String resultBuilder = "Zeit von " + dbKonnektor.getIp() + ": " + ntpResult.getKonnektorTime()
                            + "<br/>"
                            + "Zeit vom System : " + ntpResult.getSystemTime()
                            + "<br/>";

                    model.addAttribute("ergebnis", resultBuilder);
                    model.addAttribute("fehler", false);
                },
                (context, e) -> {
                    model.addAttribute("fehler", true);
                    model.addAttribute("ergebnis", "");
                }
            );
        } catch (Exception e) {
            model.addAttribute("fehler", true);
            model.addAttribute("ergebnis", "");
        }

        model.addAttribute("logs", logger.getLogContentAsStr());
        logService.removeLogger(logger.getId());

        return "konnntp/ntpUebersicht";
    }
}
