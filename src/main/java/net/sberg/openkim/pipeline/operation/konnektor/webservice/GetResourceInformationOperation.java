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

import de.gematik.ws.conn.connectorcontext.ContextType;
import de.gematik.ws.conn.eventservice.v7_2_0.GetResourceInformation;
import de.gematik.ws.conn.eventservice.v7_2_0.GetResourceInformationResponse;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.function.Consumer;

@PipelineOperation
public class GetResourceInformationOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(GetResourceInformationOperation.class);
    public static final String NAME = "GetResourceInformation";

    public static final String ENV_CARD_TERMINAL_ID = "cardTerminalId";
    public static final String ENV_SLOT_ID = "slotId";
    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_ICSSN = "icssn";
    public static final String ENV_GET_RESOURCE_INFORMATION_RESPONSE = "getResourceInformationResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> consumer) {
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
                    konnektorServiceBean.createSoapAction("GetResourceInformation"),
                    logger
            );

            GetResourceInformation getResourceInformation = new GetResourceInformation();
            getResourceInformation.setContext(contextType);

            String cardHandle = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE);
            String cardTerminalId = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARD_TERMINAL_ID);
            String slotId = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SLOT_ID);
            String icssn = (String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ICSSN);

            if (cardHandle != null && !cardHandle.trim().isEmpty()) {
                getResourceInformation.setCardHandle(cardHandle);
            }
            if (cardTerminalId != null && !cardTerminalId.trim().isEmpty()) {
                getResourceInformation.setCtId(cardTerminalId);
            }
            if (slotId != null && !slotId.trim().isEmpty()) {
                getResourceInformation.setSlotId(BigInteger.valueOf(Integer.parseInt(slotId)));
            }
            if (icssn != null && !icssn.trim().isEmpty()) {
                getResourceInformation.setIccsn(icssn);
            }

            if (!getResourceInformation.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                        "event webservice not valid "
                                + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                                + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                                + " - packagename not equal "
                                + packageName
                                + " - " + getResourceInformation.getClass().getPackageName()
                );
            }

            timeMetric = metricFactory.timer(NAME);
            GetResourceInformationResponse getResourceInformationResponse = (GetResourceInformationResponse) webserviceConnector.getSoapResponse(getResourceInformation);
            defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_GET_RESOURCE_INFORMATION_RESPONSE, getResourceInformationResponse);
            timeMetric.stopAndPublish();

            consumer.accept(defaultPipelineOperationContext);
            return true;
        } catch (Exception e) {
            log.error("error on executing the GetResourceInformationOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            return false;
        }
    }
}
