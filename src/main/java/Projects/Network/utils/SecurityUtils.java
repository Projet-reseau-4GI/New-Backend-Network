package Projects.Network.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for security operations like hashing.
 */
public class SecurityUtils {

    /**
     * Hashes a string using SHA-256 and returns the hex representation.
     * @param input the string to hash
     * @return the hex encoded hash
     */
    public static String hashApiKey(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing API Key", e);
        }
    }
}
