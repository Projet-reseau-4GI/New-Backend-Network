package Projects.Network.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Data Transfer Object for authentication response.
 * This class is used to send a success message and the JWT token to the client.
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    private String message; // Success message to be displayed
    private String token;   // JWT token for authentication
}