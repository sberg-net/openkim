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

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konnektor.webservice.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.bean.CardTerminalWebserviceBean;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.bean.CardWebserviceBean;
import net.sberg.openkim.pipeline.operation.konnektor.webservice.bean.WebserviceBean;
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
    private CardTerminalService cardTerminalService;
    @Autowired
    private CardService cardService;
    @Autowired
    private CertificateService certificateService;
    @Autowired
    private EventService eventService;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    private SignatureService signatureService;
    @Autowired
    private LogService logService;

    @RequestMapping(value = "/konnwebservice/uebersicht/{konnId}/{wsId}", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String konnwebserviceUebersicht(Model model, @PathVariable String konnId, @PathVariable String wsId) throws Exception {
        model.addAttribute("konnId", konnId);
        model.addAttribute("wsId", wsId);
        Konnektor konnektor = konfigurationService.getKonnektor(konnId, true);
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

        WebserviceBean webserviceBean = new ObjectMapper().convertValue(serviceBean, WebserviceBean.class);

        Konnektor konnektor = konfigurationService.getKonnektor(webserviceBean.getKonnId(), true);
        model.addAttribute("konnektor", konnektor);

        KonnektorServiceBean konnektorServiceBean = konfigurationService.getKonnektorServiceBean(webserviceBean.getKonnId(), webserviceBean.getWsId(), true);

        if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CardService)
            && webserviceBean.getOpId().equals(CardService.OP_VERIFY_PIN)
        ) {
            CardWebserviceBean cardWebserviceBean = new ObjectMapper().convertValue(serviceBean, CardWebserviceBean.class);
            model.addAttribute("cardWebserviceBean", cardWebserviceBean);
            return "konnwebservice/verifyPinUebersicht";
        }
        throw new IllegalStateException(
            "webservice for "
            + webserviceBean.getKonnId()
            + " - "
            + webserviceBean.getWsId()
            + " - "
            + webserviceBean.getOpId()
            + " not supported"
        );
    }

    @RequestMapping(value = "/konnwebservice/ausfuehren", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String ausfuehren(@RequestBody Map serviceBean) throws Exception {

        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        WebserviceBean webserviceBean = new ObjectMapper().convertValue(serviceBean, WebserviceBean.class);

        KonnektorServiceBean konnektorServiceBean = konfigurationService.getKonnektorServiceBean(webserviceBean.getKonnId(), webserviceBean.getWsId(), true);
        Konnektor konnektor = konfigurationService.getKonnektor(webserviceBean.getKonnId(), true);

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(
            defaultLoggerContext.buildHtmlMode(true).buildKonnektor(konnektor).buildMandantId(konfiguration.getMandantId())
                .buildClientSystemId(konfiguration.getClientSystemId()).buildWorkplaceId(konfiguration.getWorkplaceId())
                .buildKonfiguration(konfiguration)
        );

        if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CardTerminalService)) {
            CardTerminalWebserviceBean cardTerminalWebserviceBean = new ObjectMapper().convertValue(serviceBean, CardTerminalWebserviceBean.class);
            return cardTerminalService.execute(logger, cardTerminalWebserviceBean);
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CardService)) {
            CardWebserviceBean cardWebserviceBean = new ObjectMapper().convertValue(serviceBean, CardWebserviceBean.class);
            return cardService.execute(logger, cardWebserviceBean);
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.CertificateService)) {
            return certificateService.execute(logger, webserviceBean, serviceBean);
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.EventService)) {
            return eventService.execute(logger, webserviceBean, serviceBean);
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.EncryptionService)) {
            return encryptionService.execute(logger, webserviceBean, serviceBean);
        } else if (konnektorServiceBean.getEnumKonnektorServiceBeanType().equals(EnumKonnektorServiceBeanType.SignatureService)) {
            return signatureService.execute(logger, webserviceBean, serviceBean);
        }
        logService.removeLogger(logger.getId());
        return "ok";
    }
}
