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
package net.sberg.openkim.gateway.smtp.cmdhandler;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.common.x509.X509CertificateResult;
import net.sberg.openkim.gateway.smtp.AbstractGatewayHookableCmdHandler;
import net.sberg.openkim.gateway.smtp.EnumSmtpGatewayState;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konfiguration.konnektor.vzd.VzdService;
import net.sberg.openkim.log.DefaultLoggerContext;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.net.smtp.SMTPReply;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.RcptHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SmtpGatewayRcptCmdHandler extends AbstractGatewayHookableCmdHandler<RcptHook> implements
    CommandHandler<SMTPSession> {
    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayRcptCmdHandler.class);
    public static final ProtocolSession.AttachmentKey<MailAddress> CURRENT_RECIPIENT = ProtocolSession.AttachmentKey.of("CURRENT_RECIPIENT", MailAddress.class);
    private static final Collection<String> COMMANDS = ImmutableSet.of("RCPT");
    private static final Response MAIL_NEEDED = new SMTPResponse(
        SMTPRetCode.BAD_SEQUENCE,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " Need MAIL before RCPT"
    ).immutable();
    private static final Response SYNTAX_ERROR_ARGS = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Usage: RCPT TO:<recipient>"
    ).immutable();
    private static final Response SYNTAX_ERROR_DELIVERY = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_SYNTAX) + " Syntax error in parameters or arguments"
    ).immutable();
    private static final Response SYNTAX_ERROR_ADDRESS = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_MAILBOX,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_SYNTAX) + " Syntax error in recipient address"
    ).immutable();
    private static final Response MAILBOX_PERM_UNAVAILABLE = new SMTPResponse(
        SMTPRetCode.MAILBOX_PERM_UNAVAILABLE,
        ""
    ).immutable();

    private VzdService vzdService;

    private SmtpGatewayRcptCmdHandler() {
    }

    public SmtpGatewayRcptCmdHandler(VzdService vzdService) {
        this.vzdService = vzdService;
    }

    /**
     * Handler method called upon receipt of a RCPT command. Reads recipient.
     * Does some connection validation.
     *
     * @param session    SMTP session object
     * @param command    command passed
     * @param parameters parameters passed in with the command by the SMTP client
     */
    @Override
    protected Response doCoreCmd(SMTPSession session, String command, String parameters) {
        ((SmtpGatewaySession) session).log("rcpt begins");
        List<MailAddress> rcptColl = session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction).orElseGet(ArrayList::new);
        MailAddress recipientAddress = session.getAttachment(CURRENT_RECIPIENT, ProtocolSession.State.Transaction).orElse(MailAddress.nullSender());
        StringBuilder response = new StringBuilder();
        try {
            if (((SmtpGatewaySession) session).getGatewayState().equals(EnumSmtpGatewayState.CONNECT)) {
                response.append(DSNStatus.getStatus(5, "7.0")).append(" Authentication required");
                ((SmtpGatewaySession) session).log("rcpt ends - error");
                return new SMTPResponse("530", response);
            } else {

                DefaultLoggerContext loggerContext = ((SmtpGatewaySession) session).getLogger().getDefaultLoggerContext();

                try {
                    ((SmtpGatewaySession) session).log("load certs for rcpt");
                    List<X509CertificateResult> rcptCerts = vzdService.loadCerts(
                        ((SmtpGatewaySession) session).getLogger(),
                        new ArrayList<>(List.of(recipientAddress.asString().toLowerCase())),
                        false,
                        true
                    );
                    ((SmtpGatewaySession) session).getRecipientCerts().addAll(rcptCerts);
                    ((SmtpGatewaySession) session).log("load certs ending for rcpt");

                    if (loggerContext.getMailaddressCertErrorContext().isError(recipientAddress.asString().toLowerCase())) {
                        return MAILBOX_PERM_UNAVAILABLE;
                    }
                } catch (Exception e) {
                    ((SmtpGatewaySession) session).log("rcpt certs not available");
                    return MAILBOX_PERM_UNAVAILABLE;
                }

                int res = ((SmtpGatewaySession) session).getSmtpClient().rcpt("<" + recipientAddress.asString().toLowerCase() + ">");
                if (!SMTPReply.isPositiveCompletion(res)) {
                    response.append(" Recipient <")
                        .append(recipientAddress)
                        .append("> ")
                        .append(((SmtpGatewaySession) session).getSmtpClient().getReplyString());
                    ((SmtpGatewaySession) session).log("rcpt ends - error");
                    return new SMTPResponse(String.valueOf(res), response);
                }
            }
        } catch (Exception e) {
            log.error("error on doCoreCmd - set rcpt address in mta - " + session.getSessionID(), e);
            ((SmtpGatewaySession) session).log("rcpt ends - error");
            return new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, response);
        }

        rcptColl.add(recipientAddress);
        session.setAttachment(SMTPSession.RCPT_LIST, rcptColl, ProtocolSession.State.Transaction);

        response.append(DSNStatus.getStatus(2, "1.5"))
            .append(" Recipient <")
            .append(recipientAddress)
            .append("> OK");
        ((SmtpGatewaySession) session).log("rcpt ends");
        return new SMTPResponse("250", response);
    }

    /**
     * @param session  SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    @Override
    protected Response doFilterChecks(SMTPSession session, String command,
                                      String argument) {
        String recipient = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            recipient = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).isEmpty()) {
            return MAIL_NEEDED;
        } else if (argument == null
                   || !argument.toUpperCase(Locale.US).equals("TO")
                   || recipient == null) {
            return SYNTAX_ERROR_ARGS;
        }

        recipient = recipient.trim();
        int lastChar = recipient.lastIndexOf('>');
        // Check to see if any options are present and, if so, whether they
        // are correctly formatted
        // (separated from the closing angle bracket by a ' ').
        String rcptOptionString = null;
        if ((lastChar > 0)
            && (recipient.length() > lastChar + 2)
            && (recipient.charAt(lastChar + 1) == ' ')
        ) {
            rcptOptionString = recipient.substring(lastChar + 2);

            // Remove the options from the recipient
            recipient = recipient.substring(0, lastChar + 1);
        }
        if (session.getConfiguration().useAddressBracketsEnforcement()
            && (!recipient.startsWith("<") || !recipient.endsWith(">"))
        ) {
            log.info("Error parsing recipient address: Address did not start and end with < >{}", getContext(session, null, recipient));
            return SYNTAX_ERROR_DELIVERY;
        }
        MailAddress recipientAddress = null;
        // Remove < and >
        if (session.getConfiguration().useAddressBracketsEnforcement()
            || (recipient.startsWith("<") && recipient.endsWith(">"))
        ) {
            recipient = recipient.substring(1, recipient.length() - 1);
        }

        if (!recipient.contains("@")) {
            // set the default domain
            recipient = recipient
                        + "@"
                        + getDefaultDomain();
        }

        try {
            recipientAddress = new MailAddress(recipient);
        } catch (Exception pe) {
            log.info("Error parsing recipient address{}", getContext(session, recipientAddress, recipient), pe);
            /*
             * from RFC2822; 553 Requested action not taken: mailbox name
             * not allowed (e.g., mailbox syntax incorrect)
             */
            return SYNTAX_ERROR_ADDRESS;
        }

        if (rcptOptionString != null) {

            StringTokenizer optionTokenizer = new StringTokenizer(rcptOptionString, " ");
            while (optionTokenizer.hasMoreElements()) {
                String rcptOption = optionTokenizer.nextToken();
                Pair<String, String> parameter = parseParameter(rcptOption);

                if (!supportedParameter(parameter.getKey())) {
                    // Unexpected option attached to the RCPT command
                    log.debug(
                        "RCPT command had unrecognized/unexpected option {} with value {}{}",
                        parameter.getKey(),
                        parameter.getValue(),
                        getContext(session, recipientAddress, recipient)
                    );

                    return new SMTPResponse(
                        SMTPRetCode.PARAMETER_NOT_IMPLEMENTED,
                        "Unrecognized or unsupported option: " + parameter.getKey()
                    );
                }
            }
            optionTokenizer = null;
        }

        session.setAttachment(CURRENT_RECIPIENT, recipientAddress, ProtocolSession.State.Transaction);

        return null;
    }

    private String getContext(SMTPSession session, MailAddress recipientAddress, String recipient) {
        StringBuilder sb = new StringBuilder(128);
        if (null != recipientAddress) {
            sb.append(" [to:").append(recipientAddress.asString()).append(']');
        } else if (null != recipient) {
            sb.append(" [to:").append(recipient).append(']');
        }

        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());
        if (!sender.isNullSender()) {
            sb.append(" [from:").append(sender.asString()).append(']');
        }
        return sb.toString();
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Class<RcptHook> getHookInterface() {
        return RcptHook.class;
    }

    @Override
    protected HookResult callHook(RcptHook rawHook, SMTPSession session, String parametersString) {
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());
        Map<String, String> parameters = parseParameters(parametersString);
        MailAddress rcpt = session.getAttachment(CURRENT_RECIPIENT, ProtocolSession.State.Transaction).orElse(MailAddress.nullSender());

        return rawHook.doRcpt(session, sender, rcpt, parameters);
    }

    private Map<String, String> parseParameters(String rcptOptions) {
        return Splitter
            .on(' ')
            .splitToList(rcptOptions)
            .stream()
            .map(this::parseParameter)
            .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
    }

    private Pair<String, String> parseParameter(String rcptOption) {
        int equalIndex = rcptOption.indexOf('=');
        if (equalIndex > 0) {
            return Pair.of(
                rcptOption.substring(0, equalIndex).toUpperCase(Locale.US),
                rcptOption.substring(equalIndex + 1)
            );
        } else {
            return Pair.of(rcptOption, "");
        }
    }

    private boolean supportedParameter(String parameterName) {
        return getHooks().stream().anyMatch(rcptHook -> rcptHook.supportedParameters().contains(parameterName));
    }

    protected String getDefaultDomain() {
        return "localhost";
    }
}
