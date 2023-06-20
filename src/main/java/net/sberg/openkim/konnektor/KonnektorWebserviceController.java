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
import net.sberg.openkim.pipeline.operation.konnektor.webservice.VerifyPinOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class KonnektorWebserviceController {

    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private LogService logService;

    @RequestMapping(value = "/konnwebservice/uebersicht/{konnId}/{wsId}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String konnwebserviceUebersicht(Model model, @PathVariable String konnId, @PathVariable String wsId) throws Exception {
        model.addAttribute(DefaultPipelineOperationContext.ENV_KONNEKTOR_ID, konnId);
        model.addAttribute(DefaultPipelineOperationContext.ENV_WEBSERVICE_ID, wsId);
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        Konnektor konnektor = konfiguration.extractKonnektor(konnId, true);
        model.addAttribute("konnektor", konnektor);
        KonnektorServiceBean konnektorServiceBean = konfigurationService.getKonnektorServiceBean(konnId, wsId, true);

        if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CardTerminalService)) {
            return "konnwebservice/cardTerminalUebersicht";
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CardService)) {
            return "konnwebservice/cardUebersicht";
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CertificateService)) {
            return "konnwebservice/certificateUebersicht";
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.EventService)) {
            return "konnwebservice/eventUebersicht";
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.EncryptionService)) {
            return "konnwebservice/encryptionUebersicht";
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.SignatureService)) {
            return "konnwebservice/signatureUebersicht";
        }
        throw new IllegalStateException("webservice for " + konnId + " - " + wsId + " not supported");
    }

    @RequestMapping(value = "/konnwebservice/uebersicht", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public String konnwebserviceUebersicht(Model model, @RequestBody Map serviceBean) throws Exception {

        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        Konnektor konnektor = konfiguration.extractKonnektor((String)serviceBean.get(DefaultPipelineOperationContext.ENV_KONNEKTOR_ID), true);
        model.addAttribute("konnektor", konnektor);

        KonnektorServiceBean konnektorServiceBean = konfigurationService.getKonnektorServiceBean((String)serviceBean.get(DefaultPipelineOperationContext.ENV_KONNEKTOR_ID), (String)serviceBean.get(DefaultPipelineOperationContext.ENV_WEBSERVICE_ID), true);

        if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CardService)
            && pipelineService.getOperationClass((String)serviceBean.get(DefaultPipelineOperationContext.ENV_OP_ID)).equals(VerifyPinOperation.class)
        ) {
            model.addAttribute("serviceBean", serviceBean);
            return "konnwebservice/verifyPinUebersicht";
        }
        throw new IllegalStateException(
            "webservice for "
            + (String)serviceBean.get(DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
            + " - "
            + (String)serviceBean.get(DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
            + " - "
            + (String)serviceBean.get(DefaultPipelineOperationContext.ENV_OP_ID)
            + " not supported"
        );
    }

    @RequestMapping(value = "/konnwebservice/ausfuehren", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String ausfuehren(@RequestBody Map serviceBean) throws Exception {

        Konfiguration konfiguration = konfigurationService.getKonfiguration();

        KonnektorServiceBean konnektorServiceBean = konfigurationService.getKonnektorServiceBean((String)serviceBean.get(DefaultPipelineOperationContext.ENV_KONNEKTOR_ID), (String)serviceBean.get(DefaultPipelineOperationContext.ENV_WEBSERVICE_ID), true);
        Konnektor konnektor = konfiguration.extractKonnektor((String)serviceBean.get(DefaultPipelineOperationContext.ENV_KONNEKTOR_ID), true);

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(
            defaultLoggerContext.buildHtmlMode(true).buildKonnektor(konnektor).buildMandantId(konfiguration.getMandantId())
                .buildClientSystemId(konfiguration.getClientSystemId()).buildWorkplaceId(konfiguration.getWorkplaceId())
                .buildKonfiguration(konfiguration)
        );

        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext();
        defaultPipelineOperationContext.setLogger(logger);
        defaultPipelineOperationContext.setEnvironmentValues(serviceBean);

        IPipelineOperation operation = pipelineService.getOperation((String)serviceBean.get(DefaultPipelineOperationContext.ENV_OP_ID));
        operation.execute(
            defaultPipelineOperationContext,
            operation.getDefaultOkConsumer(),
            (context, e) -> {

            }
        );
        String result = logger.getLogContentAsStr();
        logService.removeLogger(logger.getId());
        return result;
    }
}
