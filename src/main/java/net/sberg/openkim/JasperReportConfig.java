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
package net.sberg.openkim;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;

@Configuration
public class JasperReportConfig {

    private static final String JASPER_REPORT_DIR = "classpath:/reports/";
    private static final String REPORT_DATA_KEY = "datasource";

    @Autowired
    private ApplicationContext applicationContext;

    private JasperReport loadReport(String url) throws Exception {
        Resource resource = applicationContext.getResource(url);
        InputStream is = resource.getInputStream();
        JasperDesign design = JRXmlLoader.load(is);
        return JasperCompileManager.compileReport(design);
    }

    @Bean(name = "signaturpruefbericht")
    public JasperReport signaturpruefbericht() throws Exception {
        return loadReport(JASPER_REPORT_DIR + "signaturpruefbericht.jrxml");
    }
}
