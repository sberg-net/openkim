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

import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

public class WebserviceConnector extends WebServiceGatewaySupport {
    public Object getSoapResponse(Object requestPayload) throws Exception {
        Object response = getWebServiceTemplate().marshalSendAndReceive(requestPayload);
        for (int i = 0; i < getMessageSenders().length; i++) {
            ((HttpComponentsMessageSender) getMessageSenders()[i]).destroy();
        }
        return response;
    }
}
