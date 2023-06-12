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
package net.sberg.openkim.gateway;

import net.sberg.openkim.common.AbstractWebController;
import net.sberg.openkim.gateway.pop3.Pop3Gateway;
import net.sberg.openkim.gateway.smtp.SmtpGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class GatewayKeystoreController extends AbstractWebController {

    private static final Logger log = LoggerFactory.getLogger(GatewayKeystoreController.class);

    @Autowired
    private GatewayKeystoreService gatewayKeystoreService;
    @Autowired
    private SmtpGateway smtpGateway;
    @Autowired
    private Pop3Gateway pop3Gateway;

    @RequestMapping(value = "/openkimkeystore/loeschen", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String delete() throws Exception {
        gatewayKeystoreService.delete();
        smtpGateway.restart();
        pop3Gateway.restart();
        return "";
    }

    @RequestMapping(value = "/openkimkeystore/erstelle/selfsigned", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String createSelfsigned() throws Exception {
        gatewayKeystoreService.createSelfSigned();
        smtpGateway.restart();
        pop3Gateway.restart();
        return "ok";
    }

    @RequestMapping(value = "/openkimkeystore/erstelle/notselfsigned", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String createNotSelfsigned(Model model) throws Exception {
        model.addAttribute("keystoreData", new GatewayKeystoreData());
        return "konfiguration/notselfsignedFormular";
    }

    @RequestMapping(value = "/openkimkeystore/erstelle/notselfsigned", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public String create(@RequestBody GatewayKeystoreData eldixSmtpKeystoreData) throws Exception {
        gatewayKeystoreService.create(eldixSmtpKeystoreData);
        smtpGateway.restart();
        pop3Gateway.restart();
        return "ok";
    }
}
