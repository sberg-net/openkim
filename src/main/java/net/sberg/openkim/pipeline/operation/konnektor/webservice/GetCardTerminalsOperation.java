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
package net.sberg.openkim.pipeline.operation.konnektor.webservice;

import de.gematik.ws.conn.cardterminalinfo.v8.CardTerminalInfoType;
import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.eventservice.v7_2_0.GetCardTerminals;
import de.gematik.ws.conn.eventservice.v7_2_0.GetCardTerminalsResponse;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class GetCardTerminalsOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(GetCardTerminalsOperation.class);
    public static final String NAME = "GetCardTerminals";

    public static final String ENV_GET_CARD_TERMINALS_RESPONSE = "getCardTerminalsResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            GetCardTerminalsResponse getCardTerminalsResponse = (GetCardTerminalsResponse) context.getEnvironmentValue(NAME, ENV_GET_CARD_TERMINALS_RESPONSE);
            context.getLogger().logLine("Status = " + getCardTerminalsResponse.getStatus().getResult());
            for (Iterator<CardTerminalInfoType> iterator = getCardTerminalsResponse.getCardTerminals().getCardTerminal().iterator(); iterator.hasNext(); ) {
                context.getLogger().logLine("***********************************");
                CardTerminalInfoType cardTerminalInfoType = iterator.next();
                context.getLogger().logLine("Kartenterminal = " + cardTerminalInfoType.getCtId());
                context.getLogger().logLine("Name = " + cardTerminalInfoType.getName());
                context.getLogger().logLine("Slotanzahl = " + cardTerminalInfoType.getSlots());
                context.getLogger().logLine("Mac-Adresse = " + cardTerminalInfoType.getMacAddress());
                context.getLogger().logLine("IP-Adresse = " + cardTerminalInfoType.getIPAddress().getIPV4Address());
                context.getLogger().logLine("Produkt = " + cardTerminalInfoType.getProductInformation().getProductIdentification().getProductVendorID() + " " + cardTerminalInfoType.getProductInformation().getProductIdentification().getProductVersion().getCentral());
                context.getLogger().logLine("Verbunden = " + cardTerminalInfoType.isConnected());
                context.getLogger().logLine("Physikalisch = " + cardTerminalInfoType.isISPHYSICAL());
            }
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.EventService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction(NAME),
                    logger
            );

            GetCardTerminals getCardTerminals = new GetCardTerminals();
            getCardTerminals.setContext(contextType);

            if (!getCardTerminals.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "event webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName + " - "
                        + getCardTerminals.getClass().getPackageName()
                ));
            }
            else {
                timeMetric = metricFactory.timer(NAME);
                GetCardTerminalsResponse getCardTerminalsResponse = (GetCardTerminalsResponse) webserviceConnector.getSoapResponse(getCardTerminals);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_GET_CARD_TERMINALS_RESPONSE, getCardTerminalsResponse);

                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the GetCardTerminalsOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
