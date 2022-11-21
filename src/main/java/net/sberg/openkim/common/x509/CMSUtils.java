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
package net.sberg.openkim.common.x509;

import net.sberg.openkim.konfiguration.konnektor.Konnektor;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cms.*;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.internet.MimePart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CMSUtils {

    private static final Logger log = LoggerFactory.getLogger(CMSUtils.class);

    private static final String ECC_OID = "1.2.840.10045.2.1";
    private static final String RECIPIENT_OID = "1.2.276.0.76.4.173";
    public static final String AUTH_ENVELOPED_DATA_OID = "1.2.840.113549.1.9.16.1.23";
    public static final String ENVELOPED_DATA_OID = "1.2.840.113549.1.7.3";

    public static final String SMIME_MIME_TYPE = "application/pkcs7-mime";
    public static final String SMIME_CONTENT_TYPE = "application/pkcs7-mime; smime-type=signed-data; name=smime.p7m";
    public static final String SMIME_CONTENT_AUTH_ENVELOPED_TYPE = "application/pkcs7-mime; smime-type=authenticated-enveloped-data; name=smime.p7m";
    public static final String SMIME_DISPOSITION = "attachment; filename=smime.p7m";

    public static final AuthEnvelopedData extractEnvelopedCMS(byte[] encryptedBytes) throws Exception {
        ASN1InputStream cmsInputStream = new ASN1InputStream(encryptedBytes);
        try {
            ContentInfo info = ContentInfo.getInstance(cmsInputStream.readObject());
            AuthEnvelopedData authEnvelopedData = AuthEnvelopedData.getInstance(info.getContent());
            cmsInputStream.close();
            return authEnvelopedData;
        } catch (Exception e) {
            log.error("encrypted bytes contains no AuthEnvelopedData", e);
            try {
                cmsInputStream.close();
            } catch (Exception ee) {
                log.error("error on closing ASN1InputStream", e);
            }
            throw e;
        }
    }

    public static final byte[] extractSignedContent(MimePart signedPart, boolean useParser) throws Exception {
        if (signedPart != null && signedPart.isMimeType(SMIME_MIME_TYPE)) {
            if (useParser) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                CMSSignedDataParser sp = new CMSSignedDataParser(new JcaDigestCalculatorProviderBuilder().setProvider("BC").build(), signedPart.getInputStream());
                CMSTypedStream recData = sp.getSignedContent();
                InputStream is = recData.getContentStream();
                for (int c = is.read(); c != -1; c = is.read()) {
                    byteArrayOutputStream.write(c);
                }
                byte[] signedContent = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.reset();
                byteArrayOutputStream.close();
                return signedContent;
            } else {
                ASN1InputStream asn1InputStream = new ASN1InputStream(signedPart.getDataHandler().getInputStream());
                try {
                    CMSSignedData cmsSignedData = new CMSSignedData(asn1InputStream);
                    CMSProcessableByteArray cmsProcessableByteArray = (CMSProcessableByteArray) cmsSignedData.getSignedContent();
                    return (byte[]) cmsProcessableByteArray.getContent();
                } catch (Exception e) {
                    log.error("error on extractSignedContent", e);
                    throw e;
                } finally {
                    if (asn1InputStream != null) {
                        asn1InputStream.close();
                    }
                }
            }
        }
        return null;
    }

    public static List<IssuerAndSerial> getCertIssuerAndSerialNumber(ContentInfo encryptedContentInfo, String userMailAddress) throws Exception {
        List<IssuerAndSerial> certIssuerAndSerialNumbers = new ArrayList<>();
        if (!encryptedContentInfo.getContentType().getId().equals(AUTH_ENVELOPED_DATA_OID)) {
            throw new IllegalStateException("unknown contenttype: " + encryptedContentInfo.getContentType().getId());
        }
        AuthEnvelopedData ap = AuthEnvelopedData.getInstance(encryptedContentInfo.getContent());
        AttributeTable attributeTable = new AttributeTable(ap.getUnauthAttrs());
        Attribute attribute = attributeTable.get(new ASN1ObjectIdentifier(RECIPIENT_OID));
        ASN1Encodable[] asn1Encodables = attribute.getAttributeValues();
        for (ASN1Encodable asn1Encodable : asn1Encodables) {
            if (asn1Encodable instanceof DERSequence) {
                DERSequence derSequence = (DERSequence) asn1Encodable;
                if (derSequence.size() == 2) {
                    DERIA5String email = (DERIA5String) derSequence.getObjectAt(0);
                    if (email.getString().equalsIgnoreCase(userMailAddress)) {
                        DERSequence derSequenceIssuerAndSerialNumber = (DERSequence) derSequence.getObjectAt(1);
                        IssuerAndSerialNumber issuerAndSerialNumber = IssuerAndSerialNumber.getInstance(derSequenceIssuerAndSerialNumber);

                        IssuerAndSerial issuerAndSerial = new IssuerAndSerial();
                        issuerAndSerial.setIssuer(issuerAndSerialNumber.getName().toString());
                        issuerAndSerial.setSerialNumber(issuerAndSerialNumber.getSerialNumber().getValue().toString());

                        certIssuerAndSerialNumbers.add(issuerAndSerial);
                    }
                }
            } else if (asn1Encodable instanceof DLSequence) {
                DLSequence dlSequence = (DLSequence) asn1Encodable;
                if (dlSequence.size() == 2) {
                    DERIA5String email = (DERIA5String) dlSequence.getObjectAt(0);
                    if (email.getString().equalsIgnoreCase(userMailAddress)) {
                        DLSequence dlSequenceIssuerAndSerialNumber = (DLSequence) dlSequence.getObjectAt(1);
                        IssuerAndSerialNumber issuerAndSerialNumber = IssuerAndSerialNumber.getInstance(dlSequenceIssuerAndSerialNumber);

                        IssuerAndSerial issuerAndSerial = new IssuerAndSerial();
                        issuerAndSerial.setIssuer(issuerAndSerialNumber.getName().toString());
                        issuerAndSerial.setSerialNumber(issuerAndSerialNumber.getSerialNumber().getValue().toString());

                        certIssuerAndSerialNumbers.add(issuerAndSerial);
                    }
                }
            }
        }
        return certIssuerAndSerialNumbers;
    }

    public static boolean encryptedRecipientInfosAvailable(CMSEnvelopedData cmsEnvelopedData) throws Exception {
        for (Iterator<RecipientInformation> iterator = cmsEnvelopedData.getRecipientInfos().getRecipients().iterator(); iterator.hasNext(); ) {
            RecipientInformation recipientInformation = iterator.next();
            if (recipientInformation.getRID().getType() == 0) {
                KeyTransRecipientId keyTransRecipientId = (KeyTransRecipientId) recipientInformation.getRID();
                if (keyTransRecipientId.getIssuer() == null || keyTransRecipientId.getSerialNumber() == null) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean encryptedRecipientEmailsAvailable(ContentInfo contentInfo) throws Exception {
        if (!contentInfo.getContentType().getId().equals(AUTH_ENVELOPED_DATA_OID)) {
            throw new IllegalStateException("unknown contenttype: " + contentInfo.getContentType().getId());
        }
        AuthEnvelopedData ap = AuthEnvelopedData.getInstance(contentInfo.getContent());
        AttributeTable attributeTable = new AttributeTable(ap.getUnauthAttrs());
        Attribute attribute = attributeTable.get(new ASN1ObjectIdentifier(RECIPIENT_OID));
        ASN1Encodable[] asn1Encodables = attribute.getAttributeValues();
        for (ASN1Encodable asn1Encodable : asn1Encodables) {
            if (asn1Encodable instanceof DERSequence) {
                DERSequence derSequence = (DERSequence) asn1Encodable;
                if (derSequence.size() == 2) {
                    DERIA5String email = (DERIA5String) derSequence.getObjectAt(0);
                    DERSequence derSequenceIssuerAndSerialNumber = (DERSequence) derSequence.getObjectAt(1);
                    IssuerAndSerialNumber issuerAndSerialNumber = IssuerAndSerialNumber.getInstance(derSequenceIssuerAndSerialNumber);
                    if (email == null || issuerAndSerialNumber.getName() == null || issuerAndSerialNumber.getSerialNumber() == null) {
                        return false;
                    }
                } else {
                    return false;
                }
            } else if (asn1Encodable instanceof DLSequence) {
                DLSequence dlSequence = (DLSequence) asn1Encodable;
                if (dlSequence.size() == 2) {
                    DERIA5String email = (DERIA5String) dlSequence.getObjectAt(0);
                    DLSequence dlSequenceIssuerAndSerialNumber = (DLSequence) dlSequence.getObjectAt(1);
                    IssuerAndSerialNumber issuerAndSerialNumber = IssuerAndSerialNumber.getInstance(dlSequenceIssuerAndSerialNumber);
                    if (email == null || issuerAndSerialNumber.getName() == null || issuerAndSerialNumber.getSerialNumber() == null) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public static final byte[] buildCmsAttributeRecipientEmails(List<X509CertificateResult> recipients, Konnektor konnektor) throws IOException {
        ASN1ObjectIdentifier asn1ObjectIdentifier = new ASN1ObjectIdentifier(RECIPIENT_OID);
        ASN1EncodableVector recipientEmails = new ASN1EncodableVector();
        for (Iterator<X509CertificateResult> iterator = recipients.iterator(); iterator.hasNext(); ) {
            X509CertificateResult x509CertificateResult = iterator.next();
            List<byte[]> certs = konnektor.isEccEncryptionAvailable() ? x509CertificateResult.getCerts() : x509CertificateResult.getRsaCerts();
            for (Iterator<byte[]> iterator1 = certs.iterator(); iterator1.hasNext(); ) {
                byte[] cert = iterator1.next();
                ASN1EncodableVector recipientEmail = new ASN1EncodableVector();
                DERIA5String deria5Email = new DERIA5String(x509CertificateResult.getMailAddress());
                recipientEmail.add(deria5Email);
                Certificate certificate = Certificate.getInstance(cert);
                IssuerAndSerialNumber issuerAndSerialNumber = new IssuerAndSerialNumber(certificate);
                RecipientIdentifier recipientIdentifier = new RecipientIdentifier(issuerAndSerialNumber);
                recipientEmail.add(recipientIdentifier);
                DERSequence seq = new DERSequence(recipientEmail);
                recipientEmails.add(seq);
            }
        }
        DERSet dERSet = new DERSet(recipientEmails);
        Attribute attr = new Attribute(asn1ObjectIdentifier, dERSet);
        return attr.getEncoded();
    }

    public static final X509CertificateResult filterRsaCerts(X509CertificateResult x509CertificateResult) throws Exception {
        ASN1ObjectIdentifier eccPublicKeyId = new ASN1ObjectIdentifier(ECC_OID);
        for (Iterator<byte[]> iterator = x509CertificateResult.getCerts().iterator(); iterator.hasNext(); ) {
            byte[] cert = iterator.next();
            Certificate certificate = Certificate.getInstance(cert);
            ASN1ObjectIdentifier certPubKeyAlgorithmIdentifier = certificate.getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm();
            if (!eccPublicKeyId.equals(certPubKeyAlgorithmIdentifier)) {
                x509CertificateResult.getRsaCerts().add(cert);
            }
        }
        return x509CertificateResult;
    }
}
