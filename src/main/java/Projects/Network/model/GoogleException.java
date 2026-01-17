package Projects.Network.model;

/**
 * Exception personnalisée pour les erreurs Google Auth
 */
public class GoogleException extends RuntimeException {
    public GoogleException(String message) {
        super(message);
    }

    public GoogleException(String message, Throwable cause) {
        super(message, cause);
    }
}