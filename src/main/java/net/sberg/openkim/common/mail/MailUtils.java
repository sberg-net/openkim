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
package net.sberg.openkim.common.mail;

import com.sun.mail.util.MailSSLSocketFactory;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.konfiguration.Konfiguration;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Properties;

public class MailUtils {

    public static final MimeMessage createMimeMessage(Session session, InputStream inputStream, boolean updateMessageID) throws Exception {
        if (updateMessageID) {
            return new MimeMessage(session, inputStream);
        } else {
            return new MimeMessage(session, inputStream) {
                protected void updateMessageID() {
                }
            };
        }
    }

    public static final Session createPop3ClientSession(
        Properties props,
        EnumMailConnectionSecurity connectionSecurity,
        EnumMailAuthMethod authMethod,
        String host,
        String port,
        Konfiguration konfiguration
    ) throws Exception {

        String certfileName = ICommonConstants.BASE_DIR + File.separator + konfiguration.getFachdienstCertFilename();
        char[] passCharArray = konfiguration.getFachdienstCertAuthPwd().toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(certfileName), passCharArray);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, passCharArray);

        MailSSLSocketFactory mailSSLSocketFactory = new MailSSLSocketFactory();
        mailSSLSocketFactory.setKeyManagers(keyManagerFactory.getKeyManagers());
        mailSSLSocketFactory.setTrustAllHosts(true);

        props = fillPop3MailProps(
            props,
            connectionSecurity,
            authMethod,
            host,
            port,
            konfiguration.getPop3ClientIdleTimeoutInSeconds() * 1000
        );
        props.put("mail.pop3.ssl.socketFactory", mailSSLSocketFactory);
        return Session.getInstance(props);
    }

    private static final Properties fillPop3MailProps(
        Properties props,
        EnumMailConnectionSecurity connectionSecurity,
        EnumMailAuthMethod authMethod,
        String host,
        String port,
        int timeout) throws Exception {
        if (connectionSecurity.equals(EnumMailConnectionSecurity.STARTTLS)) {
            props.put("mail.pop3.starttls.enable", "true");
            props.put("mail.pop3.ssl.enable", "false");
        } else if (connectionSecurity.equals(EnumMailConnectionSecurity.SSLTLS)) {
            props.put("mail.pop3.starttls.enable", "false");
            props.put("mail.pop3.ssl.enable", "true");
        } else if (connectionSecurity.equals(EnumMailConnectionSecurity.NONE)) {
            props.put("mail.pop3.starttls.enable", "false");
            props.put("mail.pop3.ssl.enable", "false");
        }

        if (authMethod.equals(EnumMailAuthMethod.NORMALPWD)) {
            props.put("mail.pop3.auth", "true");
        } else {
            props.put("mail.pop3.auth", "false");
        }

        props.put("mail.transport.protocol", "pop3");
        props.put("mail.store.protocol", "pop3");
        props.put("mail.pop3.host", host);
        props.put("mail.pop3.port", port);
        props.put("mail.pop3.connectiontimeout", timeout);
        props.put("mail.pop3.timeout", timeout);
        props.put("mail.pop3.writetimeout", timeout);

        return props;
    }
}
