import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/** 生成 RSA PEM 到 src/main/resources/keys（在 agent 根目录执行: javac scripts/GenKey.java && java -cp scripts GenKey） */
public class GenKey {
  public static void main(String[] args) throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();

    byte[] privDer = kp.getPrivate().getEncoded();
    byte[] pubDer = kp.getPublic().getEncoded();

    writePem("src/main/resources/keys/rsa-private.pem", "PRIVATE KEY", privDer);
    writePem("src/main/resources/keys/rsa-public.pem", "PUBLIC KEY", pubDer);
    System.out.println("OK: wrote src/main/resources/keys/rsa-private.pem and rsa-public.pem");
  }

  static void writePem(String path, String type, byte[] der) throws IOException {
    String b64 = Base64.getEncoder().encodeToString(der);
    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN ").append(type).append("-----\n");
    for (int i = 0; i < b64.length(); i += 64) {
      sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
    }
    sb.append("-----END ").append(type).append("-----\n");
    File f = new File(path);
    f.getParentFile().mkdirs();
    try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
      w.write(sb.toString());
    }
  }
}
