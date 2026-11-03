import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs an APK (v2 scheme: the TV is Android 10; apksig 2.3.0's v1 needs JDK internals that no longer exist) with apksig: java -cp apksig.jar Sign.java keystore password in.apk out.apk */
public class Sign {
    public static void main(String[] args) throws Exception {
        char[] password = args[1].toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[0])) {
            ks.load(in, password);
        }
        String alias = ks.aliases().nextElement();
        PrivateKey key = (PrivateKey) ks.getKey(alias, password);
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("inicio", key,
                Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[2]))
                .setOutputApk(new File(args[3]))
                .setMinSdkVersion(28)
                .setV1SigningEnabled(false)
                .build()
                .sign();
    }
}
