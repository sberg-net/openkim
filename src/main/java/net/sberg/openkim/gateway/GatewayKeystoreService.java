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
package net.sberg.openkim.gateway;

import net.sberg.openkim.common.ICommonConstants;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class GatewayKeystoreService {

    private static final Logger log = LoggerFactory.getLogger(GatewayKeystoreService.class);

    private final int keysize = 2048;

    private static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERT = "-----END CERTIFICATE-----";

    private static final String BEGIN_RSA_KEY = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String END_RSA_KEY = "-----END RSA PRIVATE KEY-----";

    private static final String BEGIN_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String END_KEY = "-----END PRIVATE KEY-----";

    @Value("${gatewaykeystore.password}")
    private String password;

    private SubjectKeyIdentifier createSubjectKeyId(final PublicKey publicKey) throws OperatorCreationException {
        final SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        final DigestCalculator digCalc =
                new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));

        return new X509ExtensionUtils(digCalc).createSubjectKeyIdentifier(publicKeyInfo);
    }

    private AuthorityKeyIdentifier createAuthorityKeyId(final PublicKey publicKey)
            throws OperatorCreationException
    {
        final SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        final DigestCalculator digCalc =
                new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1));

        return new X509ExtensionUtils(digCalc).createAuthorityKeyIdentifier(publicKeyInfo);
    }

    public void createSelfSigned() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("***generateSelfSigned keys and cert - BEGIN***");
        }

        KeyPairGenerator kg = KeyPairGenerator.getInstance("EC", "BC");
        ECGenParameterSpec kpgparams = new ECGenParameterSpec("brainpoolP256r1");
        kg.initialize(kpgparams);

        KeyPair keyPair = kg.generateKeyPair();

        final Instant now = Instant.now();
        final Date notBefore = Date.from(now);
        final Date notAfter = Date.from(now.plus(Duration.ofDays(365)));
        final ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
        final X500Name x500Name = new X500Name("CN=" + "eldix4kim.sberg.net");

        final X509v3CertificateBuilder certificateBuilder =
            new JcaX509v3CertificateBuilder(x500Name,
                BigInteger.valueOf(now.toEpochMilli()),
                notBefore,
                notAfter,
                x500Name,
                keyPair.getPublic())
                .addExtension(Extension.subjectKeyIdentifier, false, createSubjectKeyId(keyPair.getPublic()))
                .addExtension(Extension.authorityKeyIdentifier, false, createAuthorityKeyId(keyPair.getPublic()))
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        X509Certificate x509Certificate = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
        Certificate[] chain = new Certificate[]{x509Certificate};

        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry(ICommonConstants.OPENKIM_SERVER_KEYSTORE_ALIAS, keyPair.getPrivate(), password.toCharArray(), chain);
        keyStore.store(
            new FileOutputStream(new File(ICommonConstants.BASE_DIR + ICommonConstants.OPENKIM_SERVER_KEYSTORE_FILENAME)),
            password.toCharArray()
        );

        if (log.isInfoEnabled()) {
            log.info("***generateSelfSigned keys and cert - END***");
        }
    }

    public void create(GatewayKeystoreData eldixSmtpKeystoreData) throws Exception {
        if (log.isInfoEnabled()) {
            log.info("***savePrivateKeyAndCerts keys and cert - BEGIN***");
        }

        //handle certs
        String[] certStrArr = eldixSmtpKeystoreData.getCertChain().split(END_CERT);
        Certificate[] certChain = new Certificate[certStrArr.length];
        for (int i = 0; i < certStrArr.length; i++) {
            String certStr = certStrArr[i].replaceAll(BEGIN_CERT, "");
            certStr = certStr.replaceAll("\\s+", "");
            certStr = BEGIN_CERT + "\n" + certStr + "\n" + END_CERT;
            Certificate cert = new org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory().engineGenerateCertificate(new ByteArrayInputStream(certStr.getBytes()));
            certChain[i] = cert;
        }

        //handle private key
        String privateKey = eldixSmtpKeystoreData.getPrivateKey().replace(BEGIN_RSA_KEY, "");
        privateKey = privateKey.replace(END_RSA_KEY, "");
        privateKey = privateKey.replaceAll("\\s+", "");
        byte[] pkcs8EncodedBytes = Base64.getDecoder().decode(privateKey.getBytes("UTF-8"));

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8EncodedBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey privKey = kf.generatePrivate(keySpec);

        //write to keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry(ICommonConstants.OPENKIM_SERVER_KEYSTORE_ALIAS, privKey, password.toCharArray(), certChain);
        keyStore.store(
            new FileOutputStream(new File(ICommonConstants.BASE_DIR + ICommonConstants.OPENKIM_SERVER_KEYSTORE_FILENAME)),
            password.toCharArray()
        );

        if (log.isInfoEnabled()) {
            log.info("***savePrivateKeyAndCerts keys and cert - END***");
        }
    }

    public void delete() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("***delete keys and cert - BEGIN***");
        }
        new File(ICommonConstants.BASE_DIR + ICommonConstants.OPENKIM_SERVER_KEYSTORE_FILENAME).delete();
        if (log.isInfoEnabled()) {
            log.info("***delete keys and cert - END***");
        }
    }

    public boolean keystoreAvailable() {
        return new File(ICommonConstants.BASE_DIR + ICommonConstants.OPENKIM_SERVER_KEYSTORE_FILENAME).exists();
    }

}
