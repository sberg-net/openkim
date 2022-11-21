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
package net.sberg.openkim.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.WebSecurityConfig;
import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private Environment env;

    protected static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    private InMemoryUserDetailsManager userDetailsManager;

    public InMemoryUserDetailsManager create() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        userDetailsManager = new InMemoryUserDetailsManager();

        File file = new File(env.getProperty("security.authFile", ""));
        if (file.exists()) {
            try {
                List<User> userList = readExistingUser(mapper, file);
                userList.forEach((user) -> {
                    userDetailsManager.createUser(user);
                });
            }
            catch (Exception e) {
                log.error("error on reading security auth file: " + file.getAbsolutePath(), e);
                throw e;
            }
        }
        else {
            log.info("security auth file does not exist");
            List<User> userList = new ArrayList<>();

            int i = 0;
            while (env.containsProperty("security.users[" + i + "].username")) {
                List<GrantedAuthority> grantedAuthorityList = new ArrayList<>();
                grantedAuthorityList.add(new SimpleGrantedAuthority(env.getProperty("security.users[" + i + "].authorities", "")));

                String password = env.getProperty("security.users[" + i + "].password", "");
                String passwordEncoded = "{bcrypt}" + passwordEncoder.encode(password);

                User user = new User(
                    env.getProperty("security.users[" + i + "].username", ""),
                    passwordEncoded,
                    grantedAuthorityList
                );
                userDetailsManager.createUser(user);
                userList.add(user);
                i++;
            }

            Map<String, List> securityMap = new HashMap<>();
            securityMap.put("users", userList);

            String content = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(securityMap);
            content = StringUtils.xor(content, ICommonConstants.ENC_KEYS);
            content = new String(Base64.getEncoder().encode(content.getBytes()));
            FileUtils.writeToFile(content, file.getAbsolutePath());

            log.info("security auth file created");
        }

        return userDetailsManager;
    }

    private List<User> readExistingUser(ObjectMapper mapper, File file) throws Exception {
        List<User> res = new ArrayList<>();
        String fileContent = FileUtils.readFileContent(file.getAbsolutePath());
        fileContent = new String(Base64.getDecoder().decode(fileContent.getBytes()));
        fileContent = StringUtils.xor(fileContent, ICommonConstants.ENC_KEYS);

        Map<String, List<HashMap>> properties = mapper.readValue(fileContent, new TypeReference<>(){});
        List<HashMap> userList = properties.get("users");
        for (Iterator<HashMap> iterator = userList.iterator(); iterator.hasNext(); ) {
            HashMap user = iterator.next();
            List<HashMap<String, String>> authorities = (ArrayList)user.get("authorities");
            List<GrantedAuthority> grantedAthorities = authorities
                    .stream()
                    .map((obj -> new SimpleGrantedAuthority(obj.get("authority"))))
                    .collect(Collectors.toList());
            res.add(new User(
                    (String) user.get("username"),
                    (String) user.get("password"),
                    (Boolean) user.get("enabled"),
                    (Boolean) user.get("accountNonExpired"),
                    (Boolean) user.get("credentialsNonExpired"),
                    (Boolean) user.get("accountNonLocked"),
                    grantedAthorities
            ));
        }
        return res;
    }

    public boolean updateExistingUser(UserChangePwd userChangePwd) throws Exception {
        File file = new File(env.getProperty("security.authFile", ""));
        if (!file.exists()) {
            throw new IllegalStateException("Passwortdatei nicht vorhanden");
        }
        ObjectMapper mapper = new ObjectMapper();
        List<User> dbUser = readExistingUser(mapper, file);
        User existingUser = null;
        int idx = -1;
        for (Iterator<User> iterator = dbUser.iterator(); iterator.hasNext(); ) {
            User user =  iterator.next();
            idx++;
            if (user.getUsername().equals(userChangePwd.getUsername())) {
                existingUser = user;
                break;
            }
        }

        if (existingUser == null) {
            throw new IllegalStateException("Nutzer: "+userChangePwd.getUsername()+" nicht gefunden");
        }

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        String oldDbPwd = existingUser.getPassword();
        oldDbPwd = oldDbPwd.substring("{bcrypt}".length());
        if (!passwordEncoder.matches(userChangePwd.getOldPwd(), oldDbPwd)) {
            throw new IllegalStateException("das alte Passwort ist nicht korrekt");
        }

        User newUser = new User(existingUser.getUsername(), "{bcrypt}" + passwordEncoder.encode(userChangePwd.getNewPwd()), existingUser.getAuthorities());
        dbUser.remove(idx);
        dbUser.add(idx, newUser);

        userDetailsManager.deleteUser(existingUser.getUsername());
        userDetailsManager.createUser(newUser);

        Map<String, List> securityMap = new HashMap<>();
        securityMap.put("users", dbUser);

        String content = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(securityMap);
        content = StringUtils.xor(content, ICommonConstants.ENC_KEYS);
        content = new String(Base64.getEncoder().encode(content.getBytes()));
        FileUtils.writeToFile(content, file.getAbsolutePath());

        log.info("user updated");

        return true;
    }

}
