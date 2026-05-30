import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestBCrypt {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Ahmedjalil2006@";
        String hashed = "$2a$10$vI8A8vfxF9JmUq8V1Ea8e.gXgOQ7P9h5L8yE7z1QZ6F2f3K5u7G3m";
        System.out.println("Match: " + encoder.matches(rawPassword, hashed));
    }
}
