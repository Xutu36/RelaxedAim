import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VerifyZBS {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("usage: java VerifyZBS <jar> <zbs> <publicKeyHex>");
            return;
        }
        String jarPath = args[0];
        String zbsPath = args[1];
        String pubHex = args[2].toLowerCase();

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(jarPath)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) >= 0) {
                md.update(buf, 0, n);
            }
        }
        String jarSha = HexFormat.of().formatHex(md.digest());

        BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(zbsPath), StandardCharsets.UTF_8));
        String l1 = r.readLine();
        String l2 = r.readLine();
        String l3 = r.readLine();
        r.close();
        if (l1 == null || l2 == null || l3 == null) {
            System.out.println("Invalid .zbs: expected 3 lines");
            return;
        }
        l1 = l1.trim();
        l2 = l2.trim();
        l3 = l3.trim();
        if (!"ZBS".equals(l1)) {
            System.out.println("Invalid .zbs: first line not 'ZBS'");
            return;
        }
        Matcher m2 = Pattern.compile("^SteamID64:([0-9]{17})$").matcher(l2);
        if (!m2.matches()) {
            System.out.println("Invalid .zbs: bad SteamID64 line");
            return;
        }
        Matcher m3 = Pattern.compile("^Signature:([0-9a-fA-F]{128})$").matcher(l3);
        if (!m3.matches()) {
            System.out.println("Invalid .zbs: bad Signature line");
            return;
        }
        String sid = m2.group(1);
        byte[] sig = HexFormat.of().parseHex(m3.group(1).toLowerCase());
        byte[] pub = HexFormat.of().parseHex(pubHex);
        if (pub.length != 32) {
            System.out.println("Public key must be 32 bytes (64 hex)");
            return;
        }

        String canonical = "ZBS:" + sid + ":" + jarSha;
        byte[] msg = canonical.getBytes(StandardCharsets.UTF_8);

        byte[] x509 = new byte[12 + 32];
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        System.arraycopy(prefix, 0, x509, 0, 12);
        System.arraycopy(pub, 0, x509, 12, 32);

        KeyFactory kf = KeyFactory.getInstance("Ed25519");
        PublicKey pk = kf.generatePublic(new X509EncodedKeySpec(x509));
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pk);
        s.update(msg);
        boolean ok = s.verify(sig);

        System.out.println("JAR SHA-256 : " + jarSha);
        System.out.println("SteamID64   : " + sid);
        System.out.println("Canonical   : " + canonical);
        System.out.println("VERIFIED    : " + ok);
    }
}
