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
package net.sberg.openkim.testtool;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.mail.MailPartContent;
import net.sberg.openkim.mail.MailPartContentService;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

@Controller
public class MailAnalyzerController {

    private static final Logger log = LoggerFactory.getLogger(MailAnalyzerController.class);

    @Autowired
    private MailPartContentService mailPartContentService;
    @Autowired
    private LogService logService;

    @RequestMapping(value = "/mailanalyzer/execute", method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @ResponseStatus(value = HttpStatus.OK)
    public String execute(
        Model model,
        @RequestParam(name = "id", required = false) String id,
        @RequestParam(name = "mailFilename", required = false) String mailFilename,
        @RequestParam(name = "mailContent", required = false) String mailContent,
        @RequestParam(name = "mailFile", required = false) MultipartFile mailFile
    ) throws Exception {

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        DefaultLogger logger = logService.createLogger(defaultLoggerContext.buildHtmlMode(true));
        MailAnalyzerResult mailAnalyzerResult = new MailAnalyzerResult();
        try {
            mailAnalyzerResult.setMailFilename(mailFilename);

            if (id == null || id.trim().isEmpty()) {
                mailAnalyzerResult.setId(UUID.randomUUID().toString());
            } else {
                mailAnalyzerResult.setId(id);
            }

            if (mailContent != null && !mailContent.trim().isEmpty()) {
                mailAnalyzerResult.setMailContent(mailContent);
            }

            if (mailFile != null) {
                mailAnalyzerResult.setMailFilename(mailFile.getOriginalFilename());
                MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()), mailFile.getInputStream());
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                mimeMessage.writeTo(byteArrayOutputStream);
                mailAnalyzerResult.setMailContent(byteArrayOutputStream.toString(StandardCharsets.UTF_8));
                MailPartContent content = mailPartContentService.analyze(logger, mimeMessage);
                mailAnalyzerResult.setMailAnalyzedContent(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(content));
            }
            model.addAttribute("fehler", false);
        } catch (Exception e) {
            log.error("error on mailanalyzer testtool", e);
            model.addAttribute("fehler", true);
        }

        model.addAttribute("result", mailAnalyzerResult);
        model.addAttribute("logs", logger.getLogContentAsStr());
        logService.removeLogger(logger.getId());

        return "mailanalyzer/mailanalyzerFormular";
    }
}
