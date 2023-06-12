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
package net.sberg.openkim.user;

import net.sberg.openkim.common.AbstractWebController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class UserController extends AbstractWebController {

    @Autowired
    private UserService userService;

    @RequestMapping(value = "/user/settings", method = RequestMethod.GET)
    @ResponseStatus(value = HttpStatus.OK)
    public String laden() throws Exception {
        return "user/userSettings";
    }

    @RequestMapping(value = "/user/changePwd", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    @ResponseBody
    public boolean aendern(@RequestBody UserChangePwd userChangePwd) throws Exception {
        return userService.updateExistingUser(userChangePwd);
    }

}
