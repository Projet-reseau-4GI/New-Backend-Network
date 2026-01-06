package Projects.Network.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AuthResponse
 *
 * This Data Transfer Object (DTO) is used to encapsulate the response returned
 * to the client after a successful authentication process.
 *
 * Its primary role is to transport authentication-related data between the
 * backend and the client without exposing internal domain models.
 *
 * This class belongs to the DTO layer and must not contain any business logic.
 * It is designed only to carry data in a structured and secure manner.
 *
 * The presence of a JWT token allows the client to authenticate subsequent
 * requests when accessing protected resources.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@Data
@AllArgsConstructor
public class AuthResponse {

    /**
     * Human-readable message returned to the client.
     *
     * This message is typically used to indicate that the authentication
     * process was successful. It can be displayed directly in the user
     * interface or used by the client application for logging purposes.
     */
    private String message;

    /**
     * JSON Web Token (JWT) generated after successful authentication.
     *
     * This token is used by the client to authenticate future HTTP requests.
     * It is usually sent in the Authorization header using the Bearer scheme.
     *
     * The token contains encoded information about the authenticated user
     * and must be handled securely by the client.
     */
    private String token;

}
