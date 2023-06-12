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
package net.sberg.openkim.testtool;

import net.sberg.openkim.mail.MailService;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.vzd.VzdService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

@Controller
public class DecryptVerifyController {

    private static final Logger log = LoggerFactory.getLogger(DecryptVerifyController.class);

    @Autowired
    private MailService mailService;
    @Autowired
    private VzdService vzdService;
    @Autowired
    private LogService logService;
    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/decryptverify/execute", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    public String execute(
        Model model,
        @RequestParam(name = "id", required = false) String id,
        @RequestParam(name = "konnektorId", required = false) String konnektorId,
        @RequestParam(name = "decryptVerifyExecute", required = false) boolean decryptVerifyExecute,
        @RequestParam(name = "userMailAddress", required = false) String userMailAddress,
        @RequestParam(name = "mailFilename", required = false) String mailFilename,
        @RequestParam(name = "mailContent", required = false) String mailContent,
        @RequestParam(name = "mailVerifiedContent", required = false) String mailVerifiedContent,
        @RequestParam(name = "mailDecryptedContent", required = false) String mailDecryptedContent,
        @RequestParam(name = "mailFile", required = false) MultipartFile mailFile
    ) throws Exception {

        DecryptVerifyResult decryptVerifyResult = new DecryptVerifyResult();

        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        if (konfiguration.getKonnektoren().isEmpty()) {
            throw new IllegalStateException("Keine Konnektoren konfiguriert");
        }

        Konnektor konnektor = null;
        if (konnektorId != null) {
            konnektor = konfigurationService.getKonnektor(konnektorId, true);
            decryptVerifyResult.setKonnektorId(konnektor.getUuid());
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


        TimeMetric timeMetric = null;
        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("DecryptVerifyController:execute");

            model.addAttribute("konnektoren", konfiguration.getKonnektoren());

            decryptVerifyResult.setMailFilename(mailFilename);

            MimeMessage originMessage = null;

            if (id == null || id.trim().isEmpty()) {
                decryptVerifyResult.setId(UUID.randomUUID().toString());
            } else {
                decryptVerifyResult.setId(id);
            }
            if (mailContent != null && !mailContent.trim().isEmpty()) {
                decryptVerifyResult.setMailContent(mailContent);
            }
            if (mailDecryptedContent != null && !mailDecryptedContent.trim().isEmpty()) {
                decryptVerifyResult.setMailDecryptedContent(mailDecryptedContent);
            }

            if (mailFile != null) {
                decryptVerifyResult.setMailFilename(mailFile.getOriginalFilename());
                originMessage = new MimeMessage(Session.getInstance(new Properties()), mailFile.getInputStream());
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                originMessage.writeTo(byteArrayOutputStream);
                decryptVerifyResult.setMailContent(byteArrayOutputStream.toString(StandardCharsets.UTF_8));
            }

            if (decryptVerifyExecute) {
                byte[] decryptedMessage = mailService.decryptVerify(logger, userMailAddress, originMessage);
                decryptVerifyResult.setMailDecryptedContent(new String(decryptedMessage, StandardCharsets.UTF_8));
            }

            model.addAttribute("fehler", false);
        } catch (Exception e) {
            log.error("error on decryptverify testtool", e);
            model.addAttribute("fehler", true);
        }

        timeMetric.stopAndPublish();
        model.addAttribute("result", decryptVerifyResult);
        model.addAttribute("logs", logger.getLogContentAsStr());

        logService.removeLogger(logger.getId());

        return "decryptverify/decryptverifyFormular";
    }
}
