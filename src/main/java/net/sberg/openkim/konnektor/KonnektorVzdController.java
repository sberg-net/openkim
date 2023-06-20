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
import net.sberg.openkim.pipeline.operation.konnektor.vzd.EnumVzdErrorCode;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.SearchVzdOperation;
import net.sberg.openkim.pipeline.operation.konnektor.vzd.VzdResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(KonnektorVzdController.class);

    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private LogService logService;
    @Autowired
    private PipelineService pipelineService;

    @Value("${spring.ldap.base}")
    private String ldapBase;

    @RequestMapping(value = "/vzd/suchen/{konnektorId}/{resultWithCertificates}", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String suche(Model model, @PathVariable String konnektorId, @PathVariable boolean resultWithCertificates, String searchValue) throws Exception {

        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        Konnektor dbKonnektor = konfiguration.extractKonnektor(konnektorId, false);
        if (dbKonnektor == null) {
            throw new IllegalStateException("Die Konnektor-Konfiguration konnte nicht geladen werden mit der Id: " + konnektorId);
        }

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(defaultLoggerContext.buildHtmlMode(true).buildKonnektor(dbKonnektor));

        try {

            model.addAttribute("konnektor", dbKonnektor);
            model.addAttribute("fehler", false);

            if (searchValue != null && !searchValue.trim().isEmpty()) {

                IPipelineOperation vzdPipelineOperation = pipelineService.getOperation(IPipelineOperation.BUILTIN_VENDOR + "." + SearchVzdOperation.NAME);

                DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
                defaultPipelineOperationContext.setEnvironmentValue(SearchVzdOperation.NAME, SearchVzdOperation.ENV_VZD_SEARCH_BASE, ldapBase);
                defaultPipelineOperationContext.setEnvironmentValue(SearchVzdOperation.NAME, SearchVzdOperation.ENV_VZD_SEARCH_VALUE, searchValue);
                defaultPipelineOperationContext.setEnvironmentValue(SearchVzdOperation.NAME, SearchVzdOperation.ENV_VZD_RESULT_WITH_CERTIFICATES, resultWithCertificates);
                defaultPipelineOperationContext.setEnvironmentValue(SearchVzdOperation.NAME, SearchVzdOperation.ENV_VZD_ONLY_SEARCH_MAIL_ATTR, false);

                vzdPipelineOperation.execute(
                    defaultPipelineOperationContext,
                    context -> {
                        List eintraege = (List) defaultPipelineOperationContext.getEnvironmentValue(SearchVzdOperation.NAME, SearchVzdOperation.ENV_VZD_RESULT);
                        if (eintraege.size() == 1 && ((VzdResult) eintraege.get(0)).getErrorCode().equals(EnumVzdErrorCode.NOT_FOUND)) {
                            eintraege.clear();
                        }
                        model.addAttribute("eintraege", eintraege);
                    },
                    (context, e) -> {
                        log.error("error on searching vzd for the konnektor: " + dbKonnektor.getIp());
                        model.addAttribute("eintraege", new ArrayList<>());
                        model.addAttribute("fehler", true);
                    }
                );
            }
        } catch (Exception e) {
            model.addAttribute("fehler", true);
            model.addAttribute("eintraege", new ArrayList<>());
        }

        model.addAttribute("logs", logger.getLogContentAsStr());
        logService.removeLogger(logger.getId());

        return "konnvzd/vzdEintragUebersicht";
    }
}
