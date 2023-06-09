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

import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.gateway.smtp.AbstractGatewayHookableCmdHandler;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import net.sberg.openkim.konnektor.vzd.VzdService;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.MailHook;
import org.apache.james.protocols.smtp.hook.MailParametersHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.AddressException;
import java.util.*;

public class SmtpGatewayMailCmdHandler extends AbstractGatewayHookableCmdHandler<MailHook> {

    private VzdService vzdService;

    private SmtpGatewayMailCmdHandler() {
    }

    public SmtpGatewayMailCmdHandler(VzdService vzdService) {
        this.vzdService = vzdService;
    }

    private static final Collection<String> COMMANDS = ImmutableSet.of("MAIL");
    private static final Logger log = LoggerFactory.getLogger(SmtpGatewayMailCmdHandler.class);
    private static final Response SENDER_ALREADY_SPECIFIED = new SMTPResponse(
        SMTPRetCode.BAD_SEQUENCE,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " Sender already specified"
    ).immutable();
    private static final Response EHLO_HELO_NEEDED = new SMTPResponse(
        SMTPRetCode.BAD_SEQUENCE,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " Need HELO or EHLO before MAIL"
    ).immutable();
    private static final Response SYNTAX_ERROR_ARG = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Usage: MAIL FROM:<sender>"
    ).immutable();
    private static final Response SYNTAX_ERROR = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_SYNTAX_SENDER) + " Syntax error in MAIL command"
    ).immutable();
    private static final Response SYNTAX_ERROR_ADDRESS = new SMTPResponse(
        SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,
        DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.ADDRESS_SYNTAX_SENDER) + " Syntax error in sender address"
    ).immutable();
    /**
     * A map of parameterHooks
     */
    private Map<String, MailParametersHook> paramHooks;

    @Override
    public Response onCommand(SMTPSession session, Request request) {
        Response response = super.onCommand(session, request);
        // Check if the response was not ok
        if (!response.getRetCode().equals(SMTPRetCode.MAIL_OK)) {
            // cleanup the session
            session.removeAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction);
        }

        return response;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    protected Response doCoreCmd(SMTPSession session, String command, String parameters) {
        ((SmtpGatewaySession) session).log("mail begins");
        StringBuilder responseBuffer = new StringBuilder();
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());

        try {
            String senderAddress = sender.asString().toLowerCase();
            ((SmtpGatewaySession) session).setFromAddressStr(senderAddress);
            ((SmtpGatewaySession) session).getSmtpClient().mail("<" + senderAddress + ">");
        } catch (Exception e) {
            log.error("error on mail cmd - " + session.getSessionID(), e);
            ((SmtpGatewaySession) session).log("mail ends - error");
            return new SMTPResponse(SMTPRetCode.MAIL_UNDEFINDED, responseBuffer);
        }

        responseBuffer
            .append(DSNStatus.getStatus(DSNStatus.SUCCESS, DSNStatus.ADDRESS_OTHER))
            .append(" Sender <");
        if (!sender.isNullSender()) {
            responseBuffer.append(sender.asString());
        }
        responseBuffer.append("> OK");
        ((SmtpGatewaySession) session).log("mail ends");
        return new SMTPResponse(SMTPRetCode.MAIL_OK, responseBuffer);
    }

    @Override
    protected Response doFilterChecks(SMTPSession session, String command, String parameters) {
        return doMAILFilter(session, parameters);
    }

    /**
     * @param session  SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    private Response doMAILFilter(SMTPSession session, String argument) {
        String sender = null;

        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            sender = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).isPresent()) {
            return SENDER_ALREADY_SPECIFIED;
        } else if (session.getAttachment(SMTPSession.CURRENT_HELO_MODE, ProtocolSession.State.Connection).isEmpty()
                   && session.getConfiguration().useHeloEhloEnforcement()) {
            return EHLO_HELO_NEEDED;
        } else if (argument == null
                   || !argument.toUpperCase(Locale.US).equals("FROM")
                   || sender == null) {
            return SYNTAX_ERROR_ARG;
        } else {
            sender = sender.trim();
            // the next gt after the first lt ... AUTH may add more <>
            int lastChar = sender.indexOf('>', sender.indexOf('<'));
            // Check to see if any options are present and, if so, whether they
            // are correctly formatted
            // (separated from the closing angle bracket by a ' ').
            if ((lastChar > 0)
                && (sender.length() > lastChar + 2)
                && (sender.charAt(lastChar + 1) == ' ')
            ) {
                String mailOptionString = sender.substring(lastChar + 2);

                // Remove the options from the sender
                sender = sender.substring(0, lastChar + 1);

                StringTokenizer optionTokenizer = new StringTokenizer(mailOptionString, " ");
                while (optionTokenizer.hasMoreElements()) {
                    String mailOption = optionTokenizer.nextToken();
                    int equalIndex = mailOption.indexOf('=');
                    String mailOptionName = mailOption;
                    String mailOptionValue = "";
                    if (equalIndex > 0) {
                        mailOptionName = mailOption.substring(0, equalIndex).toUpperCase(Locale.US);
                        mailOptionValue = mailOption.substring(equalIndex + 1);
                    }

                    // Handle the SIZE extension keyword

                    if (paramHooks.containsKey(mailOptionName)) {
                        MailParametersHook hook = paramHooks.get(mailOptionName);
                        SMTPResponse res = calcDefaultSMTPResponse(hook.doMailParameter(session, mailOptionName, mailOptionValue));
                        if (res != null) {
                            return res;
                        }
                    } else {
                        // Unexpected option attached to the Mail command
                        log.debug("MAIL command had unrecognized/unexpected option {} with value {}", mailOptionName, mailOptionValue);
                    }
                }
            }
            if (session.getConfiguration().useAddressBracketsEnforcement()
                && (!sender.startsWith("<") || !sender.endsWith(">"))
            ) {
                log.info("Error parsing sender address: {}: did not start and end with < >", sender);
                return SYNTAX_ERROR;
            }
            try {
                MaybeSender senderAddress = toMaybeSender(removeBrackets(sender));
                // Store the senderAddress in session map
                session.setAttachment(SMTPSession.SENDER, senderAddress, ProtocolSession.State.Transaction);
            } catch (Exception pe) {
                log.info("Error parsing sender address: {}", sender, pe);
                return SYNTAX_ERROR_ADDRESS;
            }
        }
        return null;
    }

    private MaybeSender toMaybeSender(String senderAsString) throws AddressException {
        if (senderAsString.length() == 0) {
            // This is the <> case.
            return MaybeSender.nullSender();
        }
        if (senderAsString.equals("@")) {
            return MaybeSender.nullSender();
        }
        return MaybeSender.of(new MailAddress(appendDefaultDomainIfNeeded(senderAsString)));
    }

    private String removeBrackets(String input) {
        if (input.startsWith("<") && input.endsWith(">")) {
            // Remove < and >
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    private String appendDefaultDomainIfNeeded(String address) {
        if (!address.contains("@")) {
            return address + "@" + getDefaultDomain();
        }
        return address;
    }

    @Override
    protected Class<MailHook> getHookInterface() {
        return MailHook.class;
    }

    @Override
    protected HookResult callHook(MailHook rawHook, SMTPSession session, String parameters) {
        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());
        return rawHook.doMail(session, sender);
    }

    @Override
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> l = super.getMarkerInterfaces();
        l.add(MailParametersHook.class);
        return l;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void wireExtensions(Class interfaceName, List extension) {
        if (MailParametersHook.class.equals(interfaceName)) {
            this.paramHooks = new HashMap<>();
            for (MailParametersHook hook : (Iterable<MailParametersHook>) extension) {
                String[] params = hook.getMailParamNames();
                for (String param : params) {
                    paramHooks.put(param, hook);
                }
            }
        } else {
            super.wireExtensions(interfaceName, extension);
        }
    }

    /**
     * Return the default domain to append if the sender contains none
     *
     * @return defaultDomain
     */
    protected String getDefaultDomain() {
        return "localhost";
    }


}
