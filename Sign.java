import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/** Firma (v2+v3) y verificación de APK con apksig. Uso: Sign in out keystore pass | verify apk */
public class Sign {
    public static void main(String[] a) throws Exception {
        if (a[0].equals("verify")) {
            ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[1])).build().verify();
            System.out.println("verified=" + r.isVerified()
                    + " v2=" + r.isVerifiedUsingV2Scheme()
                    + " v3=" + r.isVerifiedUsingV3Scheme());
            for (ApkVerifier.IssueWithParams e : r.getErrors()) System.out.println("ERR " + e);
            return;
        }
        String in = a[0], out = a[1], ks = a[2], pass = a[3];
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        char[] pw = pass.toCharArray();
        try (FileInputStream fis = new FileInputStream(ks)) { keyStore.load(fis, pw); }
        PrivateKey key = (PrivateKey) keyStore.getKey("launcher", pw);
        X509Certificate cert = (X509Certificate) keyStore.getCertificate("launcher");
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("launcher", key, List.of(cert)).build();
        new ApkSigner.Builder(List.of(cfg))
                .setInputApk(new File(in)).setOutputApk(new File(out))
                .setV1SigningEnabled(false).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .setMinSdkVersion(26).build().sign();
        System.out.println("signed " + out);
    }
}
