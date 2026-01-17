package Projects.Network.dto;

import lombok.*;

/**
 * DTO pour les informations utilisateur Google
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleUserInfo{
        public String email;
        String firstName;
        String lastName;
        String googleId;
        String picture;
        boolean emailVerified;
}
