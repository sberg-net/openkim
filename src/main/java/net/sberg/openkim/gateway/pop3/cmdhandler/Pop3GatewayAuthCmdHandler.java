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
package net.sberg.openkim.gateway.pop3.cmdhandler;

import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.mail.EnumMailAuthMethod;
import net.sberg.openkim.mail.EnumMailConnectionSecurity;
import net.sberg.openkim.mail.MailUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.pop3.EnumPop3GatewayState;
import net.sberg.openkim.gateway.pop3.Pop3GatewaySession;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsResult;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsResultContainer;
import net.sberg.openkim.konnektor.dns.DnsService;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.core.AbstractPOP3CommandHandler;
import org.apache.james.protocols.pop3.core.CapaCapability;
import org.bouncycastle.util.IPAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Type;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Pop3GatewayAuthCmdHandler extends AbstractPOP3CommandHandler implements CapaCapability {

    private static final Set<String> CAPS = ImmutableSet.of("SASL PLAIN LOGIN");
    private static final Collection<String> COMMANDS = ImmutableSet.of("AUTH");
    private static final Logger log = LoggerFactory.getLogger(Pop3GatewayAuthCmdHandler.class);

    private static final Response AUTH_ABORTED = new POP3Response(POP3Response.ERR_RESPONSE, "Authentication aborted").immutable();
    private static final Response ALREADY_AUTH = new POP3Response(POP3Response.ERR_RESPONSE, "User has previously authenticated. Further authentication is not required!").immutable();

    private static final Response UNKNOWN_AUTH_TYPE = new POP3Response(POP3Response.ERR_RESPONSE, "Security features not supported").immutable();

    private TimeMetric timeMetric;
    private final DnsService dnsService;

    public Pop3GatewayAuthCmdHandler(DnsService dnsService) {
        this.dnsService = dnsService;
    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((Pop3GatewaySession) session).getLogger());
        timeMetric = gatewayMetricFactory.timer("pop3-auth");
        session.stopDetectingCommandInjection();
        return doExecute(session, request);
    }

    private Response doExecute(POP3Session session, Request request) {
        ((Pop3GatewaySession) session).log("auth begins");
        String argument = request.getArgument();
        if (session.getUsername() != null) {
            ((Pop3GatewaySession) session).log("auth ends - already auth");
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
            return ALREADY_AUTH;
        } else if (argument == null) {
            POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, "");
            response.appendLine("PLAIN");
            response.appendLine("LOGIN");
            response.appendLine(".");
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
            return response.immutable();
        } else {
            String initialResponse = null;
            if (argument.indexOf(" ") > 0) {
                initialResponse = argument.substring(argument.indexOf(" ") + 1);
                argument = argument.substring(0, argument.indexOf(" "));
            }
            String authType = argument.toUpperCase(Locale.US);
            if (authType.equals(AUTH_TYPE_PLAIN)) {
                String userpass;
                if (initialResponse == null) {
                    session.pushLineHandler(new Pop3GatewayAuthCmdHandler.AbstractPOP3LineHandler() {
                        @Override
                        protected Response onCommand(POP3Session session, String l) {
                            return doPlainAuthPass(session, l);
                        }
                    });
                    return new POP3Response(POP3Response.OK_RESPONSE, "Continue authentication").immutable();
                } else {
                    userpass = initialResponse.trim();
                    return doPlainAuthPass(session, userpass);
                }
            } else if (authType.equals(AUTH_TYPE_LOGIN)) {

                if (initialResponse == null) {
                    session.pushLineHandler(new Pop3GatewayAuthCmdHandler.AbstractPOP3LineHandler() {
                        @Override
                        protected Response onCommand(POP3Session session, String l) {
                            return doLoginAuthPass(session, l);
                        }
                    });
                    return new POP3Response("+", "VXNlcm5hbWU6").immutable();
                } else {
                    String user = initialResponse.trim();
                    return doLoginAuthPass(session, user);
                }
            } else {
                return doUnknownAuth(session, authType);
            }
        }
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        return CAPS;
    }

    private abstract static class AbstractPOP3LineHandler implements LineHandler<POP3Session> {

        @Override
        public Response onLine(POP3Session session, ByteBuffer line) {
            String charset = session.getCharset().name();
            try {
                byte[] l;
                if (line.hasArray()) {
                    l = line.array();
                } else {
                    l = new byte[line.remaining()];
                    line.get(l);
                }
                return handleCommand(session, new String(l, charset));
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("No " + charset + " support!");
            }

        }

        private Response handleCommand(POP3Session session, String line) {
            // See JAMES-939

            // According to RFC2554:
            // "If the client wishes to cancel an authentication exchange, it issues a line with a single "*".
            // If the server receives such an answer, it MUST reject the AUTH
            // command by sending a 501 reply."
            if (line.equals("*\r\n")) {
                session.popLineHandler();
                return AUTH_ABORTED;
            }
            return onCommand(session, line);
        }

        protected abstract Response onCommand(POP3Session session, String l);
    }


    /**
     * The text string for the SMTP AUTH type PLAIN.
     */
    protected static final String AUTH_TYPE_PLAIN = "PLAIN";

    /**
     * The text string for the SMTP AUTH type LOGIN.
     */
    protected static final String AUTH_TYPE_LOGIN = "LOGIN";

    private Response doPlainAuthPass(POP3Session session, String line) {
        ((Pop3GatewaySession) session).log("plain auth begins");
        String user = null;
        String pass = null;
        try {
            String userpass = decodeBase64(line);
            if (userpass != null) {
                /*  See: RFC 2595, Section 6
                    The mechanism consists of a single message from the client to the
                    server.  The client sends the authorization identity (identity to
                    login as), followed by a US-ASCII NUL character, followed by the
                    authentication identity (identity whose password will be used),
                    followed by a US-ASCII NUL character, followed by the clear-text
                    password.  The client may leave the authorization identity empty to
                    indicate that it is the same as the authentication identity.

                    The server will verify the authentication identity and password with
                    the system authentication database and verify that the authentication
                    credentials permit the client to login as the authorization identity.
                    If both steps succeed, the user is logged in.
                */
                StringTokenizer authTokenizer = new StringTokenizer(userpass, "\0");
                String authorizeId = authTokenizer.nextToken();  // Authorization Identity
                user = authTokenizer.nextToken();                 // Authentication Identity
                try {
                    pass = authTokenizer.nextToken();             // Password
                } catch (NoSuchElementException ignored) {
                    // If we got here, this is what happened.  RFC 2595
                    // says that "the client may leave the authorization
                    // identity empty to indicate that it is the same as
                    // the authentication identity."  As noted above,
                    // that would be represented as a decoded string of
                    // the form: "\0authenticate-id\0password".  The
                    // first call to nextToken will skip the empty
                    // authorize-id, and give us the authenticate-id,
                    // which we would store as the authorize-id.  The
                    // second call will give us the password, which we
                    // think is the authenticate-id (user).  Then when
                    // we ask for the password, there are no more
                    // elements, leading to the exception we just
                    // caught.  So we need to move the user to the
                    // password, and the authorize_id to the user.
                    pass = user;
                    user = authorizeId;
                }

                authTokenizer = null;
            }
        } catch (Exception e) {
            // Ignored - this exception in parsing will be dealt
            // with in the if clause below
        }
        // Authenticate user
        try {
            ((Pop3GatewaySession) session).getLogger().parseUsername(user);
            user = ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername();
            session.setHandlerState(POP3Session.AUTHENTICATION_USERSET);
        } catch (Exception e) {
            log.error("error on separate user details: " + user, e);
            ((Pop3GatewaySession) session).log("plain auth ends - error");

            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();

            return new POP3Response(POP3Response.ERR_RESPONSE, "Invalid command arguments").immutable();
        }

        Response response = doAuthTest(session, Username.of(user), pass, "PLAIN");

        session.popLineHandler();

        ((Pop3GatewaySession) session).log("plain auth ends");

        timeMetric.stopAndPublish();
        session.needsCommandInjectionDetection();

        return response;
    }

    private String decodeBase64(String line) {
        if (line != null) {
            String lineWithoutTrailingCrLf = StringUtils.replace(line, "\r\n", "");
            return new String(Base64.getDecoder().decode(lineWithoutTrailingCrLf), StandardCharsets.UTF_8);
        }
        return null;
    }

    private Response doLoginAuthPass(POP3Session session, String user) {
        ((Pop3GatewaySession) session).log("login auth begins - handle user");
        if (user != null) {
            try {
                user = decodeBase64(user);
            } catch (Exception e) {
                // Ignored - this parse error will be
                // addressed in the if clause below
                user = null;
            }
        }

        try {
            ((Pop3GatewaySession) session).getLogger().parseUsername(user);
            user = ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername();
            session.setHandlerState(POP3Session.AUTHENTICATION_USERSET);
        } catch (Exception e) {
            log.error("error on separate user details: " + user, e);
            ((Pop3GatewaySession) session).log("login auth ends - handle user - error");

            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();

            return new POP3Response(POP3Response.ERR_RESPONSE, "Invalid command arguments").immutable();
        }

        session.popLineHandler();

        session.pushLineHandler(new Pop3GatewayAuthCmdHandler.AbstractPOP3LineHandler() {

            private Username username;

            public LineHandler<POP3Session> setUsername(Username username) {
                this.username = username;
                return this;
            }

            @Override
            protected Response onCommand(POP3Session session, String l) {
                return doLoginAuthPassCheck(session, username, l);
            }
        }.setUsername(Username.of(user)));

        ((Pop3GatewaySession) session).log("login auth ends - handle user");

        return new POP3Response("+", "UGFzc3dvcmQ6").immutable();
    }

    private Response doLoginAuthPassCheck(POP3Session session, Username username, String pass) {
        ((Pop3GatewaySession) session).log("login auth begins - handle password");
        if (pass != null) {
            try {
                pass = decodeBase64(pass);
            } catch (Exception e) {
                // Ignored - this parse error will be
                // addressed in the if clause below
                pass = null;
            }
        }

        session.popLineHandler();

        // Authenticate user
        ((Pop3GatewaySession) session).log("login auth ends - handle password");
        Response response = doAuthTest(session, username, pass, "LOGIN");

        timeMetric.stopAndPublish();
        session.needsCommandInjectionDetection();

        return response;
    }

    protected Response doAuthTest(POP3Session session, Username username, String pass, String authType) {
        ((Pop3GatewaySession) session).log("auth begins - pop3 client auth");
        if ((username == null) || (pass == null)) {
            ((Pop3GatewaySession) session).log("auth ends - pop3 client auth - error");
            return new POP3Response(POP3Response.ERR_RESPONSE, "Could not decode parameters for AUTH " + authType);
        }

        try {

            String mailServerIpAddress;
            if (!IPAddress.isValid(((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost())) {

                ((Pop3GatewaySession) session).log("make a dns request for: " + ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost());

                DnsResult dnsResult = null;
                DnsResultContainer dnsResultContainer = dnsService.request(
                    ((Pop3GatewaySession) session).getLogger(),
                    ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost(),
                    Type.string(Type.A)
                );
                if (!dnsResultContainer.isError() && dnsResultContainer.getResult().size() >= 1) {
                    dnsResult = dnsResultContainer.getResult().get(0);
                }

                if (dnsResult == null) {
                    throw new IllegalStateException("ip-address for domain " + ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost() + " not found");
                }

                mailServerIpAddress = dnsResult.getAddress();
            } else {
                mailServerIpAddress = ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost();
                ((Pop3GatewaySession) session).log("DO NOT make a dns request. is an ip-address: " + mailServerIpAddress);
            }

            ((Pop3GatewaySession) session).log("connect to " + mailServerIpAddress);

            Konfiguration konfiguration = ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getKonfiguration();
            Properties props = new Properties();
            Session pop3ClientSession = MailUtils.createPop3ClientSession(
                props,
                EnumMailConnectionSecurity.SSLTLS,
                EnumMailAuthMethod.NORMALPWD,
                mailServerIpAddress,
                ((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerPort(),
                konfiguration.getPop3ClientIdleTimeoutInSeconds(),
                konfiguration.getFachdienstCertFilename(),
                konfiguration.getFachdienstCertAuthPwd(),
                true
            );

            Store store = pop3ClientSession.getStore("pop3");
            store.connect(((Pop3GatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername(), pass);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            ((Pop3GatewaySession) session).setPop3ClientFolder(inbox);
            ((Pop3GatewaySession) session).setPop3ClientStore(store);

            ((Pop3GatewaySession) session).setGatewayState(EnumPop3GatewayState.PROXY);
            session.setHandlerState(POP3Session.TRANSACTION);

            ((Pop3GatewaySession) session).log("auth ends - pop3 client auth - success");

            return new POP3Response(POP3Response.OK_RESPONSE, "Logged in.").immutable();
        } catch (Exception e) {
            log.error("error on authenticating the mta", e);
            ((Pop3GatewaySession) session).log("auth ends - pop3 client auth - error");
            return new POP3Response(POP3Response.ERR_RESPONSE, "Authentication credentials invalid or Temporary authentication failure").immutable();
        }
    }

    /**
     * Handles the case of an unrecognized auth type.
     *
     * @param authType the unknown auth type
     */
    private Response doUnknownAuth(POP3Session session, String authType) {
        log.error("auth ends - unknown auth type " + authType);
        ((Pop3GatewaySession) session).log("auth ends - unknown auth type " + authType + " - error");

        timeMetric.stopAndPublish();
        session.needsCommandInjectionDetection();

        return UNKNOWN_AUTH_TYPE;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
