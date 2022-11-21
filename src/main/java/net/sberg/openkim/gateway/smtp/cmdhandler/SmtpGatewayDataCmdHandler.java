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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.gateway.smtp.EnumSmtpGatewayState;
import net.sberg.openkim.gateway.smtp.SmtpGatewaySession;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.*;
import org.apache.james.protocols.smtp.core.DataLineFilter;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.util.MDCBuilder;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class SmtpGatewayDataCmdHandler implements CommandHandler<SMTPSession>, ExtensibleHandler {

    private static final Response NO_RECIPIENT = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " No recipients specified").immutable();
    private static final Response NO_SENDER = new SMTPResponse(SMTPRetCode.BAD_SEQUENCE, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_OTHER) + " No sender specified").immutable();
    private static final Response UNEXPECTED_ARG = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.DELIVERY_INVALID_ARG) + " Unexpected argument provided with DATA command").immutable();
    private static final Response DATA_READY = new SMTPResponse(SMTPRetCode.DATA_READY, "Ok Send data ending with <CRLF>.<CRLF>").immutable();
    private static final Collection<String> COMMANDS = ImmutableSet.of("DATA");

    public static final class DataConsumerLineHandler implements LineHandler<SMTPSession> {

        @Override
        public SMTPResponse onLine(SMTPSession session, ByteBuffer line) {
            // Discard everything until the end of DATA session
            if (line.remaining() == 3 && line.get() == 46) {
                session.popLineHandler();
            }
            return null;
        }
    }

    public static final class DataLineFilterWrapper implements LineHandler<SMTPSession> {

        private final DataLineFilter filter;
        private final LineHandler<SMTPSession> next;

        public DataLineFilterWrapper(DataLineFilter filter, LineHandler<SMTPSession> next) {
            this.filter = filter;
            this.next = next;
        }

        @Override
        public Response onLine(SMTPSession session, ByteBuffer line) {
            line.rewind();
            return filter.onLine(session, line, next);
        }
    }

    public static final ProtocolSession.AttachmentKey<MailEnvelope> MAILENV = ProtocolSession.AttachmentKey.of("MAILENV", MailEnvelope.class);

    private LineHandler<SMTPSession> lineHandler;

    /**
     * process DATA command
     */
    @Override
    public Response onCommand(SMTPSession session, Request request) {
        DefaultMetricFactory gatewayMetricFactory = new DefaultMetricFactory(((SmtpGatewaySession) session).getLogger());
        TimeMetric timeMetric = gatewayMetricFactory.timer("smtp-data");
        session.stopDetectingCommandInjection();
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.ACTION, request.getCommand())
                     .build()) {
            String parameters = request.getArgument();
            Response response = doDATAFilter(session, parameters);

            if (response == null) {
                return doDATA(session, parameters);
            } else {
                return response;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            timeMetric.stopAndPublish();
            session.needsCommandInjectionDetection();
        }
    }


    /**
     * Handler method called upon receipt of a DATA command.
     * Reads in message data, creates header, and delivers to
     * mail server service for delivery.
     *
     * @param session  SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    @SuppressWarnings("unchecked")
    protected Response doDATA(SMTPSession session, String argument) {
        ((SmtpGatewaySession) session).log("data begins");

        MaybeSender sender = session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).orElse(MaybeSender.nullSender());
        MailEnvelope env = createEnvelope(sender, session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction).orElse(ImmutableList.of()));
        session.setAttachment(MAILENV, env, ProtocolSession.State.Transaction);
        session.pushLineHandler(lineHandler);

        ((SmtpGatewaySession) session).setGatewayState(EnumSmtpGatewayState.PROCESS);
        ((SmtpGatewaySession) session).log("data ends");

        return DATA_READY;
    }

    protected MailEnvelope createEnvelope(MaybeSender sender, List<MailAddress> recipients) {
        MailEnvelopeImpl env = new MailEnvelopeImpl();
        env.setRecipients(recipients);
        env.setSender(sender);
        return env;
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List getMarkerInterfaces() {
        List classes = new LinkedList();
        classes.add(DataLineFilter.class);
        return classes;
    }


    @Override
    @SuppressWarnings("rawtypes")
    public void wireExtensions(Class interfaceName, List extension) throws WiringException {
        if (DataLineFilter.class.equals(interfaceName)) {

            LineHandler<SMTPSession> lineHandler = new SmtpGatewayDataCmdHandler.DataConsumerLineHandler();
            for (int i = extension.size() - 1; i >= 0; i--) {
                lineHandler = new SmtpGatewayDataCmdHandler.DataLineFilterWrapper((DataLineFilter) extension.get(i), lineHandler);
            }

            this.lineHandler = lineHandler;
        }
    }

    protected Response doDATAFilter(SMTPSession session, String argument) {
        if ((argument != null) && (argument.length() > 0)) {
            return UNEXPECTED_ARG;
        }
        if (session.getAttachment(SMTPSession.SENDER, ProtocolSession.State.Transaction).isEmpty()) {
            return NO_SENDER;
        } else if (session.getAttachment(SMTPSession.RCPT_LIST, ProtocolSession.State.Transaction).isEmpty()) {
            return NO_RECIPIENT;
        }
        return null;
    }

    protected LineHandler<SMTPSession> getLineHandler() {
        return lineHandler;
    }

}
