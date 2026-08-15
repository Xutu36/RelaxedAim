import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HexFormat;

/**
 * ZombieBuddy Ed25519 密钥生成工具（Windows 无 openssl 时的替代）。
 *
 * 用法（JDK 15+，本仓库用 JDK 25）：
 *   javac GenKey.java
 *   java GenKey [私钥输出路径，默认 ./ed25519-private.der]
 *
 * 输出：
 *   - 私钥：PKCS#8 DER 文件（填入 ~/.gradle/gradle.properties 的 zbsPrivateKeyFile，私钥保密，勿提交）
 *   - 公钥：打印 64 位十六进制，即 Steam 个人资料简介中需要发布的 JavaModZBS:<公钥>
 */
public class GenKey {
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "ed25519-private.der";

        KeyPairGenerator gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = gen.generateKeyPair();

        byte[] priv = kp.getPrivate().getEncoded();          // PKCS#8 DER
        Files.write(Path.of(out), priv);

        byte[] pubDer = kp.getPublic().getEncoded();          // X.509 DER
        byte[] raw = new byte[32];                            // 原始 ed25519 公钥 = X.509 尾部 32 字节
        System.arraycopy(pubDer, pubDer.length - raw.length, raw, 0, raw.length);

        System.out.println("Private key written to : " + out);
        System.out.println("Public key (JavaModZBS): " + HexFormat.of().formatHex(raw));
    }
}
