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
package net.sberg.openkim.log;

import de.gematik.kim.al.model.AccountLimit;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.fachdienst.Fachdienst;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.error.*;
import org.bouncycastle.asn1.cms.ContentInfo;

public class DefaultLoggerContext {

    private boolean logSoap;
    private boolean logKonnektorExecute;
    private boolean writeInFile;
    private String fileName;
    private boolean htmlMode;
    private Konfiguration konfiguration;
    private Konnektor konnektor;
    private Fachdienst fachdienst;

    //kontext
    private String mandantId;
    private String clientSystemId;
    private String workplaceId;
    private String userId;
    private String konnektorId;

    private String mailServerHost;
    private String mailServerPort;
    private String mailServerUsername;
    private String mailServerPassword;
    private de.gematik.kim.al.model.AccountLimit accountLimit = new AccountLimit();

    private final MailaddressCertErrorContext mailaddressCertErrorContext = new MailaddressCertErrorContext();
    private final MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = new MailaddressKimVersionErrorContext();
    private final MailSignEncryptErrorContext mailSignEncryptErrorContext = new MailSignEncryptErrorContext();
    private final MailEncryptFormatErrorContext mailEncryptFormatErrorContext = new MailEncryptFormatErrorContext();
    private final MailDecryptErrorContext mailDecryptErrorContext = new MailDecryptErrorContext();
    private final MailSignVerifyErrorContext mailSignVerifyErrorContext = new MailSignVerifyErrorContext();

    //encrypt mail infos
    private ContentInfo encryptedContentInfo;
    private byte[] encryptedPart;

    public void reset() {
        konnektor = null;
        fachdienst = null;
        konfiguration = null;
    }

    //setter
    //********************************************************
    public void setMandantId(String mandantId) {
        this.mandantId = mandantId;
    }

    public void setClientSystemId(String clientSystemId) {
        this.clientSystemId = clientSystemId;
    }

    public void setWorkplaceId(String workplaceId) {
        this.workplaceId = workplaceId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setKonnektorId(String konnektorId) {
        this.konnektorId = konnektorId;
    }

    public void setMailServerHost(String mailServerHost) {
        this.mailServerHost = mailServerHost;
    }

    public void setMailServerPort(String mailServerPort) {
        this.mailServerPort = mailServerPort;
    }

    public void setMailServerUsername(String mailServerUsername) {
        this.mailServerUsername = mailServerUsername;
    }

    public void setMailServerPassword(String mailServerPassword) {
        this.mailServerPassword = mailServerPassword;
    }

    public void setFachdienst(Fachdienst fachdienst) {
        this.fachdienst = fachdienst;
    }

    public void setAccountLimit(AccountLimit accountLimit) {
        this.accountLimit = accountLimit;
    }

    public void setEncryptedContentInfo(ContentInfo encryptedContentInfo) {
        this.encryptedContentInfo = encryptedContentInfo;
    }

    public void setEncryptedPart(byte[] encryptedPart) {
        this.encryptedPart = encryptedPart;
    }
    //********************************************************

    //builder
    public DefaultLoggerContext buildLogSoap(boolean logSoap) {
        this.logSoap = logSoap;
        return this;
    }

    public DefaultLoggerContext buildLogKonnektorExecute(boolean logKonnektorExecute) {
        this.logKonnektorExecute = logKonnektorExecute;
        return this;
    }

    public DefaultLoggerContext buildHtmlMode(boolean htmlMode) {
        this.htmlMode = htmlMode;
        return this;
    }

    public DefaultLoggerContext buildWriteInFile(boolean writeInFile) {
        this.writeInFile = writeInFile;
        return this;
    }

    public DefaultLoggerContext buildFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public DefaultLoggerContext buildMandantId(String mandantId) {
        this.mandantId = mandantId;
        return this;
    }

    public DefaultLoggerContext buildClientSystemId(String clientSystemId) {
        this.clientSystemId = clientSystemId;
        return this;
    }

    public DefaultLoggerContext buildWorkplaceId(String workplaceId) {
        this.workplaceId = workplaceId;
        return this;
    }

    public DefaultLoggerContext buildUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public DefaultLoggerContext buildKonnektor(Konnektor konnektor) {
        this.konnektor = konnektor;
        return this;
    }

    public DefaultLoggerContext buildKonfiguration(Konfiguration konfiguration) {
        this.konfiguration = konfiguration;
        return this;
    }

    public DefaultLoggerContext buildFachdienst(Fachdienst fachdienst) {
        this.fachdienst = fachdienst;
        return this;
    }

    //getter
    //************************************************************************
    public boolean isLogSoap() {
        return logSoap;
    }

    public boolean isHtmlMode() {
        return htmlMode;
    }

    public boolean isLogKonnektorExecute() {
        return logKonnektorExecute;
    }

    public boolean isWriteInFile() {
        return writeInFile;
    }

    public String getFileName() {
        return fileName;
    }

    public Konnektor getKonnektor() {
        return konnektor;
    }

    public Konfiguration getKonfiguration() {
        return konfiguration;
    }

    public Fachdienst getFachdienst() {
        return fachdienst;
    }

    public String getMandantId() {
        return mandantId;
    }

    public String getClientSystemId() {
        return clientSystemId;
    }

    public String getWorkplaceId() {
        return workplaceId;
    }

    public String getUserId() {
        return userId;
    }

    public String getKonnektorId() {
        return konnektorId;
    }

    public String getMailServerHost() {
        return mailServerHost;
    }

    public String getMailServerPort() {
        return mailServerPort;
    }

    public String getMailServerUsername() {
        return mailServerUsername;
    }

    public String getMailServerPassword() {
        return mailServerPassword;
    }

    public AccountLimit getAccountLimit() {
        return accountLimit;
    }

    public ContentInfo getEncryptedContentInfo() {
        return encryptedContentInfo;
    }

    public byte[] getEncryptedPart() {
        return encryptedPart;
    }

    public MailaddressCertErrorContext getMailaddressCertErrorContext() {
        return mailaddressCertErrorContext;
    }

    public MailaddressKimVersionErrorContext getMailaddressKimVersionErrorContext() {
        return mailaddressKimVersionErrorContext;
    }

    public MailSignEncryptErrorContext getMailSignEncryptErrorContext() {
        return mailSignEncryptErrorContext;
    }

    public MailEncryptFormatErrorContext getMailEncryptFormatErrorContext() {
        return mailEncryptFormatErrorContext;
    }

    public MailDecryptErrorContext getMailDecryptErrorContext() {
        return mailDecryptErrorContext;
    }

    public MailSignVerifyErrorContext getMailSignVerifyErrorContext() {
        return mailSignVerifyErrorContext;
    }
    //************************************************************************

}
