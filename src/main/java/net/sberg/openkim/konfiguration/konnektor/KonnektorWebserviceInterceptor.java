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
package net.sberg.openkim.konfiguration.konnektor;

import net.sberg.openkim.log.DefaultLogger;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.SoapBody;
import org.springframework.ws.soap.SoapEnvelope;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapMessage;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

public class KonnektorWebserviceInterceptor extends ClientInterceptorAdapter {

    private static final Logger log = LoggerFactory.getLogger(KonnektorWebserviceInterceptor.class);

    private final String soapAction;
    private DefaultLogger logger;

    public KonnektorWebserviceInterceptor(String soapAction, DefaultLogger logger) {
        this.soapAction = soapAction;
        this.logger = logger;
    }

    @Override
    public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
        SoapMessage soapMessage = (SoapMessage) messageContext.getRequest();
        soapMessage.setSoapAction(soapAction);
        transformSoap(soapMessage);
        return true;
    }

    private void transformSoap(SoapMessage soapMessage) {
        try {
            if (logger.getDefaultLoggerContext().isLogSoap() || log.isInfoEnabled()) {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                StringWriter writer = new StringWriter();
                transformer.transform(new DOMSource(soapMessage.getDocument()), new StreamResult(writer));
                String soap = writer.getBuffer().toString().replaceAll("\n|\r", "");
                if (logger.getDefaultLoggerContext().isLogSoap()) {
                    logger.logLine(soapAction + " - " + (logger.getDefaultLoggerContext().isHtmlMode() ? StringEscapeUtils.escapeHtml4(soap) : soap));
                } else if (log.isInfoEnabled()) {
                    log.info(soapAction + " - " + soap);
                }
            }
        } catch (Exception e) {
            log.error("error on transforming ws document to string", e);
        }
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
        SoapMessage soapMessage = (SoapMessage) messageContext.getResponse();
        transformSoap(soapMessage);
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
        SoapMessage soapMessage = (SoapMessage) messageContext.getResponse();
        SoapEnvelope soapEnvelope = soapMessage.getEnvelope();
        SoapBody soapBody = soapEnvelope.getBody();
        SoapFault soapFault = soapBody.getFault();
        log.error(soapFault.getFaultStringOrReason());
        logger.logLine(soapFault.getFaultStringOrReason());
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
        this.logger = null;
    }
}
