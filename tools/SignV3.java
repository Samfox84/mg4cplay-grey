import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
public class SignV3 {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) { ks.load(in, a[1].toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey(a[2], a[1].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(a[2]);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder(
                "CERT", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(new File(a[3]))
                .setOutputApk(new File(a[4]))
                .setMinSdkVersion(28)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build().sign();
        System.out.println("firmato v1+v2+v3: " + a[4]);
    }
}
