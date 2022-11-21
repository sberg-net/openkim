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
package net.sberg.openkim.konfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.mail.EnumMailConnectionSecurity;
import net.sberg.openkim.konfiguration.konnektor.EnumKonnektorAuthMethod;
import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import net.sberg.openkim.konfiguration.konnektor.KonnektorService;
import net.sberg.openkim.konfiguration.konnektor.KonnektorServiceBean;
import net.sberg.openkim.konfiguration.minimal.MinimalKonfiguration;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.Iterator;

@Service
public class KonfigurationService {

    private static final Logger log = LoggerFactory.getLogger(KonfigurationService.class);

    private Konfiguration konfiguration;

    private static final Object mutex = new Object();

    public static final String FACHDIENST_CERT_FILENAME = "fachdienst.p12";

    @Autowired
    private KonnektorService konnektorService;

    @Autowired
    private LogService logService;

    @Value("${konfiguration.gatewayHostDefaultWert}")
    private String gatewayHostDefaultWert;

    @Value("${konfiguration.smtpGatewayPortDefaultWert}")
    private String smtpGatewayPortDefaultWert;

    @Value("${konfiguration.pop3GatewayPortDefaultWert}")
    private String pop3GatewayPortDefaultWert;

    @PostConstruct
    public void construct() throws Exception {
        synchronized (mutex) {
            if (!new File(ICommonConstants.BASE_DIR).exists()) {
                new File(ICommonConstants.BASE_DIR).mkdirs();
            }
            if (new File(ICommonConstants.CONFIG_FILENAME).exists()) {
                read();
                executeKonnektoren();
            }
        }
    }

    public Konnektor executeKonnektor(Konnektor konnektor) throws Exception {
        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(
            defaultLoggerContext
                .buildHtmlMode(false)
                .buildKonfiguration(konfiguration)
                .buildLogKonnektorExecute(konfiguration.isLogKonnektorExecute())
                .buildLogSoap(konfiguration.isLogKonnektorExecute())
                .buildKonnektor(konnektor)
                .buildMandantId(konfiguration.getMandantId())
                .buildClientSystemId(konfiguration.getClientSystemId())
                .buildWorkplaceId(konfiguration.getWorkplaceId())
        );

        try {
            konnektorService.execute(logger);
        } catch (Exception e) {
            log.error("error on executing konnector services for the konnektor: " + konnektor.getIp(), e);
        } finally {
            if (log.isInfoEnabled() && konfiguration.isLogKonnektorExecute()) {
                log.info(logger.getLogContentAsStr());
            }
            logService.removeLogger(logger.getId());
        }

        return konnektor;
    }

    public void executeKonnektoren() throws Exception {
        for (Iterator<Konnektor> iterator = konfiguration.getKonnektoren().iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            executeKonnektor(konnektor);
        }
    }

    public Konfiguration checkDefaultValues(Konfiguration konfiguration) {
        if (konfiguration == null) {
            konfiguration = new Konfiguration();

            //host
            if (gatewayHostDefaultWert != null && !gatewayHostDefaultWert.trim().isEmpty()) {
                konfiguration.setGatewayHost(gatewayHostDefaultWert);
            }

            //smtp port
            if (smtpGatewayPortDefaultWert != null && !smtpGatewayPortDefaultWert.trim().isEmpty()) {
                konfiguration.setSmtpGatewayPort(smtpGatewayPortDefaultWert);
            }

            //pop3 port
            if (pop3GatewayPortDefaultWert != null && !pop3GatewayPortDefaultWert.trim().isEmpty()) {
                konfiguration.setPop3GatewayPort(pop3GatewayPortDefaultWert);
            }
        }

        return konfiguration;
    }

    public Konfiguration getKonfiguration() throws Exception {
        synchronized (mutex) {
            konfiguration = checkDefaultValues(konfiguration);
            return konfiguration;
        }
    }

    public String init() throws Exception {
        synchronized (mutex) {
            if (new File(ICommonConstants.CONFIG_FILENAME).exists()) {
                read();
            }
            if (konfiguration == null) {
                konfiguration = new Konfiguration();
            }

            konfiguration.setSmtpGatewayConnectionSec(EnumMailConnectionSecurity.NONE);
            konfiguration.setGatewayHost("127.0.0.1");
            konfiguration.setSmtpGatewayPort("8888");
            konfiguration.setPop3GatewayPort("8889");
            konfiguration.setWritePop3CmdLogFile(true);
            konfiguration.setWriteSmtpCmdLogFile(true);

            konfiguration = checkDefaultValues(konfiguration);
            write();
            return "ok";
        }
    }

    private void write() throws Exception {
        String content = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(konfiguration);
        content = StringUtils.xor(content, ICommonConstants.ENC_KEYS);
        content = new String(Base64.getEncoder().encode(content.getBytes()));
        FileUtils.writeToFile(content, ICommonConstants.CONFIG_FILENAME);
    }

    private void read() throws Exception {
        String str = FileUtils.readFileContent(ICommonConstants.CONFIG_FILENAME);
        str = new String(Base64.getDecoder().decode(str.getBytes()));
        str = StringUtils.xor(str, ICommonConstants.ENC_KEYS);
        konfiguration = new ObjectMapper().readValue(str, Konfiguration.class);
    }

    public String speichern(Konfiguration konfiguration) throws Exception {
        synchronized (mutex) {
            konfiguration = checkDefaultValues(konfiguration);
            konfiguration.synchronize(this.konfiguration);

            //check fachdienst cert file
            if (this.konfiguration.getFachdienstCertFilename() != null) {
                konfiguration.setFachdienstCertFilename(this.konfiguration.getFachdienstCertFilename());
            }
            if (konfiguration.getFachdienstCertFile() != null) {
                File dir = new File(ICommonConstants.BASE_DIR);
                File file = new File(dir.getAbsolutePath() + File.separator + FACHDIENST_CERT_FILENAME);
                file.delete();
                konfiguration.setFachdienstCertFilename(file.getName());
                konfiguration.getFachdienstCertFile().transferTo(file);
            }

            this.konfiguration = konfiguration;
            write();
            executeKonnektoren();
            return "ok";
        }
    }

    public String speichern(MinimalKonfiguration minimalKonfiguration) throws Exception {
        synchronized (mutex) {

            konfiguration.setMandantId(minimalKonfiguration.getMandantId());
            konfiguration.setClientSystemId(minimalKonfiguration.getClientSystemId());
            konfiguration.setWorkplaceId(minimalKonfiguration.getWorkplaceId());

            Konnektor konnektor = new Konnektor();
            konnektor.setUuid(minimalKonfiguration.getKonnektorUuid());
            konnektor.setIp(minimalKonfiguration.getKonnektorIp());
            konnektor.setName(minimalKonfiguration.getKonnektorName());
            konnektor.setSdsUrl(minimalKonfiguration.getKonnektorSdsUrl());
            konnektor.setKonnektorAuthMethod(minimalKonfiguration.getKonnektorAuthMethod());
            konnektor.setBasicAuthUser(minimalKonfiguration.getKonnektorBasicAuthUser());
            konnektor.setBasicAuthPwd(minimalKonfiguration.getKonnektorBasicAuthPwd());
            konnektor.setClientCertAuthPwd(minimalKonfiguration.getKonnektorClientCertAuthPwd());
            konnektor.setClientCertFile(minimalKonfiguration.getKonnektorClientCertFile());

            speichernKonnektor(konnektor);
            return "ok";
        }
    }

    public String loeschenKonnektor(String uuid) throws Exception {
        synchronized (mutex) {
            Konnektor konnektor = getKonnektor(uuid, false);
            if (konnektor != null) {
                konfiguration.getKonnektoren().remove(konnektor);
                write();
            } else {
                throw new IllegalStateException("Die Konnektor-Konfiguration konnte nicht geladen werden mit der id: " + uuid);
            }
            return "ok";
        }
    }

    public String speichernKonnektor(Konnektor konnektor) throws Exception {
        synchronized (mutex) {
            Konnektor dbKonnektor = getKonnektor(konnektor.getUuid(), false);

            //delete old client cert file
            if (!konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)
                && dbKonnektor != null
                && dbKonnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)
            ) {
                File dir = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_DIR, konnektor.getUuid()));
                if (dir.exists()) {
                    new File(dir.getAbsolutePath() + File.separator + dbKonnektor.getClientCertFilename()).delete();
                }
            }

            if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)
                && konnektor.getClientCertFilename() == null
                && dbKonnektor != null
                && dbKonnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)
            ) {
                konnektor.setClientCertFilename(dbKonnektor.getClientCertFilename());
            }

            //save new client cert file
            if (konnektor.getClientCertFile() != null) {

                File dir = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_DIR, konnektor.getUuid()));

                if (dbKonnektor != null) {
                    new File(dir.getAbsolutePath() + File.separator + dbKonnektor.getClientCertFilename()).delete();
                }

                konnektor.setClientCertFilename(konnektor.getClientCertFile().getOriginalFilename());
                dir.mkdirs();
                konnektor.getClientCertFile().transferTo(new File(dir.getAbsolutePath() + File.separator + konnektor.getClientCertFile().getOriginalFilename()));
            }

            //save new server cert file into jks
            if (konnektor.getServerCertFile() != null) {

                File dir = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_DIR, konnektor.getUuid()));
                dir.mkdirs();

                //save into jks
                File truststoreFile = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_TRUSTORE_JKS, konnektor.getUuid()));
                KeyStore keyStore = KeyStore.getInstance("JKS");
                if (truststoreFile.exists()) {
                    keyStore.load(new FileInputStream(truststoreFile), ICommonConstants.KONNEKTOR_TRUSTORE_JKS_PWD.toCharArray());
                } else {
                    keyStore.load(null, ICommonConstants.KONNEKTOR_TRUSTORE_JKS_PWD.toCharArray());
                }

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(konnektor.getServerCertFile().getInputStream());

                keyStore.deleteEntry(ICommonConstants.KONNEKTOR_TRUSTORE_JKS_CERT_ALIAS);
                keyStore.setCertificateEntry(ICommonConstants.KONNEKTOR_TRUSTORE_JKS_CERT_ALIAS, cert);

                FileOutputStream outputStream = new FileOutputStream(truststoreFile);
                keyStore.store(outputStream, ICommonConstants.KONNEKTOR_TRUSTORE_JKS_PWD.toCharArray());
            }

            //executing konnektor services
            DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
            DefaultLogger logger = logService.createLogger(
                defaultLoggerContext
                    .buildHtmlMode(false)
                    .buildKonfiguration(konfiguration)
                    .buildLogKonnektorExecute(konfiguration.isLogKonnektorExecute())
                    .buildLogSoap(konfiguration.isLogKonnektorExecute())
                    .buildKonnektor(konnektor)
                    .buildMandantId(konfiguration.getMandantId())
                    .buildClientSystemId(konfiguration.getClientSystemId())
                    .buildWorkplaceId(konfiguration.getWorkplaceId())
            );

            try {
                konnektor = konnektorService.execute(logger);
            } catch (Exception e) {
                log.error("erron on executing konnector services for the konnektor: " + konnektor.getIp(), e);
            } finally {
                if (log.isInfoEnabled() && konfiguration.isLogKonnektorExecute()) {
                    log.info(logger.getLogContentAsStr());
                }
                logService.removeLogger(logger.getId());
            }

            if (dbKonnektor != null) {
                if (dbKonnektor.isActivated() && !konnektor.isActivated()) {
                    konnektor = dbKonnektor;
                    konnektor.setActivated(false);
                }
                int foundIdx = konfiguration.getKonnektoren().indexOf(dbKonnektor);
                konfiguration.getKonnektoren().remove(foundIdx);
                konfiguration.getKonnektoren().add(foundIdx, konnektor);
            } else {
                konfiguration.getKonnektoren().add(konnektor);
            }
            write();
            return "ok";
        }
    }

    public KonnektorServiceBean getKonnektorServiceBean(String konnId, String wsId, boolean throwException) throws Exception {
        for (Iterator<Konnektor> iterator = this.konfiguration.getKonnektoren().iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            if (konnektor.getUuid().equals(konnId)) {
                for (Iterator<KonnektorServiceBean> iterator1 = konnektor.getKonnektorServiceBeans().iterator(); iterator1.hasNext(); ) {
                    KonnektorServiceBean konnektorServiceBean = iterator1.next();
                    if (konnektorServiceBean.getId().equals(wsId)) {
                        return konnektorServiceBean;
                    }
                }
            }
        }
        if (throwException) {
            throw new IllegalStateException("unknow webservice for: " + konnId + " - " + wsId);
        }
        return null;
    }

    public Konnektor getKonnektor(String konnId, boolean throwException) throws Exception {
        for (Iterator<Konnektor> iterator = this.konfiguration.getKonnektoren().iterator(); iterator.hasNext(); ) {
            Konnektor konnektor = iterator.next();
            if (konnektor.getUuid().equals(konnId)) {
                return konnektor;
            }
        }
        if (throwException) {
            throw new IllegalStateException("unknow konnektor for: " + konnId);
        }
        return null;
    }
}
