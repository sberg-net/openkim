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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

import javax.servlet.http.HttpServletRequest;

@Controller
public class AbstractWebController {

    public int ERROR_CODE_INTERNAL_FAILURE = -101;

    private static final Logger log = LoggerFactory.getLogger(AbstractWebController.class);

    @Autowired
    private MappingJackson2JsonView jsonView;

    @ExceptionHandler(HttpMessageNotWritableException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleHttpMessageNotWritableException(HttpMessageNotWritableException e, HttpServletRequest request) {
        return createView(ERROR_CODE_INTERNAL_FAILURE, e, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ModelAndView handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        return createView(ERROR_CODE_INTERNAL_FAILURE, e, e.getMessage());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public ModelAndView handleNoSuchRequestHandlingMethodException(NoHandlerFoundException e, HttpServletRequest request) {
        return createView(ERROR_CODE_INTERNAL_FAILURE, e, e.getMessage());
    }

    private ModelAndView createView(int code, Exception e, String message) {
        log.error("error on handle request: " + message, e);
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("code", code);
        modelAndView.addObject("message", message);
        modelAndView.setView(jsonView);
        return modelAndView;
    }
}
