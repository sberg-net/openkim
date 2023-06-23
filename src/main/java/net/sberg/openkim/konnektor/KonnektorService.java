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

import net.sberg.openkim.common.CommonBuilderFactory;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.dashboard.DashboardService;
import net.sberg.openkim.fachdienst.FachdienstService;
import net.sberg.openkim.konfiguration.ServerState;
import net.sberg.openkim.konfiguration.ServerStateService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.konnektor.KonnektorConnectionInformationOperation;
import net.sberg.openkim.pipeline.operation.konnektor.KonnektorLoadAllCardInformationOperation;
import net.sberg.openkim.pipeline.operation.konnektor.ntp.NtpRequestOperation;
import net.sberg.openkim.pipeline.operation.konnektor.ntp.NtpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

@Service
public class KonnektorService {

    private static final Logger log = LoggerFactory.getLogger(KonnektorService.class);

    @Autowired
    private ServerStateService serverStateService;
    @Autowired
    private FachdienstService fachdienstService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private PipelineService pipelineService;

    @Value("${spring.ldap.base}")
    private String vzdSearchBase;

    public Konnektor execute(
        DefaultLogger logger) {

        KonnektorServiceBean encryptionServiceBean = null;
        KonnektorServiceBean signatureServiceBean = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        konnektor.setVzdSearchBase(vzdSearchBase);

        //parse service descriptors
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new CommonBuilderFactory().requestKonnektorServiceDescriptors(konnektor));
            doc.getDocumentElement().normalize();

            //parse ProductName
            Node node = (Node) xpath.evaluate("//*[local-name()='ProductInformation']/*[local-name()='ProductMiscellaneous']/*[local-name()='ProductName']", doc, XPathConstants.NODE);
            konnektor.setProductName(node.getTextContent());
            //parse ProductType
            node = (Node) xpath.evaluate("//*[local-name()='ProductInformation']/*[local-name()='ProductTypeInformation']/*[local-name()='ProductType']", doc, XPathConstants.NODE);
            konnektor.setProductType(node.getTextContent());
            //parse ProductTypeVersion
            node = (Node) xpath.evaluate("//*[local-name()='ProductInformation']/*[local-name()='ProductTypeInformation']/*[local-name()='ProductTypeVersion']", doc, XPathConstants.NODE);
            konnektor.setProductTypeVersion(node.getTextContent());
            //parse HWVersion
            node = (Node) xpath.evaluate("//*[local-name()='HWVersion']", doc, XPathConstants.NODE);
            konnektor.setHwVersion(node.getTextContent());
            //parse HWVersion
            node = (Node) xpath.evaluate("//*[local-name()='FWVersion']", doc, XPathConstants.NODE);
            konnektor.setFwVersion(node.getTextContent());

            konnektor.setKonnektorServiceBeans(new ArrayList<>());
            encryptionServiceBean = parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.EncryptionService, null);
            signatureServiceBean = parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.SignatureService, null);
            parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.CardService, null);
            parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.CardTerminalService, null);
            parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.AuthSignatureService, null);
            parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.CertificateService, null);
            parseServiceBean(xpath, konnektor, doc, EnumKonnektorServiceBeanType.EventService, null);

            konnektor.setKonnektorServiceBeansLoaded(konnektor.getKonnektorServiceBeans().size() > 0);
            logger.logLine("konnektor service beans loaded: " + konnektor.getIp());
        } catch (Exception e) {
            log.error("error on loading the service beans for the konnektor: " + konnektor.getIp(), e);
            konnektor.setKonnektorServiceBeansLoaded(false);
        }

        log.info("checkEccEncryption for the konnektor: " + konnektor.getIp());
        checkEccEncryption(konnektor, logger, encryptionServiceBean, signatureServiceBean);

        log.info("checkTls for the konnektor: " + konnektor.getIp());
        checkTls(konnektor);

        log.info("checkVzd for the konnektor: " + konnektor.getIp());
        checkVzd(konnektor);

        log.info("checkNtp for the konnektor: " + konnektor.getIp());
        checkNtp(konnektor, logger);

        log.info("loadAllCards for the konnektor: " + konnektor.getIp());
        loadAllCards(konnektor, logger);

        log.info("checkConnectivity for the konnektor: " + konnektor.getIp());
        checkConnectivity(konnektor, logger);

        log.info("checkKonnektorWebServiceBean for the konnektor: " + konnektor.getIp());
        checkKonnektorWebServiceBean(konnektor, logger);

        log.info("createFachdienste for the konnektor: " + konnektor.getIp());
        createFachdienste(konnektor, logger);

        try {
            dashboardService.createResult(konnektor);
        } catch (Exception e) {
            log.error("error on creating monitpring result for the konnektor: " + konnektor.getIp(), e);
        }

        return konnektor;
    }

    private void checkEccEncryption(Konnektor konnektor, DefaultLogger logger, KonnektorServiceBean encryptionServiceBean, KonnektorServiceBean signatureServiceBean) {
        try {
            if (signatureServiceBean == null || encryptionServiceBean == null) {
                return;
            }
            if (StringUtils.isNewVersionHigher(signatureServiceBean.getVersion(), KonnektorWebserviceUtils.ECC_ENC_AVAILABLE_SIGNATURESERVICE_VERSION)
                || StringUtils.isNewVersionHigher(encryptionServiceBean.getVersion(), KonnektorWebserviceUtils.ECC_ENC_AVAILABLE_ENCRYPTIONSERVICE_VERSION)
            ) {
                konnektor.setEccEncryptionAvailable(false);
                logger.logLine("ecc encryption for konnektor: " + konnektor.getIp() + " not available");
                log.info("ecc encryption for konnektor: " + konnektor.getIp() + " not available");
            } else {
                konnektor.setEccEncryptionAvailable(true);
                logger.logLine("ecc encryption for konnektor: " + konnektor.getIp() + " available");
                log.info("ecc encryption for konnektor: " + konnektor.getIp() + " available");
            }
        } catch (Exception e) {
            log.error("error on checking ecc encryption available for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void checkTls(Konnektor konnektor) {
        try {
            konnektor.setTlsPortServerState(serverStateService.checkServer(
                "konnektor" + konnektor.getUuid(),
                "Konnektor - " + (konnektor.getName() == null ? "unbekannt" : konnektor.getName()),
                konnektor.getIp(),
                "443",
                konnektor.isActivated()
            ));
        } catch (Exception e) {
            log.error("error on checking tls port for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void checkVzd(Konnektor konnektor) {
        try {
            ServerState serverState = serverStateService.checkServer(
                "gem-ldap",
                "VZD Zugriff per LDAP",
                konnektor.getIp(),
                "636",
                true
            );
            konnektor.setVzdLdapServerState(serverState);
        } catch (Exception e) {
            log.error("error on checking vzd service for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void checkKonnektorWebServiceBean(Konnektor konnektor, DefaultLogger logger) {
        try {
            CommonBuilderFactory commonBuilderFactory = new CommonBuilderFactory();
            for (Iterator<KonnektorServiceBean> iterator = konnektor.getKonnektorServiceBeans().iterator(); iterator.hasNext(); ) {
                KonnektorServiceBean konnektorServiceBean = iterator.next();
                try {
                    commonBuilderFactory.checkKonnektorServiceBean(konnektor, konnektorServiceBean, logger);
                } catch (Exception e) {
                    konnektorServiceBean.setAlive(false);
                    logger.logLine("false check exception checkKonnektorWebServiceBean for: " + konnektorServiceBean.getEnumKonnektorServiceBeanType() + " - " + konnektorServiceBean.getEndpointTls());
                    log.error("false check exception checkKonnektorWebServiceBean for: " + konnektorServiceBean.getEnumKonnektorServiceBeanType() + " - " + konnektorServiceBean.getEndpointTls(), e);
                }
            }
        } catch (Exception e) {
            log.error("error on checking webservice bean for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void checkNtp(Konnektor konnektor, DefaultLogger logger) {
        try {
            IPipelineOperation ntpPipelineOperation = pipelineService.getOperation(IPipelineOperation.BUILTIN_VENDOR+"."+ NtpRequestOperation.NAME);
            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);

            ntpPipelineOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    NtpResult ntpResult = (NtpResult) defaultPipelineOperationContext.getEnvironmentValue(NtpRequestOperation.NAME, NtpRequestOperation.ENV_NTP_RESULT);
                    konnektor.setKonnektorTime(ntpResult.getKonnektorTime());
                    konnektor.setSystemTime(ntpResult.getSystemTime());
                    konnektor.setDiffSystemKonnektorTime(ntpResult.getKonnektorTime().getTime() - ntpResult.getSystemTime().getTime());
                },
                (context, e) -> {
                    log.error("error on checking ntp service for the konnektor: " + konnektor.getIp(), e);
                }
            );
        } catch (Exception e) {
            log.error("error on checking ntp service for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void loadAllCards(Konnektor konnektor, DefaultLogger logger) {
        try {
            log.info("load card infos for the konnektor: " + konnektor.getIp());
            logger.logLine("load card infos for the konnektor: " + konnektor.getIp());

            KonnektorLoadAllCardInformationOperation konnektorLoadAllCardsPipelineOperation = (KonnektorLoadAllCardInformationOperation)pipelineService.getOperation(IPipelineOperation.BUILTIN_VENDOR+"."+ KonnektorLoadAllCardInformationOperation.NAME);
            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);

            konnektorLoadAllCardsPipelineOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    logger.logLine("load card infos successful for the konnektor: " + konnektor.getIp());
                },
                (context, e) -> {
                    log.error("error on loading all cards for the konnektor: " + konnektor.getIp(), e);
                }
            );
        } catch (Exception e) {
            log.error("error on loading all cards for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void checkConnectivity(Konnektor konnektor, DefaultLogger logger) {
        try {
            log.info("load connectivity info for the konnektor: " + konnektor.getIp());
            logger.logLine("load connectivity info for the konnektor: " + konnektor.getIp());

            KonnektorConnectionInformationOperation konnektorConnectionInfoPipelineOperation = (KonnektorConnectionInformationOperation) pipelineService.getOperation(IPipelineOperation.BUILTIN_VENDOR+"."+ KonnektorConnectionInformationOperation.NAME);

            DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
            konnektorConnectionInfoPipelineOperation.execute(
                defaultPipelineOperationContext,
                context -> {
                    logger.logLine("load connectivity info successful for the konnektor: " + konnektor.getIp());
                },
                (context, e) -> {
                    log.error("error on loading connectivity infos for the konnektor: " + konnektor.getIp(), e);
                }
            );

        } catch (Exception e) {
            log.error("error on loading connectivity infos for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private void createFachdienste(Konnektor konnektor, DefaultLogger logger) {
        try {
            log.info("create all fachdienste for the konnektor: " + konnektor.getIp());
            logger.logLine("create all fachdienste for the konnektor: " + konnektor.getIp());
            konnektor.getFachdienste().clear();
            konnektor.getFachdienste().addAll(fachdienstService.create(logger));
        } catch (Exception e) {
            log.error("error on creating all fachdienste for the konnektor: " + konnektor.getIp(), e);
        }
    }

    private KonnektorServiceBean parseServiceBean(XPath xpath, Konnektor konnektor, Document doc, EnumKonnektorServiceBeanType enumKonnektorServiceBeanType, String maxVersion) throws Exception {
        NodeList list = (NodeList) xpath.evaluate("//*[local-name()='Service'][@Name='" + enumKonnektorServiceBeanType.name() + "']//*[local-name()='Version']", doc, XPathConstants.NODESET);

        KonnektorServiceBean currentKonnektorServiceBean = null;
        for (int i = 0; i < list.getLength(); i++) {
            KonnektorServiceBean konnektorServiceBean = new KonnektorServiceBean();
            konnektorServiceBean.setId(UUID.randomUUID().toString());
            konnektorServiceBean.setEnumKonnektorServiceBeanType(enumKonnektorServiceBeanType);
            Node node = list.item(i);
            konnektorServiceBean.setVersion(node.getAttributes().getNamedItem("Version").getNodeValue());
            for (int j = 0; j < node.getChildNodes().getLength(); j++) {
                Node child = node.getChildNodes().item(j);
                if (child.getNodeName().toLowerCase().endsWith("endpoint")) {
                    konnektorServiceBean.setEndpoint(child.getAttributes().getNamedItem("Location").getNodeValue());
                } else if (child.getNodeName().toLowerCase().endsWith("endpointtls")) {
                    konnektorServiceBean.setEndpointTls(child.getAttributes().getNamedItem("Location").getNodeValue());
                }
            }

            if (currentKonnektorServiceBean == null) {
                currentKonnektorServiceBean = konnektorServiceBean;
            } else if (StringUtils.isNewVersionHigher(currentKonnektorServiceBean.getVersion(), konnektorServiceBean.getVersion())) {
                if (maxVersion != null && !StringUtils.isNewVersionHigher(maxVersion, konnektorServiceBean.getVersion())) {
                    currentKonnektorServiceBean = konnektorServiceBean;
                } else if (maxVersion == null) {
                    currentKonnektorServiceBean = konnektorServiceBean;
                }
            }
        }

        if (currentKonnektorServiceBean != null) {
            konnektor.getKonnektorServiceBeans().add(currentKonnektorServiceBean);
        }

        log.info("service bean " + enumKonnektorServiceBeanType.name() + " for the konnektor: " + konnektor.getIp() + " is initialized");
        return currentKonnektorServiceBean;
    }
}
