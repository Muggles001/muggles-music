package top.boluofan.musictv.source;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class SourceCryptoBridge {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SourceCryptoBridge() {}

    static String md5(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String randomBytes(int size) {
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid random byte size");
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    static String aes(String dataBase64, String rawMode, String keyBase64, String ivBase64) {
        try {
            byte[] data = Base64.decode(dataBase64, Base64.NO_WRAP);
            byte[] key = Base64.decode(keyBase64, Base64.NO_WRAP);
            byte[] iv = ivBase64.isEmpty() ? new byte[16] : Base64.decode(ivBase64, Base64.NO_WRAP);
            String mode = rawMode.toLowerCase(Locale.US);
            String transformation = mode.contains("ecb") ? "AES/ECB/NoPadding" : "AES/CBC/PKCS5Padding";
            Cipher cipher = Cipher.getInstance(transformation);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            if (transformation.contains("ECB")) {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            }
            return Base64.encodeToString(cipher.doFinal(data), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalArgumentException("AES 加密失败", e);
        }
    }

    static String rsa(String dataBase64, String pemKey) {
        try {
            String normalizedKey = pemKey
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(Base64.decode(normalizedKey, Base64.DEFAULT)));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.encodeToString(
                    cipher.doFinal(Base64.decode(dataBase64, Base64.NO_WRAP)), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalArgumentException("RSA 加密失败", e);
        }
    }
}
