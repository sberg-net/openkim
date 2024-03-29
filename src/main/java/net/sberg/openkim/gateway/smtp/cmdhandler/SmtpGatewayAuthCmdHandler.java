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
package net.sberg.openkim.gateway.smtp.cmdhandler;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.smtp.EnumSmtpGatewayState;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.EnumGatewayTIMode;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsRequestOperation;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsResult;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsResultContainer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.smtp.AuthenticatingSMTPClient;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.james.core.Username;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.esmtp.EhloExtension;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.*;
import org.bouncycastle.util.IPAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Type;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmtpGatewayAuthCmdHandler implements CommandHandler<SMTPSession>, EhloExtension, ExtensibleHandler, MailParametersHook {
    private static final Collection<String> COMMANDS = ImmutableSet.of("AUTH");
    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayAuthCmdHandler.class);
    private static final String[] MAIL_PARAMS = {"AUTH"};
    private static final String AUTH_TYPES_DELIMITER = " ";

    private static final Response AUTH_ABORTED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH) + " Authentication aborted").immutable();
    private static final Response ALREADY_AUTH = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " User has previously authenticated. "
                                                                                            + " Further authentication is not required!").immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Usage: AUTH (authentication type) <challenge>").immutable();
    private static final Response AUTH_READY_PLAIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "OK. Continue authentication").immutable();
    private static final Response AUTH_READY_USERNAME_LOGIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "VXNlcm5hbWU6").immutable(); // base64 encoded "Username:"
    private static final Response AUTH_READY_PASSWORD_LOGIN = new SMTPResponse(SMTPRetCode.AUTH_READY, "UGFzc3dvcmQ6").immutable(); // base64 encoded "Password:
    private static final Response AUTH_FAILED = new SMTPResponse(SMTPRetCode.AUTH_FAILED, "Authentication Failed").immutable();
    private static final Response UNKNOWN_AUTH_TYPE = new SMTPResponse(SMTPRetCode.PARAMETER_NOT_IMPLEMENTED, "Security features not supported").immutable();

    private PipelineService pipelineService;

    private SmtpGatewayAuthCmdHandler() {
    }

    public SmtpGatewayAuthCmdHandler(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    private abstract static class AbstractSMTPLineHandler implements LineHandler<SMTPSession> {

        @Override
        public Response onLine(SMTPSession session, byte[] line) {
            return handleCommand(session, new String(line, session.getCharset()));
        }

        private Response handleCommand(SMTPSession session, String line) {
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

        protected abstract Response onCommand(SMTPSession session, String l);
    }


    /**
     * The text string for the SMTP AUTH type PLAIN.
     */
    protected static final String AUTH_TYPE_PLAIN = "PLAIN";

    /**
     * The text string for the SMTP AUTH type LOGIN.
     */
    protected static final String AUTH_TYPE_LOGIN = "LOGIN";

    /**
     * The text string for the SMTP AUTH type OAUTHBEARER.
     */
    protected static final String AUTH_TYPE_OAUTHBEARER = "OAUTHBEARER";
    protected static final String AUTH_TYPE_XOAUTH2 = "XOAUTH2";

    /**
     * The AuthHooks
     */
    private List<AuthHook> hooks;

    private List<HookResultHook> rHooks;

    private TimeMetric timeMetric;

    /**
     * handles AUTH command
     */
    @Override
    public Response onCommand(SMTPSession session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((SmtpGatewaySession) session).getLogger());
        timeMetric = gatewayMetricFactory.timer("smtp-auth");
        session.stopDetectingCommandInjection();
        return doAUTH(session, request.getArgument());
    }

    /**
     * Handler method called upon receipt of a AUTH command.
     * Handles client authentication to the SMTP server.
     *
     * @param session  SMTP session
     * @param argument the argument passed in with the command by the SMTP client
     */
    private Response doAUTH(SMTPSession session, String argument) {
        ((SmtpGatewaySession) session).log("auth begins");
        if (session.getUsername() != null) {
            ((SmtpGatewaySession) session).log("auth ends - already auth");
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
            return ALREADY_AUTH;
        } else if (argument == null) {
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
            return SYNTAX_ERROR;
        } else {
            String initialResponse = null;
            if (argument.indexOf(" ") > 0) {
                initialResponse = argument.substring(argument.indexOf(" ") + 1);
                argument = argument.substring(0, argument.indexOf(" "));
            }
            String authType = argument.toUpperCase(Locale.US);
            if (authType.equals(AUTH_TYPE_PLAIN) && session.getConfiguration().isPlainAuthEnabled()) {
                String userpass;
                if (initialResponse == null) {
                    session.pushLineHandler(new SmtpGatewayAuthCmdHandler.AbstractSMTPLineHandler() {
                        @Override
                        protected Response onCommand(SMTPSession session, String l) {
                            return doPlainAuthPass(session, l);
                        }
                    });
                    return AUTH_READY_PLAIN;
                } else {
                    userpass = initialResponse.trim();
                    return doPlainAuthPass(session, userpass);
                }
            } else if (authType.equals(AUTH_TYPE_LOGIN) && session.getConfiguration().isPlainAuthEnabled()) {

                if (initialResponse == null) {
                    session.pushLineHandler(new SmtpGatewayAuthCmdHandler.AbstractSMTPLineHandler() {
                        @Override
                        protected Response onCommand(SMTPSession session, String l) {
                            return doLoginAuthPass(session, l);
                        }
                    });
                    return AUTH_READY_USERNAME_LOGIN;
                } else {
                    String user = initialResponse.trim();
                    return doLoginAuthPass(session, user);
                }
            } else {
                return doUnknownAuth(session, authType);
            }
        }
    }

    /**
     * Carries out the Plain AUTH SASL exchange.
     * <p>
     * According to RFC 2595 the client must send: [authorize-id] \0 authenticate-id \0 password.
     * <p>
     * >>> AUTH PLAIN dGVzdAB0ZXN0QHdpei5leGFtcGxlLmNvbQB0RXN0NDI=
     * Decoded: test\000test@wiz.example.com\000tEst42
     * <p>
     * >>> AUTH PLAIN dGVzdAB0ZXN0AHRFc3Q0Mg==
     * Decoded: test\000test\000tEst42
     *
     * @param session SMTP session object
     * @param line    the initial response line passed in with the AUTH command
     */
    private Response doPlainAuthPass(SMTPSession session, String line) {
        ((SmtpGatewaySession) session).log("plain auth begins");
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
                } catch (java.util.NoSuchElementException ignored) {
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
            ((SmtpGatewaySession) session).getLogger().parseUsername(user);
            user = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername();
        } catch (Exception e) {
            log.error("error on separate user details: " + user + " - " + session.getSessionID(), e);
            ((SmtpGatewaySession) session).log("plain auth ends - error");

            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();

            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Invalid command arguments").immutable();
        }

        Response response = doAuthTest(session, Username.of(user), pass, "PLAIN");

        session.popLineHandler();

        ((SmtpGatewaySession) session).log("plain auth ends");

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

    /**
     * Carries out the Login AUTH SASL exchange.
     *
     * @param session SMTP session object
     * @param user    the user passed in with the AUTH command
     */
    private Response doLoginAuthPass(SMTPSession session, String user) {
        ((SmtpGatewaySession) session).log("login auth begins - handle user");
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
            ((SmtpGatewaySession) session).getLogger().parseUsername(user);
            user = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername();
        } catch (Exception e) {
            log.error("error on separate user details: " + user + " - " + session.getSessionID(), e);
            ((SmtpGatewaySession) session).log("login auth ends - handle user - error");

            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();

            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Invalid command arguments").immutable();
        }

        session.popLineHandler();

        session.pushLineHandler(new SmtpGatewayAuthCmdHandler.AbstractSMTPLineHandler() {

            private Username username;

            public LineHandler<SMTPSession> setUsername(Username username) {
                this.username = username;
                return this;
            }

            @Override
            protected Response onCommand(SMTPSession session, String l) {
                return doLoginAuthPassCheck(session, username, l);
            }
        }.setUsername(Username.of(user)));

        ((SmtpGatewaySession) session).log("login auth ends - handle user");

        return AUTH_READY_PASSWORD_LOGIN;
    }

    private Response doLoginAuthPassCheck(SMTPSession session, Username username, String pass) {
        ((SmtpGatewaySession) session).log("login auth begins - handle password");
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
        ((SmtpGatewaySession) session).log("login auth ends - handle password");
        Response response = doAuthTest(session, username, pass, "LOGIN");

        timeMetric.stopAndPublish();
        session.needsCommandInjectionDetection();

        return response;
    }

    protected Response doAuthTest(SMTPSession session, Username username, String pass, String authType) {
        ((SmtpGatewaySession) session).log("auth begins - smtp client auth");
        if ((username == null) || (pass == null)) {
            ((SmtpGatewaySession) session).log("auth begins - smtp client auth - error");
            return new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, "Could not decode parameters for AUTH " + authType);
        }

        try {
            Konfiguration konfiguration = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getKonfiguration();

            AuthenticatingSMTPClient client = null;

            //instantiate client
            if (konfiguration.getGatewayTIMode().equals(EnumGatewayTIMode.FULLSTACK)) {
                String certfileName = ICommonConstants.BASE_DIR + File.separator + konfiguration.getFachdienstCertFilename();
                char[] passCharArray = konfiguration.getFachdienstCertAuthPwd().toCharArray();
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(new FileInputStream(certfileName), passCharArray);

                SSLContext sslContext = new SSLContextBuilder()
                    .loadKeyMaterial(keyStore, passCharArray)
                    .loadTrustMaterial(keyStore, new TrustStrategy() {
                        @Override
                        public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                            return true;
                        }
                    })
                    .build();

                client = new AuthenticatingSMTPClient(true, sslContext);
                client.setDefaultTimeout(((SmtpGatewaySession) session).getSmtpClientIdleTimeoutInSeconds() * 1000);
                client.setConnectTimeout(((SmtpGatewaySession) session).getSmtpClientIdleTimeoutInSeconds() * 1000);
                ((SmtpGatewaySession) session).setSmtpClient(client);
            }
            else {
                client = new AuthenticatingSMTPClient("TLS", true);
                client.setDefaultTimeout(((SmtpGatewaySession) session).getSmtpClientIdleTimeoutInSeconds() * 1000);
                client.setConnectTimeout(((SmtpGatewaySession) session).getSmtpClientIdleTimeoutInSeconds() * 1000);
                ((SmtpGatewaySession) session).setSmtpClient(client);
            }

            String mailServerHost = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost();

            //instantiate mailserver domain check
            if (konfiguration.getGatewayTIMode().equals(EnumGatewayTIMode.FULLSTACK)) {
                if (!IPAddress.isValid(((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost())) {

                    ((SmtpGatewaySession) session).log("make a dns request for: " + ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost());

                    DnsResult dnsResult = null;
                    AtomicInteger failedCounter = new AtomicInteger();
                    DnsRequestOperation dnsRequestOperation = (DnsRequestOperation) pipelineService.getOperation(DnsRequestOperation.BUILTIN_VENDOR+"."+DnsRequestOperation.NAME);
                    DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(((SmtpGatewaySession) session).getLogger());
                    defaultPipelineOperationContext.setEnvironmentValue(DnsRequestOperation.NAME, DnsRequestOperation.ENV_DOMAIN, ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost());
                    defaultPipelineOperationContext.setEnvironmentValue(DnsRequestOperation.NAME, DnsRequestOperation.ENV_RECORD_TYPE, Type.string(Type.A));

                    dnsRequestOperation.execute(
                        defaultPipelineOperationContext,
                        context -> {
                            ((SmtpGatewaySession) session).log("dns request finished for: " + ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost());
                        },
                        (context, e) -> {
                            log.error("dns request failed for: " + ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost(), e);
                            failedCounter.incrementAndGet();
                        }
                    );
                    DnsResultContainer dnsResultContainer = (DnsResultContainer) defaultPipelineOperationContext.getEnvironmentValue(DnsRequestOperation.NAME, DnsRequestOperation.ENV_DNS_RESULT);
                    if (failedCounter.get() > 0 || dnsResultContainer == null || dnsResultContainer.isError()) {
                        throw new IllegalStateException("ip-address for domain " + ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost() + " not found");
                    }
                    if (!dnsResultContainer.isError() && dnsResultContainer.getResult().size() >= 1) {
                        dnsResult = dnsResultContainer.getResult().get(0);
                    }
                    if (dnsResult == null) {
                        throw new IllegalStateException("ip-address for domain " + ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost() + " not found");
                    }

                    mailServerHost = dnsResult.getAddress();
                } else {
                    mailServerHost = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost();
                    ((SmtpGatewaySession) session).log("DO NOT make a dns request. is an ip-address: " + mailServerHost);
                }
            }
            else {
                mailServerHost = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerHost();
            }

            try {
                ((SmtpGatewaySession) session).log("connect to " + mailServerHost);
                client.connect(mailServerHost, Integer.parseInt(((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerPort()));
            } catch (Exception e) {
                log.error("erron on connecting the mta - " + session.getSessionID(), e);
                ((SmtpGatewaySession) session).log("auth ends - smtp client auth - connect error");
                return new SMTPResponse(SMTPRetCode.AUTH_TEMPORARY_ERROR, DSNStatus.getStatus(DSNStatus.NETWORK, DSNStatus.SECURITY_OTHER) + " Temporary authentication failure").immutable();
            }

            client.login();

            try {
                ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().setMailServerPassword(pass);
                boolean res = client.auth(AuthenticatingSMTPClient.AUTH_METHOD.LOGIN, ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext().getMailServerUsername(), pass);
                if (res) {
                    ((SmtpGatewaySession) session).setGatewayState(EnumSmtpGatewayState.PROXY);
                    ((SmtpGatewaySession) session).log("auth ends - smtp client auth - success");
                    return calcDefaultSMTPResponse(HookResult.OK);
                } else {
                    ((SmtpGatewaySession) session).log("auth ends - smtp client auth - auth error");
                    return new SMTPResponse(SMTPRetCode.AUTH_FAILED, DSNStatus.getStatus(DSNStatus.PERMANENT, "7.8") + " Authentication credentials invalid").immutable();
                }
            } catch (Exception e) {
                log.error("erron on authenticating the mta - " + session.getSessionID(), e);
                ((SmtpGatewaySession) session).log("auth ends - smtp client auth - auth error");
                return new SMTPResponse(SMTPRetCode.AUTH_FAILED, DSNStatus.getStatus(DSNStatus.PERMANENT, "7.8") + " Authentication credentials invalid").immutable();
            }
        } catch (Exception e) {
            log.error("error on authenticating the mta - " + session.getSessionID(), e);
            ((SmtpGatewaySession) session).log("auth ends - smtp client auth - error");
            return calcDefaultSMTPResponse(HookResult.DENY);
        }
    }

    /**
     * Calculate the SMTPResponse for the given result
     *
     * @param result the HookResult which should converted to SMTPResponse
     * @return the calculated SMTPResponse for the given HookResult
     */
    protected Response calcDefaultSMTPResponse(HookResult result) {
        if (result != null) {
            HookReturnCode returnCode = result.getResult();

            String smtpReturnCode = Optional.ofNullable(result.getSmtpRetCode())
                .or(() -> retrieveDefaultSmtpReturnCode(returnCode))
                .orElse(null);

            String smtpDescription = Optional.ofNullable(result.getSmtpDescription())
                .or(() -> retrieveDefaultSmtpDescription(returnCode))
                .orElse(null);

            if (HookReturnCode.Action.ACTIVE_ACTIONS.contains(returnCode.getAction())) {
                SMTPResponse response = new SMTPResponse(smtpReturnCode, smtpDescription);

                if (returnCode.isDisconnected()) {
                    response.setEndSession(true);
                }
                return response;
            } else if (returnCode.isDisconnected()) {
                return Response.DISCONNECT;
            }
        }
        return null;

    }

    private Optional<String> retrieveDefaultSmtpDescription(HookReturnCode returnCode) {
        switch (returnCode.getAction()) {
            case DENY:
                return Optional.of("Authentication Failed");
            case DENYSOFT:
                return Optional.of("Temporary problem. Please try again later");
            case OK:
                return Optional.of("Authentication Successful");
            case DECLINED:
            case NONE:
                break;
        }
        return Optional.empty();
    }

    private Optional<String> retrieveDefaultSmtpReturnCode(HookReturnCode returnCode) {
        switch (returnCode.getAction()) {
            case DENY:
                return Optional.of(SMTPRetCode.AUTH_FAILED);
            case DENYSOFT:
                return Optional.of(SMTPRetCode.LOCAL_ERROR);
            case OK:
                return Optional.of(SMTPRetCode.AUTH_OK);
            case DECLINED:
            case NONE:
                break;
        }
        return Optional.empty();
    }

    /**
     * Handles the case of an unrecognized auth type.
     *
     * @param authType the unknown auth type
     */
    private Response doUnknownAuth(SMTPSession session, String authType) {
        log.error("auth ends - unknown auth type " + authType);
        ((SmtpGatewaySession) session).log("auth ends - unknown auth type " + authType + " - error");

        timeMetric.stopAndPublish();
        session.needsCommandInjectionDetection();

        return UNKNOWN_AUTH_TYPE;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public List<String> getImplementedEsmtpFeatures(SMTPSession session) {
        if (session.isAuthAnnounced()) {
            ImmutableList.Builder<String> authTypesBuilder = ImmutableList.builder();
            if (session.getConfiguration().isPlainAuthEnabled()) {
                authTypesBuilder.add(AUTH_TYPE_LOGIN, AUTH_TYPE_PLAIN);
            }
            if (session.getConfiguration().saslConfiguration().isPresent()) {
                authTypesBuilder.add(AUTH_TYPE_OAUTHBEARER);
                authTypesBuilder.add(AUTH_TYPE_XOAUTH2);
            }
            ImmutableList<String> authTypes = authTypesBuilder.build();
            if (authTypes.isEmpty()) {
                return Collections.emptyList();
            }
            String joined = Joiner.on(AUTH_TYPES_DELIMITER).join(authTypes);
            return ImmutableList.of("AUTH " + joined, "AUTH=" + joined);
        }
        return Collections.emptyList();
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> classes = new ArrayList<>(1);
        classes.add(AuthHook.class);
        return classes;
    }


    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (HookResultHook.class.equals(interfaceName)) {
            this.rHooks = (List<HookResultHook>) extension;
        }
    }


    /**
     * Return a list which holds all hooks for the cmdHandler
     *
     * @return list containing all hooks for the cmd handler
     */
    protected List<AuthHook> getHooks() {
        return hooks;
    }

    @Override
    public HookResult doMailParameter(SMTPSession session, String paramName, String paramValue) {
        // Ignore the AUTH command.
        return HookResult.DECLINED;
    }

    @Override
    public String[] getMailParamNames() {
        return MAIL_PARAMS;
    }
}
