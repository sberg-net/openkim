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
package net.sberg.openkim.testtool;

import net.sberg.openkim.mail.MailService;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.konnektor.vzd.VzdService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.mail.MailUtils;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

@Controller
public class SignEncryptController {

    private static final Logger log = LoggerFactory.getLogger(SignEncryptController.class);

    @Autowired
    private MailService mailService;
    @Autowired
    private VzdService vzdService;
    @Autowired
    private LogService logService;
    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/signencrypt/execute", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    public String execute(
        Model model,
        @RequestParam(name = "id", required = false) String id,
        @RequestParam(name = "konnektorId", required = false) String konnektorId,
        @RequestParam(name = "signEncryptExecute", required = false) boolean signEncryptExecute,
        @RequestParam(name = "mailFilename", required = false) String mailFilename,
        @RequestParam(name = "mailContent", required = false) String mailContent,
        @RequestParam(name = "mailSignedContent", required = false) String mailSignedContent,
        @RequestParam(name = "mailEncryptedContent", required = false) String mailEncryptedContent,
        @RequestParam(name = "mailFile", required = false) MultipartFile mailFile
    ) throws Exception {


        SignEncryptResult signEncryptResult = new SignEncryptResult();
        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        if (konfiguration.getKonnektoren().isEmpty()) {
            throw new IllegalStateException("Keine Konnektoren konfiguriert");
        }

        Konnektor konnektor = null;
        if (konnektorId != null) {
            konnektor = konfigurationService.getKonnektor(konnektorId, true);
            signEncryptResult.setKonnektorId(konnektor.getUuid());
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
            timeMetric = metricFactory.timer("SignEncryptController:execute");

            model.addAttribute("konnektoren", konfiguration.getKonnektoren());

            signEncryptResult.setMailFilename(mailFilename);

            MimeMessage originMessage = null;

            if (id == null || id.trim().isEmpty()) {
                signEncryptResult.setId(UUID.randomUUID().toString());
            } else {
                signEncryptResult.setId(id);
            }
            if (mailContent != null && !mailContent.trim().isEmpty()) {
                InputStream is = new ByteArrayInputStream(mailContent.getBytes(StandardCharsets.UTF_8));
                originMessage = new MimeMessage(Session.getInstance(new Properties()), is);
                signEncryptResult.setMailContent(mailContent);
            }
            if (mailSignedContent != null && !mailSignedContent.trim().isEmpty()) {
                signEncryptResult.setMailSignedContent(mailSignedContent);
            }
            if (mailEncryptedContent != null && !mailEncryptedContent.trim().isEmpty()) {
                signEncryptResult.setMailEncryptedContent(mailEncryptedContent);
            }

            if (mailFile != null) {
                signEncryptResult.setMailFilename(mailFile.getOriginalFilename());
                originMessage = new MimeMessage(Session.getInstance(new Properties()), mailFile.getInputStream());
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                originMessage.writeTo(byteArrayOutputStream);
                signEncryptResult.setMailContent(byteArrayOutputStream.toString(StandardCharsets.UTF_8));
            }

            if (signEncryptExecute) {
                String signCardHandle = mailService.getSignCardHandle(logger, true);
                List<String> recipients = MailUtils.getAddresses(originMessage, true);
                List<X509CertificateResult> certs = vzdService.loadCerts(logger, recipients, false, true);
                byte[] signedBytes = mailService.sign(logger, originMessage, signCardHandle, certs, true);
                signEncryptResult.setMailSignedContent(new String(signedBytes, StandardCharsets.UTF_8));

                byte[] encryptedBytes = mailService.encrypt(logger, certs, signedBytes, true);
                byte[] composedBytes = mailService.composeEncryptedMsg(logger, encryptedBytes, originMessage, certs, true, true);

                signEncryptResult.setMailEncryptedContent(new String(composedBytes, StandardCharsets.UTF_8));
            }

            model.addAttribute("fehler", false);
        } catch (Exception e) {
            log.error("error on signencrypt testtool", e);
            model.addAttribute("fehler", true);
        }

        timeMetric.stopAndPublish();
        model.addAttribute("result", signEncryptResult);
        model.addAttribute("logs", logger.getLogContentAsStr());

        logService.removeLogger(logger.getId());

        return "signencrypt/signencryptFormular";
    }
}
