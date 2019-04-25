package com.softwareplumbers.dms.rest.server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.security.cert.Certificate;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class KeyManager {
    
    
    public static enum KeyName {
        JWT_SIGNING_KEY,
        DEFAULT_SERVICE_ACCOUNT
    }
    
    public static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA1withDSA";
    
    private static final KeyStore.PasswordProtection KEY_PASSWORD = new KeyStore.PasswordProtection("".toCharArray());
    
    private static final Log LOG = new Log(KeyManager.class);
    
    private KeyStore keystore; 
    
    /** Generate a certificate for the default service account.
     * 
     * This is really only for creating a runnable test setup.
     * 
     * @param subjectDN
     * @param pair
     * @return
     * @throws CertIOException
     * @throws OperatorCreationException
     * @throws CertificateException 
     */
    private static X509Certificate generateCertificate(String account, KeyPair pair) throws CertIOException, OperatorCreationException, CertificateException {
        Provider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        X500Name dnName = new X500Name("cn=" + account);
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 3); // <-- 3 Yr validity

        Date endDate = calendar.getTime();

        ContentSigner contentSigner = new JcaContentSignerBuilder(DEFAULT_SIGNATURE_ALGORITHM).build(pair.getPrivate());

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, pair.getPublic());

        // Extensions --------------------------

        // Basic Constraints
        BasicConstraints basicConstraints = new BasicConstraints(true); // <-- true for CA, false for EndEntity

        certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.

        // -------------------------------------

        return new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));
    }
    
    /** Initialize the key store.
     * 
     * Creates a new, default key store for the manager to use. It will contain a randomly-generated
     * secret signing key, and a randomly generated public/private key pair under the name 'defaultServiceAccount'.
     * 
     * The generated private key can be used to sign service requests passed to the auth/service endpoint, in order
     * to obtain an access token to use the API.
     * 
     * @param keystore
     */
    private static void init(KeyStore keystore) {
        LOG.logEntering("init", Log.fmt(keystore));
        try {
            Key secret = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA", "SUN");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            keyGen.initialize(1024, random);
            KeyPair defaultServiceAccount = keyGen.generateKeyPair();
            X509Certificate certificate = generateCertificate(KeyName.DEFAULT_SERVICE_ACCOUNT.name(), defaultServiceAccount);  
            Certificate[] certChain = new Certificate[1];  
            certChain[0] = certificate;  
            keystore.setKeyEntry(KeyName.DEFAULT_SERVICE_ACCOUNT.name(), (Key)defaultServiceAccount.getPrivate(), KEY_PASSWORD.getPassword(), certChain);  
            keystore.setKeyEntry(KeyName.JWT_SIGNING_KEY.name(), secret, KEY_PASSWORD.getPassword(), null);
        } catch (Exception e) {
            throw LOG.logRethrow("init", new RuntimeException(e));
        }         
        LOG.logExiting("init");
    }
    
    public Key getKey(String name) {
        LOG.logEntering("getKey", name);
        try {
            if (keystore.isCertificateEntry(name)) {
                Certificate cert = keystore.getCertificate(name);
                return cert.getPublicKey();
            } else {
                Key key = keystore.getKey(name, KEY_PASSWORD.getPassword());
                if (key instanceof PrivateKey) {
                    Certificate cert = keystore.getCertificate(name);
                    key =  cert.getPublicKey();                    
                }
                LOG.logReturn("getKey", "<redacted>");
                return key;
            }
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw LOG.logRethrow("getKey", new RuntimeException(e));
        } 
    }
    
    public KeyPair getKeyPair(String name) {
        LOG.logEntering("getKeyPair", name);
        try {
            Key key = keystore.getKey(name, KEY_PASSWORD.getPassword());
            Certificate cert = keystore.getCertificate(name);
            LOG.logReturn("getKeyPair", "<redacted>");
            return new KeyPair(cert.getPublicKey(), (PrivateKey)key);
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw LOG.logRethrow("getKey", new RuntimeException(e));
        } 
        
    }
    
    public KeyPair getKeyPair(KeyName name) {
        return getKeyPair(name.name());
    }
    
    public Key getKey(KeyName keyname) {
        return getKey(keyname.name());
    }
    
    public KeyManager(String location, String password) throws KeyStoreException {
  
        LOG.logEntering("<constructor>", location, "<redacted>");
        File file = new File(location);
        keystore = KeyStore.getInstance("JCEKS");
        
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                keystore.load(is, password.toCharArray());
            } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
                throw LOG.logRethrow("<constructor>", new RuntimeException(e));
            }
        } else {
            try (OutputStream os = new FileOutputStream(file)) {
                keystore.load(null, password.toCharArray());
                init(keystore);
                keystore.store(os, password.toCharArray());
            } catch (NoSuchAlgorithmException | CertificateException | IOException e) {
                throw LOG.logRethrow("<constructor>", new RuntimeException(e));
            }
        }
        
        LOG.logExiting("<constructor>");
    }

}
