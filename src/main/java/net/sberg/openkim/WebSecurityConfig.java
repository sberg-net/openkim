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

import lombok.RequiredArgsConstructor;
import net.sberg.openkim.common.EnumAuthRole;
import net.sberg.openkim.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfig {

    final UserService userService;

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests((request) -> request
                        .anyRequest().hasRole(EnumAuthRole.ROLE_ADMIN.getSuffix())
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .invalidSessionUrl("/login")
                .and()
                .csrf()
                .and()
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
                .logout((logout) -> logout
                        .permitAll()
                        .logoutSuccessUrl("/login"));
        return http.build();
    }

    // ignore resource paths and /dev/* in spring secure to have access without rules
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/webjars/**", "/js/**", "/css/**",
                "/img/**", "/fonts/**", "/dev/**");
    }

    @Autowired
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userService.create());
    }
}


