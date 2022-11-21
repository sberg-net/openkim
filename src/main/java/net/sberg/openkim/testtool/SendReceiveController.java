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

import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.mail.EnumMailAuthMethod;
import net.sberg.openkim.common.mail.EnumMailConnectionSecurity;
import net.sberg.openkim.common.mail.MailService;
import net.sberg.openkim.common.mail.MailUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsResult;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsResultContainer;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsService;
import net.sberg.openkim.konfiguration.konnektor.vzd.VzdService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
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
import org.xbill.DNS.Type;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;

@Controller
public class SendReceiveController {

    private static final Logger log = LoggerFactory.getLogger(SendReceiveController.class);

    @Autowired
    private MailService mailService;
    @Autowired
    private LogService logService;
    @Autowired
    private VzdService vzdService;
    @Autowired
    private DnsService dnsService;
    @Autowired
    private KonfigurationService konfigurationService;

    @RequestMapping(value = "/sendreceive/execute", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    public String execute(
        Model model,
        @RequestParam(name = "id", required = false) String id,
        @RequestParam(name = "sendExecute", required = false) boolean sendExecute,
        @RequestParam(name = "receiveExecute", required = false) boolean receiveExecute,
        @RequestParam(name = "mailFilename", required = false) String mailFilename,
        @RequestParam(name = "konnektorId", required = false) String konnektorId,
        @RequestParam(name = "username", required = false) String username,
        @RequestParam(name = "password", required = false) String password,
        @RequestParam(name = "mailFile", required = false) MultipartFile mailFile
    ) throws Exception {

        SendReceiveResult sendReceiveResult = new SendReceiveResult();

        Konfiguration konfiguration = konfigurationService.getKonfiguration();
        if (konfiguration.getKonnektoren().isEmpty()) {
            throw new IllegalStateException("Keine Konnektoren konfiguriert");
        }

        Konnektor konnektor = null;
        if (konnektorId != null) {
            konnektor = konfigurationService.getKonnektor(konnektorId, true);
            sendReceiveResult.setKonnektorId(konnektor.getUuid());
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
            timeMetric = metricFactory.timer("SendReceiveController:execute");

            model.addAttribute("konnektoren", konfiguration.getKonnektoren());

            if (id == null || id.trim().isEmpty()) {
                sendReceiveResult.setId(UUID.randomUUID().toString());
            } else {
                sendReceiveResult.setId(id);
            }

            DnsResult dnsResult = null;
            if (sendExecute || receiveExecute) {
                logger.parseUsername(username);
                DnsResultContainer dnsResultContainer = dnsService.request(logger, logger.getDefaultLoggerContext().getMailServerHost(), Type.string(Type.A));
                if (!dnsResultContainer.isError() && dnsResultContainer.getResult().size() >= 1) {
                    dnsResult = dnsResultContainer.getResult().get(0);
                }
            }

            //send mail
            if (sendExecute && dnsResult != null) {
                sendReceiveResult.setMailFilename(mailFilename);
                MimeMessage originMessage = null;

                if (mailFile != null) {
                    sendReceiveResult.setMailFilename(mailFile.getOriginalFilename());
                    originMessage = new MimeMessage(Session.getInstance(new Properties()), mailFile.getInputStream());
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    originMessage.writeTo(byteArrayOutputStream);
                }

                List<String> recipients = mailService.getAddresses(originMessage, false);
                List<X509CertificateResult> recipientsCerts = vzdService.loadCerts(logger, recipients, false, true);

                String from = ((InternetAddress) originMessage.getFrom()[0]).getAddress();
                List<X509CertificateResult> fromCerts = vzdService.loadCerts(logger, new ArrayList<>(Collections.singletonList(from)), true, false);

                //sign encrypt mail
                byte[] signEncryptedBytes = mailService.signEncrypt(logger, originMessage, recipientsCerts, fromCerts, true);
                MimeMessage signEncryptedMessage = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(signEncryptedBytes));

                sendMail(
                    logger,
                    dnsResult,
                    password,
                    signEncryptedMessage,
                    recipients,
                    from
                );
            } else if (receiveExecute && dnsResult != null) {
                sendReceiveResult = readMail(logger, dnsResult, password, sendReceiveResult);
            }

            model.addAttribute("fehler", false);
        } catch (Exception e) {
            log.error("error on sendreceive testtool", e);
            model.addAttribute("fehler", true);
        }

        timeMetric.stopAndPublish();
        model.addAttribute("result", sendReceiveResult);
        model.addAttribute("logs", logger.getLogContentAsStr());

        logService.removeLogger(logger.getId());

        return "sendreceive/sendreceiveFormular";
    }

    private SendReceiveResult readMail(
        DefaultLogger logger,
        DnsResult dnsResult,
        String password,
        SendReceiveResult sendReceiveResult) throws Exception {
        Store store = null;
        Folder inbox = null;
        try {

            Konfiguration konfiguration = konfigurationService.getKonfiguration();
            Properties props = new Properties();
            Session pop3ClientSession = MailUtils.createPop3ClientSession(
                props,
                EnumMailConnectionSecurity.SSLTLS,
                EnumMailAuthMethod.NORMALPWD,
                dnsResult.getAddress(),
                "995",
                konfiguration
            );

            store = pop3ClientSession.getStore("pop3");
            store.connect(logger.getDefaultLoggerContext().getMailServerUsername(), password);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.getMessages();
            logger.logLine(messages.length + " mails readed");

            for (Message message : messages) {
                String[] headerTestnachricht = message.getHeader(MailService.X_KIM_TESTNACHRICHT);
                String[] headerTestid = message.getHeader(MailService.X_KIM_TESTID);
                if (headerTestnachricht != null
                    && headerTestnachricht.length > 0
                    && headerTestid != null
                    && headerTestid.length > 0
                ) {
                    logger.logLine(((MimeMessage) message).getMessageID() + " is a test message");
                    InternetAddress fromAddress = (InternetAddress) message.getFrom()[0];
                    byte[] decryptVerifiedMsg = mailService.decryptVerify(logger, fromAddress.getAddress(), (MimeMessage) message);
                    message.setFlag(Flags.Flag.DELETED, true);
                    sendReceiveResult.getReceivedMails().add(new String(decryptVerifiedMsg));
                } else {
                    logger.logLine(((MimeMessage) message).getMessageID() + " no test message");
                }
            }
        } catch (Exception e) {
            log.error("error on read messages", e);
            logger.logLine("error on read messages");
        } finally {
            try {
                if (inbox != null) {
                    inbox.close(true);
                }
            } catch (Exception e) {
                log.error("error on read messages: close inbox", e);
                logger.logLine("error on read messages: close inbox");
            }
            try {
                if (store != null) {
                    store.close();
                }
            } catch (Exception e) {
                log.error("error on read messages: close store", e);
                logger.logLine("error on read messages: close store");
            }
        }
        return sendReceiveResult;
    }

    private void sendMail(
        DefaultLogger logger,
        DnsResult dnsResult,
        String password,
        MimeMessage signEncryptedMessage,
        List<String> recipients,
        String from
    ) throws Exception {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        signEncryptedMessage.writeTo(byteArrayOutputStream);
        byteArrayOutputStream.close();

        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

        String certfileName = ICommonConstants.BASE_DIR + File.separator + konfiguration.getFachdienstCertFilename();
        char[] passCharArray = konfiguration.getFachdienstCertAuthPwd().toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(certfileName), passCharArray);

        SSLContext sslContext = new SSLContextBuilder().loadKeyMaterial(keyStore, passCharArray).loadTrustMaterial(new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                return true;
            }
        }).build();

        AuthenticatingSMTPClient client = new AuthenticatingSMTPClient(true, sslContext);
        client.connect(dnsResult.getAddress(), 465);
        boolean res = client.auth(AuthenticatingSMTPClient.AUTH_METHOD.LOGIN, logger.getDefaultLoggerContext().getMailServerUsername(), password);
        logger.logLine("smtp auth: " + res);
        if (res) {
            String content = byteArrayOutputStream.toString();
            String[] recs = new String[recipients.size()];
            recs = recipients.toArray(recs);
            res = client.sendSimpleMessage(from, recs, content);
            logger.logLine("smtp sent: " + res);
        }
    }
}
