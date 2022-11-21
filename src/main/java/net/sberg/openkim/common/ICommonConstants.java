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
package net.sberg.openkim.common;

import java.io.File;

public interface ICommonConstants {
    String BASE_DIR = System.getProperty("user.dir") + File.separator + "data" + File.separator;
    String CONFIG_FILENAME = BASE_DIR + "config.cfg";
    String POP3_LOG_DIR = BASE_DIR + "pop3" + File.separator;
    String POP3_LOG_FILENAME = POP3_LOG_DIR + "{0}.log";
    String SMTP_LOG_DIR = BASE_DIR + "smtp" + File.separator;
    String SMTP_LOG_FILENAME = SMTP_LOG_DIR + "{0}.log";
    String KONNEKTOR_DIR = BASE_DIR + "konnektor" + File.separator + "{0}" + File.separator;
    String KONNEKTOR_TRUSTORE_JKS = KONNEKTOR_DIR + "truststore.jks";
    String KONNEKTOR_TRUSTORE_JKS_PWD = "changeit";
    String KONNEKTOR_TRUSTORE_JKS_CERT_ALIAS = "servercert";
    String[] ENC_KEYS = new String[]{"qwzwebnjcv5461237884", "fgjnkfnkndfk", "tgzjnbdnbjdfngj", "dkgfjgkfjgkjfgkfxccnv", "rfughnvvcnbfjgjvnxcbn"};
    String OPENKIM_SERVER_KEYSTORE_FILENAME = "openkimkeystore.jks";
    String OPENKIM_SERVER_KEYSTORE_PWD = "123!sberg!456";
    String OPENKIM_SERVER_KEYSTORE_ALIAS = "openkimkeystorealias_";
}
