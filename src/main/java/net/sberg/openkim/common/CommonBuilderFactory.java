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
package net.sberg.openkim.common;

import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.konnektor.webservice.jaxb.CMSAttribute;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.transport.http.HttpComponentsMessageSender;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;

public class CommonBuilderFactory {

    private static final Logger log = LoggerFactory.getLogger(CommonBuilderFactory.class);

    public void checkKonnektorServiceBean(Konnektor konnektor, KonnektorServiceBean konnektorServiceBean, DefaultLogger logger) throws Exception {
        log.info("checkKonnektorWebServiceBean for: " + konnektorServiceBean.getEnumKonnektorServiceBeanType() + " - " + konnektorServiceBean.getEndpointTls());
        logger.logLine("checkKonnektorWebServiceBean for: " + konnektorServiceBean.getEnumKonnektorServiceBeanType() + " - " + konnektorServiceBean.getEndpointTls());

        HttpClientBuilder httpClientBuilder;
        if (konnektor.getTimeoutInSeconds() > 0) {
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(konnektor.getTimeoutInSeconds() * 1000)
                .setConnectionRequestTimeout(konnektor.getTimeoutInSeconds() * 1000)
                .setSocketTimeout(konnektor.getTimeoutInSeconds() * 1000).build();
            httpClientBuilder = HttpClientBuilder.create()
                .disableContentCompression()
                .setDefaultRequestConfig(config)
                .setSSLSocketFactory(createApacheSSLSocketFactory(konnektor)).setRetryHandler(new DefaultHttpRequestRetryHandler(1, true));
        } else {
            httpClientBuilder = HttpClientBuilder.create().disableContentCompression().setSSLSocketFactory(createApacheSSLSocketFactory(konnektor)).setRetryHandler(new DefaultHttpRequestRetryHandler(1, true));
        }

        if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.BASICAUTH)) {
            BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(konnektor.getBasicAuthUser(), konnektor.getBasicAuthPwd());
            basicCredentialsProvider.setCredentials(AuthScope.ANY, credentials);
            httpClientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
        }

        HttpClient httpCLient = httpClientBuilder.build();
        HttpGet httpGet = new HttpGet(konnektorServiceBean.getEndpointTls());
        CloseableHttpResponse response = (CloseableHttpResponse) httpCLient.execute(httpGet);

        log.info("true check " + response.getStatusLine().getStatusCode() + " checkKonnektorWebServiceBean for: " + konnektorServiceBean.getEnumKonnektorServiceBeanType() + " - " + konnektorServiceBean.getEndpointTls());
        logger.logLine("true check " + response.getStatusLine().getStatusCode() + " checkKonnektorWebServiceBean for: " + konnektorServiceBean.getEnumKonnektorServiceBeanType() + " - " + konnektorServiceBean.getEndpointTls());

        konnektorServiceBean.setAlive(true);
    }

    public InputStream requestKonnektorServiceDescriptors(Konnektor konnektor) throws Exception {

        HttpClientBuilder httpClientBuilder;
        if (konnektor.getTimeoutInSeconds() > 0) {
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(konnektor.getTimeoutInSeconds() * 1000)
                .setConnectionRequestTimeout(konnektor.getTimeoutInSeconds() * 1000)
                .setSocketTimeout(konnektor.getTimeoutInSeconds() * 1000).build();
            httpClientBuilder = HttpClientBuilder.create()
                .disableContentCompression()
                .setDefaultRequestConfig(config)
                .setSSLSocketFactory(createApacheSSLSocketFactory(konnektor)).setRetryHandler(new DefaultHttpRequestRetryHandler(1, true));
        } else {
            httpClientBuilder = HttpClientBuilder.create().disableContentCompression().setSSLSocketFactory(createApacheSSLSocketFactory(konnektor)).setRetryHandler(new DefaultHttpRequestRetryHandler(1, true));
        }

        if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.BASICAUTH)) {
            BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(konnektor.getBasicAuthUser(), konnektor.getBasicAuthPwd());
            basicCredentialsProvider.setCredentials(AuthScope.ANY, credentials);
            httpClientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
        }

        HttpClient httpCLient = httpClientBuilder.build();

        HttpGet httpGet = new HttpGet(konnektor.getSdsUrl());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        CloseableHttpResponse response = (CloseableHttpResponse) httpCLient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        entity.writeTo(bos);
        return new ByteArrayInputStream(bos.toByteArray());
    }

    public WebserviceConnector buildWebserviceConnector(
        DefaultLogger logger,
        String packageName,
        String uri,
        Konnektor konnektor,
        String soapAction
    ) throws Exception {

        HttpComponentsMessageSender messageSender = new HttpComponentsMessageSender();

        if (konnektor.getTimeoutInSeconds() > 0) {
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(konnektor.getTimeoutInSeconds() * 1000)
                .setConnectionRequestTimeout(konnektor.getTimeoutInSeconds() * 1000)
                .setSocketTimeout(konnektor.getTimeoutInSeconds() * 1000).build();
            HttpClient httpCLient = HttpClientBuilder.create()
                .disableContentCompression()
                .setDefaultRequestConfig(config)
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor())
                .setSSLSocketFactory(createApacheSSLSocketFactory(konnektor)).setRetryHandler(new DefaultHttpRequestRetryHandler(1, true))
                .build();
            messageSender.setHttpClient(httpCLient);
        } else {
            HttpClient httpCLient = HttpClientBuilder.create()
                .disableContentCompression()
                .addInterceptorFirst(new HttpComponentsMessageSender.RemoveSoapHeadersInterceptor())
                .setSSLSocketFactory(createApacheSSLSocketFactory(konnektor)).setRetryHandler(new DefaultHttpRequestRetryHandler(1, true))
                .build();
            messageSender.setHttpClient(httpCLient);
        }

        if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.BASICAUTH)) {
            messageSender.setCredentials(new UsernamePasswordCredentials(konnektor.getBasicAuthUser(), konnektor.getBasicAuthPwd()));
        }

        messageSender.afterPropertiesSet();

        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setPackagesToScan(packageName, CMSAttribute.class.getPackageName());
        WebserviceConnector connector = new WebserviceConnector();
        connector.setMessageSender(messageSender);
        connector.setDefaultUri(uri);
        connector.setMarshaller(marshaller);
        connector.setUnmarshaller(marshaller);
        connector.setInterceptors(new ClientInterceptor[]{new KonnektorWebserviceInterceptor(soapAction, logger)});
        connector.afterPropertiesSet();
        return connector;

    }

    private SSLConnectionSocketFactory createApacheSSLSocketFactory(Konnektor konnektor) throws Exception {

        SSLContextBuilder sslContextBuilder;

        if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)) {

            File dir = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_DIR, konnektor.getUuid()));
            String clientCertFileName = dir + File.separator + konnektor.getClientCertFilename();

            char[] passCharArray = konnektor.getClientCertAuthPwd().toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new FileInputStream(clientCertFileName), passCharArray);
            sslContextBuilder = SSLContexts.custom().loadKeyMaterial(keyStore, passCharArray);
        } else {
            sslContextBuilder = SSLContexts.custom();
        }

        File truststoreFile = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_TRUSTORE_JKS, konnektor.getUuid()));
        if (truststoreFile.exists()) {
            sslContextBuilder.loadTrustMaterial(truststoreFile, ICommonConstants.KONNEKTOR_TRUSTORE_JKS_PWD.toCharArray());
        } else {
            sslContextBuilder.loadTrustMaterial(new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    return true;
                }
            });
        }

        return new SSLConnectionSocketFactory(sslContextBuilder.build(), NoopHostnameVerifier.INSTANCE);
    }

    public KeyManager[] createKeyManager(Konnektor konnektor) throws Exception {
        if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)) {

            File dir = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_DIR, konnektor.getUuid()));
            String clientCertFileName = dir + File.separator + konnektor.getClientCertFilename();

            char[] passCharArray = konnektor.getClientCertAuthPwd().toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new FileInputStream(clientCertFileName), passCharArray);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keyStore, passCharArray);
            return kmf.getKeyManagers();
        } else {
            return null;
        }
    }
}
