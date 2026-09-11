package com.fnphoto.tv.api;

import android.util.Log;

import com.fnphoto.tv.FnPhotoApplication;
import com.fnphoto.tv.R;

import org.conscrypt.Conscrypt;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class TlsUtils {
    private static final String TAG = "TlsUtils";
    private static boolean initialized = false;
    private static X509TrustManager combinedTrustManager;

    public static OkHttpClient.Builder enableTlsOnApi19(OkHttpClient.Builder builder) {
        installConscryptAndCerts();
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2", Conscrypt.newProvider());
            sslContext.init(null, new TrustManager[]{combinedTrustManager}, null);
            builder.sslSocketFactory(sslContext.getSocketFactory(), combinedTrustManager);
            builder.hostnameVerifier(ALLOW_ALL_HOSTNAME_VERIFIER);
            setDefaultHttpsSsl(sslContext);
            Log.i(TAG, "Conscrypt + custom CA trust configured");
        } catch (Exception e) {
            Log.e(TAG, "Conscrypt init failed, using platform TLS", e);
            try {
                SSLContext fallback = SSLContext.getInstance("TLSv1.2");
                fallback.init(null, null, null);
                builder.sslSocketFactory(fallback.getSocketFactory(), systemDefaultTrustManager());
            } catch (Exception e2) {
                Log.e(TAG, "Platform TLSv1.2 failed too, using default", e2);
            }
        }
        return builder;
    }

    public static void setDefaultHttpsSsl(SSLContext sslContext) {
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    public static void initGlobalTls() {
        installConscryptAndCerts();
        try {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2", Conscrypt.newProvider());
            sslContext.init(null, new TrustManager[]{combinedTrustManager}, null);
            setDefaultHttpsSsl(sslContext);
            Log.i(TAG, "Global TLS (HttpsURLConnection) configured for Glide");
        } catch (Exception e) {
            Log.e(TAG, "Global TLS init failed", e);
        }
    }

    private static final HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    private static void installConscryptAndCerts() {
        if (initialized) return;
        initialized = true;
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
            Log.i(TAG, "Conscrypt provider installed");
        } catch (Exception e) {
            Log.e(TAG, "Conscrypt install failed", e);
        }
        try {
            combinedTrustManager = createCombinedTrustManager();
            Log.i(TAG, "Combined trust manager created");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create combined trust manager", e);
            combinedTrustManager = systemDefaultTrustManager();
        }
    }

    private static X509TrustManager createCombinedTrustManager() {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);

            // Load custom CA certs from bundled PEM
            InputStream in = FnPhotoApplication.getAppContext()
                .getResources().openRawResource(R.raw.cacert);
            StringBuilder pemBuffer = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                pemBuffer.append((char) ch);
            }
            in.close();

            String pemData = pemBuffer.toString();
            String[] entries = pemData.split("-----BEGIN CERTIFICATE-----");
            int imported = 0;
            for (String entry : entries) {
                if (entry.contains("-----END CERTIFICATE-----")) {
                    String b64 = entry.substring(0, entry.indexOf("-----END CERTIFICATE-----"))
                        .replaceAll("\\s", "");
                    if (!b64.isEmpty()) {
                        try {
                            byte[] der = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                                new java.io.ByteArrayInputStream(der));
                            ks.setCertificateEntry("custom_" + imported, cert);
                            imported++;
                        } catch (Exception e) {
                            Log.w(TAG, "Skipping bad cert entry", e);
                        }
                    }
                }
            }
            Log.i(TAG, "Imported " + imported + " custom CA certs");

            // Create TrustManagerFactory with custom CAs
            TrustManagerFactory customTmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            customTmf.init(ks);
            X509TrustManager customTm = (X509TrustManager) customTmf.getTrustManagers()[0];

            // Get system default trust manager too
            X509TrustManager systemTm = systemDefaultTrustManager();

            // Return composite trust manager
            return new CompositeTrustManager(systemTm, customTm);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust manager", e);
        }
    }

    private static class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager custom;

        CompositeTrustManager(X509TrustManager system, X509TrustManager custom) {
            this.system = system;
            this.custom = custom;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            try {
                system.checkClientTrusted(chain, authType);
            } catch (java.security.cert.CertificateException e) {
                try {
                    custom.checkClientTrusted(chain, authType);
                } catch (java.security.cert.CertificateException e2) {
                    // 本地局域网环境，接受自签名证书
                }
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException e) {
                try {
                    custom.checkServerTrusted(chain, authType);
                } catch (java.security.cert.CertificateException e2) {
                    // 本地局域网环境，接受自签名证书
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            java.util.LinkedHashSet<X509Certificate> issuers = new java.util.LinkedHashSet<>();
            for (X509Certificate c : system.getAcceptedIssuers()) issuers.add(c);
            for (X509Certificate c : custom.getAcceptedIssuers()) issuers.add(c);
            return issuers.toArray(new X509Certificate[0]);
        }
    }

    private static X509TrustManager systemDefaultTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get default trust manager", e);
        }
        return null;
    }
}
