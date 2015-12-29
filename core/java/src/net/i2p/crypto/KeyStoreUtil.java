package net.i2p.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Locale;

import net.i2p.I2PAppContext;
import net.i2p.data.Base32;
import net.i2p.util.Log;
import net.i2p.util.SecureDirectory;
import net.i2p.util.SecureFileOutputStream;
import net.i2p.util.ShellCommand;
import net.i2p.util.SystemVersion;

/**
 *  Keystore utilities, consolidated from various places.
 *
 *  @since 0.9.9
 */
public class KeyStoreUtil {
        
    public static boolean _blacklistLogged;

    public static final String DEFAULT_KEYSTORE_PASSWORD = "changeit";
    private static final String DEFAULT_KEY_ALGORITHM = "RSA";
    private static final int DEFAULT_KEY_SIZE = 2048;
    private static final int DEFAULT_KEY_VALID_DAYS = 3652;  // 10 years

    /**
     *  No reports of some of these in a Java keystore but just to be safe...
     *  CNNIC ones are in Ubuntu keystore.
     */
    private static final BigInteger[] BLACKLIST_SERIAL = new BigInteger[] {
        // CNNIC https://googleonlinesecurity.blogspot.com/2015/03/maintaining-digital-certificate-security.html
        new BigInteger("49:33:00:01".replace(":", ""), 16),
        // CNNIC EV root https://bugzilla.mozilla.org/show_bug.cgi?id=607208
        new BigInteger("48:9f:00:01".replace(":", ""), 16),
        // Superfish http://blog.erratasec.com/2015/02/extracting-superfish-certificate.html
        new BigInteger("d2:fc:13:87:a9:44:dc:e7".replace(":", ""), 16),
        // eDellRoot https://www.reddit.com/r/technology/comments/3twmfv/dell_ships_laptops_with_rogue_root_ca_exactly/
        new BigInteger("6b:c5:7b:95:18:93:aa:97:4b:62:4a:c0:88:fc:3b:b6".replace(":", ""), 16),
        // DSDTestProvider https://blog.hboeck.de/archives/876-Superfish-2.0-Dangerous-Certificate-on-Dell-Laptops-breaks-encrypted-HTTPS-Connections.html
        // serial number is actually negative; hex string as reported by certtool below
        //new BigInteger("a4:4c:38:47:f8:ee:71:80:43:4d:b1:80:b9:a7:e9:62".replace(":", ""), 16)
        new BigInteger("-5b:b3:c7:b8:07:11:8e:7f:bc:b2:4e:7f:46:58:16:9e".replace(":", ""), 16),
        // Verisign G1 Roots
        // https://googleonlinesecurity.blogspot.com/2015/12/proactive-measures-in-digital.html
        // https://knowledge.symantec.com/support/ssl-certificates-support/index?page=content&id=ALERT1941
        // SHA-1
        new BigInteger("3c:91:31:cb:1f:f6:d0:1b:0e:9a:b8:d0:44:bf:12:be".replace(":", ""), 16),
        // MD2
        new BigInteger("70:ba:e4:1d:10:d9:29:34:b6:38:ca:7b:03:cc:ba:bf".replace(":", ""), 16),
        // Comodo SHA1 https://cabforum.org/pipermail/public/2015-December/006500.html
        // https://bugzilla.mozilla.org/show_bug.cgi?id=1208461
        new BigInteger("44:be:0c:8b:50:00:21:b4:11:d3:2a:68:06:a9:ad:69".replace(":", ""), 16)
    };

    /**
     *  Corresponding issuer CN for the serial number.
     *  Must be same number of entries as BLACKLIST_SERIAL.
     *  Either CN or OU must be non-null
     */
    private static final String[] BLACKLIST_ISSUER_CN = new String[] {
        "CNNIC ROOT",
        "China Internet Network Information Center EV Certificates Root",
        "Superfish, Inc.",
        "eDellRoot",
        "DSDTestProvider",
        null,
        null,
	"UTN - DATACorp SGC"
    };

    /**
     *  Corresponding issuer OU for the serial number.
     *  Must be same number of entries as BLACKLIST_SERIAL.
     *  Either CN or OU must be non-null
     */
    private static final String[] BLACKLIST_ISSUER_OU = new String[] {
        null,
        null,
        null,
        null,
        null,
        "Class 3 Public Primary Certification Authority",
        "Class 3 Public Primary Certification Authority",
        null
    };


    /**
     *  Create a new KeyStore object, and load it from ksFile if it is
     *  non-null and it exists.
     *  If ksFile is non-null and it does not exist, create a new empty
     *  keystore file.
     *
     *  @param ksFile may be null
     *  @param password may be null
     *  @return success
     */
    public static KeyStore createKeyStore(File ksFile, String password)
                              throws GeneralSecurityException, IOException {
        boolean exists = ksFile != null && ksFile.exists();
        char[] pwchars = password != null ? password.toCharArray() : null;
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        if (exists) {
            InputStream fis = null;
            try {
                fis = new FileInputStream(ksFile);
                ks.load(fis, pwchars);
            } finally {
                if (fis != null) try { fis.close(); } catch (IOException ioe) {}
            }
        }
        if (ksFile != null && !exists) {
            OutputStream fos = null;
            try {
                // must be initted
                ks.load(null, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
                fos = new SecureFileOutputStream(ksFile);
                ks.store(fos, pwchars);
            } finally {
                if (fos != null) try { fos.close(); } catch (IOException ioe) {}
            }
        }
        return ks;
    }

    /**
     *  Loads certs from location of javax.net.ssl.keyStore property,
     *  else from $JAVA_HOME/lib/security/jssacacerts,
     *  else from $JAVA_HOME/lib/security/cacerts.
     *
     *  @return null on catastrophic failure, returns empty KeyStore if can't load system file
     *  @since 0.8.2, moved from SSLEepGet.initSSLContext() in 0.9.9
     */
    public static KeyStore loadSystemKeyStore() {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (GeneralSecurityException gse) {
            error("Key Store init error", gse);
            return null;
        }
        boolean success = false;
        String override = System.getProperty("javax.net.ssl.keyStore");
        if (override != null)
            success = loadCerts(new File(override), ks);
        if (!success) {
            if (SystemVersion.isAndroid()) {
                if (SystemVersion.getAndroidVersion() >= 14) {
                    try {
                        ks.load(null, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
                        success = addCerts(new File(System.getProperty("java.home"), "etc/security/cacerts"), ks) > 0;
                    } catch (IOException e) {
                    } catch (GeneralSecurityException e) {}
                } else {
                    success = loadCerts(new File(System.getProperty("java.home"), "etc/security/cacerts.bks"), ks);
                }
            } else {
                success = loadCerts(new File(System.getProperty("java.home"), "lib/security/jssecacerts"), ks);
                if (!success)
                    success = loadCerts(new File(System.getProperty("java.home"), "lib/security/cacerts"), ks);
            }
        }

        if (success) {
            removeBlacklistedCerts(ks);
        } else {
            try {
                // must be initted
                ks.load(null, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
            } catch (IOException e) {
            } catch (GeneralSecurityException e) {}
            error("All key store loads failed, will only load local certificates", null);
        }
        return ks;
    }

    /**
     *  Load all X509 Certs from a key store File into a KeyStore
     *  Note that each call reinitializes the KeyStore
     *
     *  @return success
     *  @since 0.8.2, moved from SSLEepGet in 0.9.9
     */
    private static boolean loadCerts(File file, KeyStore ks) {
        if (!file.exists())
            return false;
        InputStream fis = null;
        try {
            fis = new FileInputStream(file);
            // "changeit" is the default password
            ks.load(fis, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
            info("Certs loaded from " + file);
        } catch (GeneralSecurityException gse) {
            error("KeyStore load error, no default keys: " + file.getAbsolutePath(), gse);
            try {
                // not clear if null is allowed for password
                ks.load(null, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
            } catch (IOException foo) {
            } catch (GeneralSecurityException e) {}
            return false;
        } catch (IOException ioe) {
            error("KeyStore load error, no default keys: " + file.getAbsolutePath(), ioe);
            try {
                ks.load(null, DEFAULT_KEYSTORE_PASSWORD.toCharArray());
            } catch (IOException foo) {
            } catch (GeneralSecurityException e) {}
            return false;
        } finally {
            try { if (fis != null) fis.close(); } catch (IOException foo) {}
        }
        return true;
    }


    /**
     *  Count all X509 Certs in a key store
     *
     *  @return number successfully added
     *  @since 0.8.2, moved from SSLEepGet in 0.9.9
     */
    public static int countCerts(KeyStore ks) {
        int count = 0;
        try {
            for(Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
                String alias = e.nextElement();
                if (ks.isCertificateEntry(alias)) {
                    //info("Found cert " + alias);
                    count++;
                }
            }
        } catch (GeneralSecurityException e) {}
        return count;
    }

    /**
     *  Remove all blacklisted X509 Certs in a key store.
     *  Match by serial number and issuer CN, which should uniquely identify a cert,
     *  if the CN is present. Should be faster than fingerprints.
     *
     *  @return number successfully removed
     *  @since 0.9.24
     */
    private static int removeBlacklistedCerts(KeyStore ks) {
        // This matches on the CN or OU in the issuer,
        // and we can't do that on Android.
        // Alternative is sha1hash(cert.getEncoded()) but that would be slower,
        // unless the blacklist gets a little longer.
        if (SystemVersion.isAndroid())
            return 0;
        int count = 0;
        try {
            for(Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
                String alias = e.nextElement();
                if (ks.isCertificateEntry(alias)) {
                    Certificate c = ks.getCertificate(alias);
                    if (c != null && (c instanceof X509Certificate)) {
                        X509Certificate xc = (X509Certificate) c;
                        BigInteger serial = xc.getSerialNumber();
                        // debug:
                        //String xname = CertUtil.getIssuerValue(xc, "CN");
                        //info("Found \"" + xname + "\" s/n: " + serial.toString(16));
                        //if (xname == null)
                        //    info("name is null, full issuer: " + xc.getIssuerX500Principal().getName());
                        for (int i = 0; i < BLACKLIST_SERIAL.length; i++) {
                            if (BLACKLIST_SERIAL[i].equals(serial)) {
                                if (BLACKLIST_ISSUER_CN[i] != null) {
                                    String name = CertUtil.getIssuerValue(xc, "CN");
                                    if (BLACKLIST_ISSUER_CN[i].equals(name)) {
                                        ks.deleteEntry(alias);
                                        count++;
                                        if (!_blacklistLogged) {
                                            // should this be a logAlways?
                                            warn("Ignoring blacklisted certificate \"" + alias +
                                                 "\" CN: \"" + name +
                                                 "\" s/n: " + serial.toString(16), null);
                                        }
                                        break;
                                    }
                                }
                                if (BLACKLIST_ISSUER_OU[i] != null) {
                                    String name = CertUtil.getIssuerValue(xc, "OU");
                                    if (BLACKLIST_ISSUER_OU[i].equals(name)) {
                                        ks.deleteEntry(alias);
                                        count++;
                                        if (!_blacklistLogged) {
                                            // should this be a logAlways?
                                            warn("Ignoring blacklisted certificate \"" + alias +
                                                 "\" OU: \"" + name +
                                                 "\" s/n: " + serial.toString(16), null);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (GeneralSecurityException e) {}
        if (count > 0)
            _blacklistLogged = true;
        return count;
    }

    /**
     *  Load all X509 Certs from a directory and add them to the
     *  trusted set of certificates in the key store
     *
     *  @return number successfully added
     *  @since 0.8.2, moved from SSLEepGet in 0.9.9
     */
    public static int addCerts(File dir, KeyStore ks) {
        info("Looking for X509 Certificates in " + dir.getAbsolutePath());
        int added = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    if (!f.isFile())
                        continue;
                    // use file name as alias
                    // https://www.sslshopper.com/ssl-converter.html
                    // No idea if all these formats can actually be read by CertificateFactory
                    String alias = f.getName().toLowerCase(Locale.US);
                    if (alias.endsWith(".crt") || alias.endsWith(".pem") || alias.endsWith(".key") ||
                        alias.endsWith(".der") || alias.endsWith(".key") || alias.endsWith(".p7b") ||
                        alias.endsWith(".p7c") || alias.endsWith(".pfx") || alias.endsWith(".p12") ||
                        alias.endsWith(".cer"))
                        alias = alias.substring(0, alias.length() - 4);
                    boolean success = addCert(f, alias, ks);
                    if (success)
                        added++;
                }
            }
        }
        return added;
    }

    /**
     *  Load an X509 Cert from a file and add it to the
     *  trusted set of certificates in the key store
     *
     *  @return success
     *  @since 0.8.2, moved from SSLEepGet in 0.9.9
     */
    public static boolean addCert(File file, String alias, KeyStore ks) {
        try {
            X509Certificate cert = CertUtil.loadCert(file);
            info("Read X509 Certificate from " + file.getAbsolutePath() +
                          " Issuer: " + cert.getIssuerX500Principal() +
                          " Serial: " + cert.getSerialNumber().toString(16) +
                          "; Valid From: " + cert.getNotBefore() +
                          " To: " + cert.getNotAfter());
            ks.setCertificateEntry(alias, cert);
            info("Now trusting X509 Certificate, Issuer: " + cert.getIssuerX500Principal());
        } catch (CertificateExpiredException cee) {
            String s = "Rejecting expired X509 Certificate: " + file.getAbsolutePath();
            // Android often has old system certs
            if (SystemVersion.isAndroid())
                warn(s, cee);
            else
                error(s, cee);
            return false;
        } catch (CertificateNotYetValidException cnyve) {
            error("Rejecting X509 Certificate not yet valid: " + file.getAbsolutePath(), cnyve);
            return false;
        } catch (GeneralSecurityException gse) {
            error("Error reading X509 Certificate: " + file.getAbsolutePath(), gse);
            return false;
        } catch (IOException ioe) {
            error("Error reading X509 Certificate: " + file.getAbsolutePath(), ioe);
            return false;
        }
        return true;
    }

    /** 48 char b32 string (30 bytes of entropy) */
    public static String randomString() {
        I2PAppContext ctx = I2PAppContext.getGlobalContext();
        // make a random 48 character password (30 * 8 / 5)
        byte[] rand = new byte[30];
        ctx.random().nextBytes(rand);
        return Base32.encode(rand);
    }

    /**
     *  Create a keypair and store it in the keystore at ks, creating it if necessary.
     *  Use default keystore password, valid days, algorithm, and key size.
     *
     *  Warning, may take a long time.
     *
     *  @param ks path to the keystore
     *  @param alias the name of the key
     *  @param cname e.g. randomstuff.console.i2p.net
     *  @param ou e.g. console
     *  @param keyPW the key password, must be at least 6 characters
     *
     *  @return success
     *  @since 0.8.3, consolidated from RouterConsoleRunner and SSLClientListenerRunner in 0.9.9
     */
    public static boolean createKeys(File ks, String alias, String cname, String ou,
                                     String keyPW) {
        return createKeys(ks, DEFAULT_KEYSTORE_PASSWORD, alias, cname, ou,
                          DEFAULT_KEY_VALID_DAYS, DEFAULT_KEY_ALGORITHM, DEFAULT_KEY_SIZE, keyPW);
    }

    /**
     *  Create a keypair and store it in the keystore at ks, creating it if necessary.
     *
     *  Warning, may take a long time.
     *
     *  @param ks path to the keystore
     *  @param ksPW the keystore password
     *  @param alias the name of the key
     *  @param cname e.g. randomstuff.console.i2p.net
     *  @param ou e.g. console
     *  @param validDays e.g. 3652 (10 years)
     *  @param keyAlg e.g. DSA , RSA, EC
     *  @param keySize e.g. 1024
     *  @param keyPW the key password, must be at least 6 characters
     *
     *  @return success
     *  @since 0.8.3, consolidated from RouterConsoleRunner and SSLClientListenerRunner in 0.9.9
     */
    public static boolean createKeys(File ks, String ksPW, String alias, String cname, String ou,
                                     int validDays, String keyAlg, int keySize, String keyPW) {
        if (ks.exists()) {
            try {
                if (getCert(ks, ksPW, alias) != null) {
                    error("Not overwriting key " + alias + ", already exists in " + ks, null);
                    return false;
                }
            } catch (IOException e) {
                error("Not overwriting key \"" + alias + "\", already exists in " + ks, e);
                return false;
            } catch (GeneralSecurityException e) {
                error("Not overwriting key \"" + alias + "\", already exists in " + ks, e);
                return false;
            }
        } else {
            File dir = ks.getParentFile();
            if (dir != null && !dir.exists()) {
                File sdir = new SecureDirectory(dir.getAbsolutePath());
                if (!sdir.mkdir()) {
                    error("Can't create directory " + dir, null);
                    return false;
                }
            }
        }
        String keytool = (new File(System.getProperty("java.home"), "bin/keytool")).getAbsolutePath();
        String[] args = new String[] {
                   keytool,
                   "-genkey",            // -genkeypair preferred in newer keytools, but this works with more
                   "-storetype", KeyStore.getDefaultType(),
                   "-keystore", ks.getAbsolutePath(),
                   "-storepass", ksPW,
                   "-alias", alias,
                   "-dname", "CN=" + cname + ",OU=" + ou + ",O=I2P Anonymous Network,L=XX,ST=XX,C=XX",
                   "-validity", Integer.toString(validDays),  // 10 years
                   "-keyalg", keyAlg,
                   "-sigalg", getSigAlg(keySize, keyAlg),
                   "-keysize", Integer.toString(keySize),
                   "-keypass", keyPW
        };
        // TODO pipe key password to process; requires ShellCommand enhancements
        boolean success = (new ShellCommand()).executeSilentAndWaitTimed(args, 240);
        if (success) {
            success = ks.exists();
            if (success) {
                try {
                    success = getPrivateKey(ks, ksPW, alias, keyPW) != null;
                    if (!success)
                        error("Key gen failed to get private key", null);
                } catch (IOException e) {
                    error("Key gen failed to get private key", e);
                    success = false;
                } catch (GeneralSecurityException e) {
                    error("Key gen failed to get private key", e);
                    success = false;
                }
            }
            if (!success)
                error("Key gen failed for unknown reasons", null);
        }
        if (success) {
            SecureFileOutputStream.setPerms(ks);
            info("Created self-signed certificate for " + cname + " in keystore: " + ks.getAbsolutePath());
        } else {
            StringBuilder buf = new StringBuilder(256);
            for (int i = 0;  i < args.length; i++) {
                buf.append('"').append(args[i]).append("\" ");
            }
            error("Failed to generate keys using command line: " + buf, null);
        }
        return success;
    }

    private static String getSigAlg(int size, String keyalg) {
        if (keyalg.equals("EC"))
            keyalg = "ECDSA";
        String hash;
        if (keyalg.equals("ECDSA")) {
            if (size <= 256)
                hash = "SHA256";
            else if (size <= 384)
                hash = "SHA384";
            else
                hash = "SHA512";
        } else {
            if (size <= 1024)
                hash = "SHA1";
            else if (size <= 2048)
                hash = "SHA256";
            else if (size <= 3072)
                hash = "SHA384";
            else
                hash = "SHA512";
        }
        return hash + "with" + keyalg;
    }

    /** 
     *  Get a private key out of a keystore
     *
     *  @param ks path to the keystore
     *  @param ksPW the keystore password, may be null
     *  @param alias the name of the key
     *  @param keyPW the key password, must be at least 6 characters
     *  @return the key or null if not found
     */
    public static PrivateKey getPrivateKey(File ks, String ksPW, String alias, String keyPW)
                              throws GeneralSecurityException, IOException {
        InputStream fis = null;
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            fis = new FileInputStream(ks);
            char[] pwchars = ksPW != null ? ksPW.toCharArray() : null;
            keyStore.load(fis, pwchars);
            char[] keypwchars = keyPW.toCharArray();
            return (PrivateKey) keyStore.getKey(alias, keypwchars);
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ioe) {}
        }
    }

    /** 
     *  Get a cert out of a keystore
     *
     *  @param ks path to the keystore
     *  @param ksPW the keystore password, may be null
     *  @param alias the name of the key
     *  @return the certificate or null if not found
     */
    public static Certificate getCert(File ks, String ksPW, String alias)
                              throws GeneralSecurityException, IOException {
        InputStream fis = null;
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            fis = new FileInputStream(ks);
            char[] pwchars = ksPW != null ? ksPW.toCharArray() : null;
            keyStore.load(fis, pwchars);
            return keyStore.getCertificate(alias);
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ioe) {}
        }
    }

    /** 
     *  Pull the cert back OUT of the keystore and save it in Base64-encoded X.509 format
     *  so the clients can get to it.
     *
     *  @param ks path to the keystore
     *  @param ksPW the keystore password, may be null
     *  @param alias the name of the key
     *  @param certFile output
     *  @return success
     *  @since 0.8.3 moved from SSLClientListenerRunner in 0.9.9
     */
    public static boolean exportCert(File ks, String ksPW, String alias, File certFile) {
        InputStream fis = null;
        try {
            Certificate cert = getCert(ks, ksPW, alias);
            if (cert != null)
                return CertUtil.saveCert(cert, certFile);
        } catch (GeneralSecurityException gse) {
            error("Error saving ASCII SSL keys", gse);
        } catch (IOException ioe) {
            error("Error saving ASCII SSL keys", ioe);
        }
        return false;
    }

    private static void info(String msg) {
        log(I2PAppContext.getGlobalContext(), Log.INFO, msg, null);
    }

    /** @since 0.9.17 */
    private static void warn(String msg, Throwable t) {
        log(I2PAppContext.getGlobalContext(), Log.WARN, msg, t);
    }

    private static void error(String msg, Throwable t) {
        log(I2PAppContext.getGlobalContext(), Log.ERROR, msg, t);
    }

    //private static void info(I2PAppContext ctx, String msg) {
    //    log(ctx, Log.INFO, msg, null);
    //}

    //private static void error(I2PAppContext ctx, String msg, Throwable t) {
    //    log(ctx, Log.ERROR, msg, t);
    //}

    private static void log(I2PAppContext ctx, int level, String msg, Throwable t) {
        if (level >= Log.WARN && !ctx.isRouterContext()) {
            System.out.println(msg);
            if (t != null)
                t.printStackTrace();
        }
        Log l = ctx.logManager().getLog(KeyStoreUtil.class);
        l.log(level, msg, t);
    }

    /**
     *   Usage: KeyStoreUtil (loads from system keystore)
     *          KeyStoreUtil foo.ks (loads from system keystore, and from foo.ks keystore if exists, else creates empty)
     *          KeyStoreUtil certDir (loads from system keystore and all certs in certDir if exists)
     */
/****
    public static void main(String[] args) {
        File ksf = (args.length > 0) ? new File(args[0]) : null;
        try {
            if (ksf != null && !ksf.exists()) {
                createKeyStore(ksf, DEFAULT_KEYSTORE_PASSWORD);
                System.out.println("Created empty keystore " + ksf);
            } else {
                KeyStore ks = loadSystemKeyStore();
                if (ks != null) {
                    System.out.println("Loaded system keystore");
                    int count = countCerts(ks);
                    System.out.println("Found " + count + " certs");
                    if (ksf != null && ksf.isDirectory()) {
                        count = addCerts(ksf, ks);
                        System.out.println("Found " + count + " certs in " + ksf);
                        if (count > 0) {
                            // rerun blacklist as a test
                            _blacklistLogged = false;
                            count = removeBlacklistedCerts(ks);
                            if (count > 0)
                                System.out.println("Found " + count + " blacklisted certs in " + ksf);
                        }
                    }
                } else {
                    System.out.println("FAIL");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
****/
}
