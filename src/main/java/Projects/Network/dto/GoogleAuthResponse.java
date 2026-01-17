package Projects.Network.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoogleAuthResponse {
    private String token;
    private String email;
    private String name;
    private boolean isNewUser;
}