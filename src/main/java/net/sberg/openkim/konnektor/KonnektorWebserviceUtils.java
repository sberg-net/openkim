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
package net.sberg.openkim.konnektor;

import net.sberg.openkim.common.CommonBuilderFactory;
import net.sberg.openkim.log.DefaultLogger;

import java.util.Arrays;
import java.util.List;

public class KonnektorWebserviceUtils {

    public static final String STATUS_OK = "OK";
    public static final String CARD_PIN_TYP_SMC = "PIN.SMC";
    public static final String CARD_PIN_TYP_CH = "PIN.CH";
    public static final String CERT_REF_AUT = "C.AUT";
    public static final String CERT_REF_ENC = "C.ENC";
    public static final String CERT_REF_SIG = "C.SIG";
    public static final String CERT_REF_QES = "C.QES";

    public static final String CARD_TYPE_SMB = "SM-B";
    public static final String CARD_TYPE_SMCB = "SMC-B";
    public static final String CARD_TYPE_HBA = "HBA";
    public static final String CARD_TYPE_HBAx = "HBAx";
    public static final List interestingCardTypes = Arrays.asList(CARD_TYPE_SMCB, CARD_TYPE_SMB);

    public static final String ECC_ENC_AVAILABLE_SIGNATURESERVICE_VERSION = "7.4.1";
    public static final String ECC_ENC_AVAILABLE_ENCRYPTIONSERVICE_VERSION = "6.1.1";

    public static final String getPinType(String cardType) {
        if (cardType.toLowerCase().startsWith("sm")) {
            return CARD_PIN_TYP_SMC;
        }
        return CARD_PIN_TYP_CH;
    }

    public static final WebserviceConnector createConnector(Konnektor konnektor, String packageName, KonnektorServiceBean konnektorServiceBean, String soapAction, DefaultLogger logger) throws Exception {
        CommonBuilderFactory konnektorConnectionBuilder = new CommonBuilderFactory();
        return konnektorConnectionBuilder.buildWebserviceConnector(logger, packageName, konnektorServiceBean.getEndpointTls(), konnektor, soapAction);
    }
}
