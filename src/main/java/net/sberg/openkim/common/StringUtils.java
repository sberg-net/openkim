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
package net.sberg.openkim.common;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.XMLGregorianCalendar;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class StringUtils {

    private static final Logger log = LoggerFactory.getLogger(StringUtils.class);

    public static final String xor(String s, String[] keys) throws Exception {
        for (int i = 0; i < keys.length; i++) {
            s = xor(s, keys[i]);
        }
        return s;
    }

    public static final String xor(String s, String key) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            sb.append((char) (s.charAt(i) ^ key.charAt(i % key.length())));
        }
        return sb.toString();
    }

    public static final boolean isNewVersionHigher(String oldVersion, String newVersion) throws Exception {
        String[] oldVersionArr = oldVersion.split("\\.");
        String[] newVersionArr = newVersion.split("\\.");
        if (oldVersionArr.length != 3 || newVersionArr.length != 3) {
            log.info("versions not semantic versions: " + oldVersion + " - " + newVersion);
            return false;
        }
        for (int i = 0; i < oldVersionArr.length; i++) {
            int oldV = Integer.parseInt(oldVersionArr[i]);
            int newV = Integer.parseInt(newVersionArr[i]);
            if (newV > oldV) {
                return true;
            }
        }
        return false;
    }

    public static final String convertToPem(X509Certificate cert) throws CertificateEncodingException {
        Base64 encoder = new Base64(64);
        String cert_begin = "-----BEGIN CERTIFICATE-----\n";
        String end_cert = "-----END CERTIFICATE-----";

        byte[] derCert = cert.getEncoded();
        String pemCertPre = new String(encoder.encode(derCert));
        String pemCert = cert_begin + pemCertPre + end_cert;
        return pemCert;
    }

    public static final String convertToStr(XMLGregorianCalendar xmlGregorianCalendar) throws Exception {
        Date date = xmlGregorianCalendar.toGregorianCalendar().getTime();
        DateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        df.setTimeZone(TimeZone.getDefault());
        return df.format(date);
    }

}
