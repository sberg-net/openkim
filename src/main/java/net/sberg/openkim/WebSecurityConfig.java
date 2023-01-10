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

import net.sberg.openkim.common.EnumAuthRole;
import net.sberg.openkim.gateway.GatewayKeystoreController;
import net.sberg.openkim.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

// https://spring.io/guides/gs/securing-web/


@EnableWebSecurity
@Configuration
public class WebSecurityConfig {

    @Autowired
    private UserService userService;

   /* @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/", "/konfiguration/**,", "/minimalkonfiguration/**",
                                "/openkimkeystore/**", "/konnektor/**", "/log/**", "/pop3log/**", "/smtplog/**",
                                "/dashboard/**", "/konnvzd/**", "/konnwebservice/**", "/konnntp/**",
                                "/mailanalyzer/**", "/signencrypt/**", "/decryptverify/**", "/sendreceive/**",
                                "/user/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin((form) -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .logout((logout) -> logout.permitAll());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user =
                User.withDefaultPasswordEncoder()
                        .username("user")
                        .password("password")
                        .roles("USER")
                        .build();

        return new InMemoryUserDetailsManager(user);
    }*/

    /*@Configuration
    @Order(1)
    public static class ApiWebSecurityConfigurationAdapter {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf().disable()
                    .authorizeHttpRequests((authz) -> authz
                            .requestMatchers("/api/**")
                            .hasAnyRole(EnumAuthRole.ROLE_ADMIN.getSuffix())
                            .anyRequest()
                            .authenticated()
                    )
                    .httpBasic(withDefaults());
            return http.build();
        }*/

//        protected void configure(HttpSecurity http) throws Exception {
//            http.csrf().disable()
//                .antMatcher("/api/**")
//                .authorizeRequests()
//                .anyRequest()
//                .hasAnyRole(EnumAuthRole.ROLE_ADMIN.getSuffix(), EnumAuthRole.ROLE_ADMIN.getSuffix())
//                .and()
//                .httpBasic();
//        }
//    }

    @Configuration
    public static class FormLoginWebSecurityConfigurerAdapter {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                    .authorizeHttpRequests((request) -> request
                            .requestMatchers("/", "/konfiguration/**,", "/minimalkonfiguration/**",
                                    "/openkimkeystore/**", "/konnektor/**", "/log/**", "/pop3log/**", "/smtplog/**",
                                    "/dashboard/**", "/konnvzd/**", "/konnwebservice/**", "/konnntp/**",
                                    "/mailanalyzer/**", "/signencrypt/**", "/decryptverify/**", "/sendreceive/**",
                                    "/user/**")
                            .hasAnyRole(EnumAuthRole.ROLE_ADMIN.getSuffix(), EnumAuthRole.ROLE_MONITORING.getSuffix())
                            .anyRequest().authenticated()
                    )
                    .formLogin((form) -> form
                            .loginPage("/login")
                            .permitAll()
                    )
                    .logout((logout) -> logout.permitAll());
            return http.build();
        }
    }

    /*@Configuration
    public class FormLoginWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .invalidSessionUrl("/login")
                .and()
                .csrf()
                .and()
                .authorizeRequests()
                .antMatchers("/dev/**")
                .permitAll()
                .and()
                .authorizeRequests()
                .antMatchers("/", "/konfiguration/**,", "/minimalkonfiguration/**", "/openkimkeystore/**", "/konnektor/**", "/log/**", "/pop3log/**", "/smtplog/**", "/dashboard/**", "/konnvzd/**", "/konnwebservice/**", "/konnntp/**", "/mailanalyzer/**", "/signencrypt/**", "/decryptverify/**", "/sendreceive/**", "/user/**")
                .hasAnyRole(EnumAuthRole.ROLE_ADMIN.getSuffix(), EnumAuthRole.ROLE_MONITORING.getSuffix())
                .and()
                .formLogin()
                .loginPage("/login")
                .permitAll()
                .and()
                .logout()
                .permitAll()
                .logoutSuccessUrl("/login");
        }*/

        @Autowired
        protected void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.userDetailsService(userService.create());
        }
}


