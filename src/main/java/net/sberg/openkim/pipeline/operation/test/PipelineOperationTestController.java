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
package net.sberg.openkim.pipeline.operation.test;

import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

@Controller
public class PipelineOperationTestController {

    private static final Logger log = LoggerFactory.getLogger(PipelineOperationTestController.class);

    @Autowired
    private LogService logService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/pipelineoperationtest/uebersicht", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String uebersicht(Model model) throws Exception {
        model.addAttribute("ops", pipelineService.getTestableOperations());
        return "pipelineoperationtest/pipelineoperationtestFormular";
    }

    @RequestMapping(value = "/pipelineoperationtest/lade/{opId}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String ladeOpIdForm(Model model, @PathVariable String opId) throws Exception {
        model.addAttribute("konnektoren", konfigurationService.getKonfiguration().getKonnektoren());
        model.addAttribute("opId", opId);
        return "pipelineoperationtest/"+opId.replaceAll("\\.","_")+"_Formular";
    }

    @RequestMapping(value = "/pipelineoperationtest/execute", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String execute(MultipartHttpServletRequest multipartRequest) throws Exception {
        Map<String, String[]> multipartRequestParams = multipartRequest.getParameterMap();
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        Konnektor konnektor = null;
        if (multipartRequestParams.containsKey("konnektorId")) {
            konnektor = konfiguration.extractKonnektor(multipartRequestParams.get("konnektorId")[0], false);
        }
        if (konnektor == null) {
            if (!multipartRequestParams.containsKey("withKonnektor") || Boolean.valueOf(multipartRequestParams.get("withKonnektor")[0])) {
                return "Konnektor kann nicht gefunden werden";
            }
        }
        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(
            defaultLoggerContext
                .buildHtmlMode(true)
                .buildKonnektor(konnektor)
                .buildMandantId(konfiguration.getMandantId())
                .buildClientSystemId(konfiguration.getClientSystemId())
                .buildWorkplaceId(konfiguration.getWorkplaceId())
                .buildKonfiguration(konfiguration)
        );
        IPipelineOperation pipelineOperation = pipelineService.getOperation(multipartRequestParams.get("opId")[0]);
        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
        multipartRequestParams.keySet().stream().forEach(key -> {
            defaultPipelineOperationContext.setEnvironmentValue(pipelineOperation.getName(), key, multipartRequestParams.get(key)[0]);
        });

        pipelineOperation.execute(
            defaultPipelineOperationContext,
            pipelineOperation.getDefaultOkConsumer(),
            pipelineOperation.getDefaultFailConsumer()
        );

        logService.removeLogger(logger.getId());
        return logger.getLogContentAsStr();
    }
}
