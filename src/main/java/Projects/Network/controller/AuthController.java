package Projects.Network.controller;

import Projects.Network.model.User;
import Projects.Network.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for user authentication and registration operations.
 *
 * This controller provides HTTP endpoints for user authentication and account management
 * in a reactive WebFlux application. It handles two primary operations: user login
 * (authentication) and user registration (account creation), both of which are essential
 * for securing application access and managing user identities.
 *
 * Architecture and design patterns:
 *
 * 1. RESTful API design:
 *    This controller follows REST principles by providing resource-based endpoints
 *    with appropriate HTTP methods (POST for creating resources and state changes).
 *    All endpoints return standardized JSON responses for easy client consumption.
 *
 * 2. Reactive programming model:
 *    All methods return Mono types (part of Project Reactor) which represent
 *    asynchronous, non-blocking operations. This allows the application to handle
 *    high concurrency without blocking threads, as operations are executed on
 *    the reactive event loop rather than dedicated threads per request.
 *
 *    Benefits of reactive approach:
 *    - Better resource utilization (fewer threads needed)
 *    - Higher throughput under load
 *    - Natural backpressure handling
 *    - Composable asynchronous operations
 *
 * 3. Separation of concerns:
 *    The controller focuses solely on HTTP request/response handling and delegates
 *    all business logic to the AuthService. This follows the Controller-Service pattern
 *    which provides clear separation between:
 *    - HTTP layer (this controller): Request parsing, response formatting, HTTP status codes
 *    - Business layer (AuthService): Authentication logic, password validation, token generation
 *    - Data layer (UserRepository): Database operations
 *
 * Authentication flow:
 *
 * Login process:
 * 1. Client sends POST request to /api/auth/login with email and password in JSON body
 * 2. Controller extracts credentials from LoginRequest DTO
 * 3. AuthService validates credentials against stored user data
 * 4. If valid, AuthService generates a JWT token containing user claims
 * 5. Controller returns 200 OK with JWT token in response body
 * 6. Client stores token (typically in localStorage or memory)
 * 7. Client includes token in Authorization header for subsequent requests
 * 8. JwtFilter validates token on each protected request
 *
 * Registration process:
 * 1. Client sends POST request to /api/auth/register with user details in JSON body
 * 2. Controller creates User entity from RegisterRequest DTO
 * 3. AuthService validates email uniqueness and password strength
 * 4. Password is hashed using BCrypt before database storage
 * 5. User record is saved to database with generated UUID
 * 6. Controller returns 201 CREATED with user ID and email
 * 7. Client can now use credentials to login via /api/auth/login
 *
 * Security considerations:
 *
 * 1. Password handling:
 *    Passwords are never logged or exposed in responses. They are immediately
 *    hashed by AuthService before storage. The plaintext password exists only
 *    briefly in memory during the request processing.
 *
 * 2. Error messages:
 *    Error responses provide minimal information to prevent user enumeration attacks.
 *    The same "Invalid credentials" message is returned whether the email doesn't exist
 *    or the password is wrong, preventing attackers from determining valid emails.
 *
 * 3. Rate limiting (recommended):
 *    Production deployments should implement rate limiting on these endpoints to prevent:
 *    - Brute force password guessing attacks
 *    - Account enumeration attempts
 *    - Automated bot registrations
 *    Consider limiting to 5 attempts per IP per minute.
 *
 * 4. Input validation (recommended enhancement):
 *    While basic validation exists in AuthService, consider adding:
 *    - Email format validation using regex or dedicated library
 *    - Password complexity requirements (length, character types)
 *    - Request body size limits to prevent memory exhaustion
 *    - Field presence validation before processing
 *
 * Response structure:
 * All responses follow a consistent JSON structure with appropriate HTTP status codes:
 *
 * Success responses:
 * - Login: {token: "jwt-token-string", message: "Login successful"}
 * - Register: {message: "User registered successfully", email: "user@example.com", userId: "uuid"}
 *
 * Error responses:
 * - {error: "Error category", details: "Specific error message"}
 *
 * HTTP status codes used:
 * - 200 OK: Successful login
 * - 201 CREATED: Successful registration
 * - 400 BAD REQUEST: Invalid input or registration failure
 * - 401 UNAUTHORIZED: Invalid credentials during login
 *
 * Testing considerations:
 * When testing this controller:
 * - Mock AuthService to test controller logic in isolation
 * - Verify correct HTTP status codes for various scenarios
 * - Test request body parsing with valid and invalid JSON
 * - Verify response structure matches expected format
 * - Test error handling for service-layer exceptions
 *
 * Integration with frontend:
 * Frontend applications should:
 * - Store JWT token securely (avoid localStorage for sensitive apps, prefer memory)
 * - Include token in Authorization header as "Bearer <token>"
 * - Handle 401 responses by redirecting to login page
 * - Implement token refresh logic before expiration
 * - Clear token on logout
 *
 * Monitoring and logging:
 * Consider logging:
 * - Successful login attempts (email, timestamp, IP address)
 * - Failed login attempts (for security monitoring)
 * - Registration events (new user creation)
 * - Token generation events
 * Do NOT log: passwords, tokens, or other sensitive data
 *
 * Future enhancements:
 * - Add email verification for new registrations
 * - Implement password reset functionality
 * - Add multi-factor authentication (MFA) support
 * - Implement refresh token mechanism for long-lived sessions
 * - Add OAuth2/OpenID Connect for social login
 * - Implement account lockout after multiple failed attempts
 * - Add CAPTCHA for bot prevention
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * JSON response key constants to ensure consistency across all endpoints.
     *
     * Using constants for JSON keys provides several benefits:
     * - Compile-time checking prevents typos in key names
     * - Centralized definition makes refactoring easier
     * - Self-documenting code (constant names explain purpose)
     * - Easier to maintain consistent API contract
     * - IDE autocomplete reduces errors
     *
     * These constants define the structure of JSON responses returned to clients.
     * Any changes to these values will affect the API contract and may require
     * client applications to update their response parsing logic.
     */
    private static final String TOKEN_KEY = "token";
    private static final String MESSAGE_KEY = "message";
    private static final String ERROR_KEY = "error";
    private static final String DETAILS_KEY = "details";
    private static final String EMAIL_KEY = "email";
    private static final String USER_ID_KEY = "userId";

    /**
     * Standardized message constants for consistent user communication.
     *
     * These constants define the exact text returned in API responses for various
     * scenarios. Using constants ensures:
     * - Consistent messaging across the application
     * - Easier internationalization (i18n) implementation in the future
     * - Single point of change if messages need updating
     * - Clear documentation of possible response messages
     *
     * Message design considerations:
     * - Keep messages user-friendly and non-technical
     * - Avoid exposing internal implementation details
     * - Provide enough information to be helpful
     * - Don't reveal information useful to attackers (e.g., "email exists" vs "invalid credentials")
     */
    private static final String LOGIN_SUCCESS_MESSAGE = "Login successful";
    private static final String INVALID_CREDENTIALS_ERROR = "Invalid credentials";
    private static final String REGISTRATION_SUCCESS_MESSAGE = "User registered successfully";
    private static final String REGISTRATION_FAILED_ERROR = "Registration failed";

    /**
     * Injected AuthService for handling authentication business logic.
     *
     * This service is injected via constructor injection (enabled by @RequiredArgsConstructor)
     * and provides all authentication-related operations including:
     * - Credential validation against stored user data
     * - Password verification using BCrypt
     * - JWT token generation and signing
     * - User registration with password hashing
     * - Email uniqueness validation
     *
     * The 'final' modifier ensures this dependency is immutable after construction,
     * preventing accidental reassignment and making the class thread-safe.
     *
     * Dependency injection benefits:
     * - Loose coupling between controller and service
     * - Easy to mock in unit tests
     * - Spring manages service lifecycle and dependencies
     * - Clear declaration of dependencies
     */
    private final AuthService authService;

    /**
     * Authenticates a user with email and password credentials and returns a JWT token.
     *
     * This endpoint implements the login functionality, which is the primary way users
     * authenticate themselves to access protected resources in the application. Upon
     * successful authentication, a JWT (JSON Web Token) is generated and returned,
     * which the client must include in subsequent requests to prove their identity.
     *
     * Endpoint details:
     * - HTTP Method: POST (appropriate for authentication as it changes server state)
     * - URL: /api/auth/login
     * - Content-Type: application/json
     * - Request Body: JSON object with email and password fields
     * - Response: JSON object with JWT token and success message
     *
     * Authentication process flow:
     *
     * 1. Request reception and parsing:
     *    Spring automatically deserializes the JSON request body into a LoginRequest
     *    object using Jackson. If the JSON is malformed or missing required fields,
     *    a 400 Bad Request is returned automatically by Spring.
     *
     * 2. Credential extraction:
     *    Email and password are extracted from the LoginRequest DTO. These are then
     *    passed to AuthService for validation.
     *
     * 3. User lookup and validation (in AuthService):
     *    - User is retrieved from database by email
     *    - If user doesn't exist, authentication fails
     *    - If user exists, stored password hash is retrieved
     *    - BCrypt is used to verify plaintext password against stored hash
     *    - Verification is intentionally slow (~100ms) to prevent brute force attacks
     *
     * 4. JWT token generation (in AuthService):
     *    If credentials are valid, a JWT token is created containing:
     *    - User ID (subject claim)
     *    - Email (custom claim)
     *    - User roles/permissions (custom claims)
     *    - Token issuer (application identifier)
     *    - Issued at timestamp
     *    - Expiration timestamp (typically 15-60 minutes)
     *    - Digital signature using secret key (prevents tampering)
     *
     * 5. Response construction:
     *    The token is packaged into a JSON response with a success message.
     *    HTTP status 200 OK indicates successful authentication.
     *
     * Error handling:
     * The onErrorResume operator catches any exceptions thrown during authentication
     * and converts them into appropriate HTTP error responses:
     *
     * - Invalid credentials (wrong password or non-existent email):
     *   Returns 401 UNAUTHORIZED with generic error message
     *   Note: We intentionally don't distinguish between "user not found" and
     *   "wrong password" to prevent user enumeration attacks
     *
     * - Database errors or service failures:
     *   Returns 401 UNAUTHORIZED (same as invalid credentials)
     *   Actual error is logged server-side for debugging
     *
     * Security considerations:
     *
     * 1. Timing attacks:
     *    BCrypt's constant-time comparison helps prevent timing attacks where
     *    attackers measure response times to determine if an email exists.
     *
     * 2. Brute force protection:
     *    Consider implementing:
     *    - Rate limiting (e.g., 5 attempts per IP per minute)
     *    - Account lockout after N failed attempts
     *    - CAPTCHA after multiple failures
     *    - IP-based blocking for repeated failures
     *
     * 3. Password transmission:
     *    In production, ALWAYS use HTTPS to encrypt credentials in transit.
     *    Plain HTTP exposes passwords to network sniffing attacks.
     *
     * 4. Token security:
     *    - JWT tokens should have short expiration times
     *    - Implement token refresh mechanism for long sessions
     *    - Consider token revocation for logout functionality
     *    - Store token securely on client (avoid localStorage for sensitive apps)
     *
     * 5. Error information leakage:
     *    Error responses deliberately provide minimal information to prevent:
     *    - User enumeration (determining which emails have accounts)
     *    - Information about internal system structure
     *    - Hints that could aid brute force attacks
     *
     * JWT token structure:
     * The returned token has three Base64-encoded parts separated by dots:
     * header.payload.signature
     *
     * Example: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIn0.signature
     *
     * Clients should:
     * - Store the token securely
     * - Include it in the Authorization header as "Bearer <token>"
     * - Handle token expiration gracefully
     * - Clear token on logout
     *
     * Client usage example:
     * POST /api/auth/login
     * Content-Type: application/json
     *
     * {
     *   "email": "user@example.com",
     *   "password": "SecurePassword123"
     * }
     *
     * Response (200 OK):
     * {
     *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "message": "Login successful"
     * }
     *
     * Response (401 UNAUTHORIZED):
     * {
     *   "error": "Invalid credentials",
     *   "details": "Email or password is incorrect"
     * }
     *
     * Monitoring and logging:
     * Consider logging:
     * - Successful logins (email, timestamp, IP)
     * - Failed login attempts (email, timestamp, IP)
     * - Multiple failed attempts from same IP
     * Never log: passwords or JWT tokens
     *
     * Performance considerations:
     * - BCrypt verification is CPU-intensive by design (~100ms per attempt)
     * - Database lookup for user by email should use indexed column
     * - Consider caching user data if login rate is very high
     * - Monitor authentication endpoint latency
     *
     * Testing:
     * Test cases should cover:
     * - Valid credentials return token and 200 OK
     * - Invalid password returns 401 UNAUTHORIZED
     * - Non-existent email returns 401 UNAUTHORIZED
     * - Malformed JSON returns 400 BAD REQUEST
     * - Missing fields returns 400 BAD REQUEST
     * - Generated token is valid and can be used for authentication
     *
     * @param request the LoginRequest DTO containing user credentials (email and password)
     *                extracted from the JSON request body, automatically deserialized by Spring
     * @return a Mono that emits a ResponseEntity containing a Map with JWT token and success message
     *         on successful authentication (HTTP 200 OK), or error details on failure (HTTP 401 UNAUTHORIZED)
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest request) {
        return authService.login(request.getEmail(), request.getPassword())
                .map(token -> {
                    Map<String, String> response = new HashMap<>();
                    response.put(TOKEN_KEY, token);
                    response.put(MESSAGE_KEY, LOGIN_SUCCESS_MESSAGE);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    Map<String, String> error = new HashMap<>();
                    error.put(ERROR_KEY, INVALID_CREDENTIALS_ERROR);
                    error.put(DETAILS_KEY, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error));
                });
    }

    /**
     * Registers a new user account in the system.
     *
     * This endpoint handles user registration (account creation), which is the process
     * of adding a new user to the system with their chosen credentials and personal
     * information. Registration is a prerequisite for authentication and must ensure
     * data integrity, uniqueness constraints, and security requirements are met.
     *
     * Endpoint details:
     * - HTTP Method: POST (creates a new user resource)
     * - URL: /api/auth/register
     * - Content-Type: application/json
     * - Request Body: JSON object with email, password, firstName, lastName
     * - Response: JSON object with success message, email, and generated user ID
     *
     * Registration process flow:
     *
     * 1. Request reception and parsing:
     *    Spring deserializes the JSON request body into a RegisterRequest DTO.
     *    This DTO contains all required fields for creating a new user account:
     *    - email: User's email address (used for login and communication)
     *    - password: User's chosen password (will be hashed before storage)
     *    - firstName: User's first name (for personalization)
     *    - lastName: User's last name (for identification)
     *
     * 2. User entity creation:
     *    The createUserFromRequest private method transforms the DTO into a User entity.
     *    This separation between DTO and entity provides:
     *    - Clear boundary between API contract and internal data model
     *    - Protection against over-posting attacks (clients can't set internal fields)
     *    - Flexibility to change entity structure without affecting API
     *
     * 3. Validation and persistence (in AuthService):
     *
     *    a) Email uniqueness check:
     *       The service verifies that no existing user has the same email address.
     *       Email is used as the unique identifier for login, so duplicates would
     *       cause authentication ambiguity and security issues.
     *
     *       Implementation: Database query with unique constraint on email column.
     *       If duplicate found, registration fails with appropriate error message.
     *
     *    b) Password validation:
     *       While basic validation exists, production systems should enforce:
     *       - Minimum length (e.g., 8-12 characters)
     *       - Character complexity (uppercase, lowercase, numbers, special chars)
     *       - No common passwords (check against known weak password lists)
     *       - No personal information (name, email) in password
     *
     *    c) Password hashing:
     *       The plaintext password is immediately hashed using BCrypt before storage.
     *       BCrypt properties:
     *       - Generates unique salt for each password
     *       - Uses configurable work factor (default 10 = 2^10 rounds)
     *       - Produces 60-character hash including algorithm version and salt
     *       - Intentionally slow to prevent brute force attacks
     *       - One-way function (impossible to reverse to get original password)
     *
     *       Hash format: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
     *       Structure: $version$cost$salt(22 chars)$hash(31 chars)
     *
     *    d) User ID generation:
     *       A UUID (Universally Unique Identifier) is automatically generated for the user.
     *       UUIDs provide:
     *       - Globally unique identifiers without coordination
     *       - 128-bit random value (2^128 possible values)
     *       - Non-sequential IDs prevent enumeration attacks
     *       - Can be generated without database round-trip
     *
     *       Format: 550e8400-e29b-41d4-a716-446655440000 (36 characters with hyphens)
     *
     *    e) Database insertion:
     *       The complete User entity with hashed password and generated UUID is
     *       inserted into the users table. This operation is:
     *       - Atomic: Either fully succeeds or fully fails
     *       - Constraints-checked: Email uniqueness is enforced at database level
     *       - Transactional: Can be rolled back if part of larger operation
     *
     * 4. Success response construction:
     *    On successful registration, a JSON response is returned containing:
     *    - Success message confirming account creation
     *    - User's email (for confirmation)
     *    - Generated user ID (for reference in client applications)
     *
     *    HTTP status 201 CREATED indicates a new resource was successfully created,
     *    which is semantically correct for user registration.
     *
     * Error handling and scenarios:
     *
     * The onErrorResume operator catches exceptions and converts them to HTTP responses:
     *
     * 1. Duplicate email (most common error):
     *    - Cause: Email already registered in system
     *    - Response: 400 BAD REQUEST
     *    - Message: "Registration failed" with details about duplicate email
     *    - Client action: Display error, suggest password reset if user forgot account
     *
     * 2. Invalid email format:
     *    - Cause: Email doesn't match expected pattern
     *    - Response: 400 BAD REQUEST
     *    - Message: Details about email format requirements
     *    - Client action: Display validation error on email field
     *
     * 3. Weak password:
     *    - Cause: Password doesn't meet complexity requirements
     *    - Response: 400 BAD REQUEST
     *    - Message: Details about password requirements
     *    - Client action: Display validation errors with specific requirements
     *
     * 4. Database errors:
     *    - Cause: Connection issues, constraint violations, disk full, etc.
     *    - Response: 400 BAD REQUEST (generic registration failed message)
     *    - Server action: Log detailed error for investigation
     *    - Client action: Display generic error, suggest retry
     *
     * 5. Validation errors (missing fields):
     *    - Cause: Required fields not provided in request
     *    - Response: 400 BAD REQUEST (handled by Spring before reaching this method)
     *    - Message: Details about missing fields
     *    - Client action: Highlight missing fields in UI
     *
     * Security considerations:
     *
     * 1. Input sanitization:
     *    All user inputs should be sanitized to prevent:
     *    - SQL injection (use parameterized queries)
     *    - XSS attacks (escape HTML in names if displayed on web)
     *    - Command injection (if names are used in system commands)
     *
     * 2. Email verification (recommended enhancement):
     *    After registration, send verification email with unique token:
     *    - Prevents fake email registrations
     *    - Ensures user owns the email address
     *    - Provides communication channel for password resets
     *    - Account remains inactive until email verified
     *
     * 3. Rate limiting:
     *    Prevent automated bulk registrations:
     *    - Limit registrations per IP (e.g., 5 per hour)
     *    - Implement CAPTCHA after multiple attempts
     *    - Block disposable email domains if appropriate
     *    - Monitor for suspicious patterns
     *
     * 4. Data privacy (GDPR compliance):
     *    - Collect only necessary data (minimal data principle)
     *    - Provide clear privacy policy
     *    - Allow users to delete accounts
     *    - Implement data export functionality
     *    - Log consent for data processing
     *
     * 5. Password security:
     *    - Never store passwords in plaintext
     *    - Never log passwords
     *    - Don't send passwords in emails
     *    - Enforce strong password policies
     *    - Consider password strength meter in UI
     *
     * Post-registration workflow:
     *
     * After successful registration, consider:
     * 1. Send welcome email with account confirmation link
     * 2. Auto-login user (generate and return JWT token)
     * 3. Redirect to onboarding flow or dashboard
     * 4. Create default user preferences/settings
     * 5. Log registration event for analytics
     *
     * Client usage example:
     * POST /api/auth/register
     * Content-Type: application/json
     *
     * {
     *   "email": "newuser@example.com",
     *   "password": "SecurePassword123!",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     *
     * Response (201 CREATED):
     * {
     *   "message": "User registered successfully",
     *   "email": "newuser@example.com",
     *   "userId": "550e8400-e29b-41d4-a716-446655440000"
     * }
     *
     * Response (400 BAD REQUEST):
     * {
     *   "error": "Registration failed",
     *   "details": "Email already registered"
     * }
     *
     * Database schema requirements:
     * The users table must have:
     * - user_id: UUID PRIMARY KEY
     * - email: VARCHAR(255) UNIQUE NOT NULL
     * - password: VARCHAR(60) NOT NULL (BCrypt hash is 60 chars)
     * - first_name: VARCHAR(100)
     * - last_name: VARCHAR(100)
     * - created_at: TIMESTAMP DEFAULT CURRENT_TIMESTAMP
     *
     * Monitoring and analytics:
     * Track:
     * - Registration rate (users per day/week/month)
     * - Registration failures and reasons
     * - Email domains of registering users
     * - Geographic distribution of new users
     * - Drop-off points in registration flow
     *
     * Testing:
     * Test cases should cover:
     * - Valid data creates user and returns 201 CREATED
     * - Duplicate email returns 400 BAD REQUEST
     * - Invalid email format returns 400 BAD REQUEST
     * - Weak password returns 400 BAD REQUEST
     * - Missing required fields returns 400 BAD REQUEST
     * - Password is properly hashed in database
     * - UUID is generated and stored correctly
     * - User can login immediately after registration
     *
     * @param request the RegisterRequest DTO containing all required information for creating
     *                a new user account (email, password, firstName, lastName), automatically
     *                deserialized from the JSON request body by Spring's message converters
     * @return a Mono that emits a ResponseEntity containing a Map with success message, user email,
     *         and generated user ID on successful registration (HTTP 201 CREATED), or error details
     *         on failure (HTTP 400 BAD REQUEST)
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, String>>> register(@RequestBody RegisterRequest request) {
        User user = createUserFromRequest(request);

        return authService.register(user)
                .map(savedUser -> {
                    Map<String, String> response = new HashMap<>();
                    response.put(MESSAGE_KEY, REGISTRATION_SUCCESS_MESSAGE);
                    response.put(EMAIL_KEY, savedUser.getEmail());
                    response.put(USER_ID_KEY, savedUser.getUserId().toString());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .onErrorResume(e -> {
                    Map<String, String> error = new HashMap<>();
                    error.put(ERROR_KEY, REGISTRATION_FAILED_ERROR);
                    error.put(DETAILS_KEY, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error));
                });
    }

/**
 * Creates a User entity from a RegisterRequest DTO.
 *
 * This private helper method performs the transformation from the API-layer data
 * transfer object (RegisterRequest) to the domain-layer entity (User). This separation
 * is a key principle of layered architecture and provides several important benefits.
 *
 * Purpose and responsibilities:
 *
 * 1. Data transformation:
 *    Maps fields from RegisterRequest (API contract) to User entity (domain model).
 *    While the mapping is currently straightforward (field names match), this
 *    abstraction allows for future flexibility if the structures diverge.
 *
 * 2. Encapsulation:
 *    Centralizes the conversion logic in one place. If the User entity structure
 *    changes (e.g., new required fields, different field names, computed fields),
 *    only this method needs updating rather than every place that creates users.
 *
 * 3. Security boundary:
 *    Acts as a controlled entry point for creating User entities. The RegisterRequest
 *    DTO contains only fields that clients are allowed to set. Internal fields like
 *    userId, createdAt, roles, or accountStatus cannot be set via the API, preventing
 *    privilege escalation attacks where clients might try to register as administrators.
 *
 *    Example attack prevented:
 *    Without this separation, a malicious client could send:
 *    {
 *      "email": "attacker@example.com",
 *      "password": "password",
 *      "role": "ADMIN"              // Attempting to set admin role
 *    }
 *
 *    With proper DTO-to-entity conversion, the role field is ignored because
 *    RegisterRequest doesn't have a role field, and this method doesn't set one.
 *
 * 4. Validation preparation:
 *    Provides a single point where default values can be set and initial
 *    validation can be performed before the entity reaches the service layer.
 *
 *    Future enhancements could include:
 *    - Trimming whitespace from names
 *    - Normalizing email to lowercase
 *    - Setting default values for optional fields
 *    - Performing basic format validation
 *
 * Architecture pattern:
 * This follows the DTO (Data Transfer Object) pattern where:
 * - DTOs define the API contract (what clients send/receive)
 * - Entities define the domain model (how data is stored and processed internally)
 * - Mapper methods (like this one) handle the conversion between layers
 *
 * This pattern is especially important in:
 * - REST APIs where API structure must remain stable for backward compatibility
 * - Systems where internal and external representations differ significantly
 * - Applications with complex validation or business rules during entity creation
 *
 * Current implementation:
 * The method creates a new User instance and sets four fields:
 * - email: Used for authentication and communication
 * - password: Will be hashed by AuthService before storage
 * - firstName: User's given name for personalization
 * - lastName: User's family name for identification
 *
 * Fields NOT set here (handled elsewhere):
 * - userId: Auto-generated by database or service layer as UUID
 * - createdAt: Set by database with DEFAULT CURRENT_TIMESTAMP
 * - updatedAt: Set by database on INSERT and UPDATE operations
 * - roles: Assigned by admin or default role set in service layer
 * - accountStatus: Defaults to 'PENDING' or 'ACTIVE' in service layer
 * - lastLoginAt: Remains null until first login
 * - emailVerified: Set to false initially, updated after email confirmation
 *
 * Why this is a private method:
 * - Only used internally by this controller
 * - Not part of the controller's public API
 * - Implementation detail that clients don't need to know about
 * - Can be refactored without affecting other classes
 *
 * Alternative approaches:
 * *
 * * 1. Dedicated mapper class:
 * *    For complex mappings, consider a dedicated UserMapper class:
 * *    public class UserMapper {
 * *        public static User fromRegisterRequest(RegisterRequest dto) { ... }
 * *        public static UserResponse toUserResponse(User entity) { ... }
 * *    }
 * *
 * * 2. MapStruct library:
 * *    For applications with many entities, use MapStruct for automatic mapping:
 * *    @Mapper
 * *    interface UserMapper {
 * *        User toEntity(RegisterRequest dto);
 * *    }
 * *
 * * 3. Builder pattern:
 * *    For entities with many fields, use builder for readability:
 * *    User user = User.builder()
 * *        .email(request.getEmail())
 * *        .password(request.getPassword())
 * *        .firstName(request.getFirstName())
 * *        .lastName(request.getLastName())
 * *        .build();
 * *
 * * Testing considerations:
 * * While private methods typically aren't tested directly, this method's behavior
 * * is tested indirectly through the register() endpoint tests. Verify that:
 * * - All RegisterRequest fields are correctly mapped to User entity
 * * - No extra fields are set that shouldn't be client-controlled
 * * - Null or empty values are handled appropriately
 * * - The created User entity is valid for persistence
 * *
 * * Future enhancements:
 * * As requirements grow, this method might be extended to:
 * * - Normalize email addresses (trim, lowercase)
 * * - Validate email format before entity creation
 * * - Set default values for optional fields
 * * - Initialize related entities (UserProfile, UserPreferences)
 * * - Apply business rules (e.g., VIP users get different defaults)
 * *
 * * @param request the RegisterRequest DTO containing user-provided data from the
 * *                registration form, including email, password, and name information
 * * @return a new User entity populated with data from the request, ready to be
 * *         passed to AuthService for validation, password hashing, and persistence
 *
 */


    private User createUserFromRequest(RegisterRequest request) {
 User user = new User();
 user.setEmail(request.getEmail());
 user.setPassword(request.getPassword());
 user.setFirstName(request.getFirstName());
 user.setLastName(request.getLastName());
 return user;
 }
    /**
     * Data transfer object for login requests.
     *
     * This inner class defines the structure of JSON payloads sent to the login endpoint.
     * It is a simple POJO (Plain Old Java Object) that serves as a contract between
     * the client and server, specifying exactly what data must be provided for authentication.
     *
     * Purpose and design:
     *
     * 1. API contract definition:
     *    This DTO explicitly defines what the login API expects, serving as documentation
     *    for API consumers and ensuring type safety during JSON deserialization.
     *
     * 2. Separation of concerns:
     *    By using a dedicated DTO rather than directly accepting User entities, we:
     *    - Prevent clients from sending unexpected fields
     *    - Protect against mass assignment vulnerabilities
     *    - Keep the API independent of internal entity structure
     *    - Make API evolution easier (can change entity without breaking API)
     *
     * 3. Lombok integration:
     *    The @Data annotation automatically generates:
     *    - Getters for all fields (getEmail(), getPassword())
     *    - Setters for all fields (setEmail(), setPassword())
     *    - toString() method for debugging
     *    - equals() and hashCode() for comparisons
     *    - Required args constructor (none in this case as all fields have default null)
     *
     *    This reduces boilerplate code and ensures consistent implementation of
     *    these common methods.
     *
     * Fields:
     *
     * - email (String):
     *   The user's email address, which serves as their unique identifier for login.
     *   Expected format: standard email address (e.g., user@example.com)
     *   Validation: Should be validated for format, but authentication will fail
     *              if email doesn't exist, so strict pre-validation is optional.
     *
     * - password (String):
     *   The user's plaintext password as entered in the login form.
     *   SECURITY NOTE: This field exists only temporarily in memory during request
     *                  processing. It is never stored and is immediately used for
     *                  BCrypt comparison, after which it becomes eligible for garbage
     *                  collection. The password should be transmitted over HTTPS only.
     *
     * JSON example:
     * {
     *   "email": "user@example.com",
     *   "password": "SecurePassword123"
     * }
     *
     * Security considerations:
     * - Passwords are not validated for format at this stage (any string is accepted)
     * - The actual security check happens in AuthService during BCrypt comparison
     * - This DTO should only be used over HTTPS to prevent password interception
     * - Consider implementing rate limiting to prevent brute force attacks
     *
     * Why an inner class:
     * Defining LoginRequest as a static inner class of AuthController provides:
     * - Logical grouping (DTO is co-located with its only consumer)
     * - Namespace organization (LoginRequest is scoped to AuthController)
     * - Reduced file clutter (fewer separate files to manage)
     * - Clear indication that this DTO is specific to this controller
     *
     * Alternative approaches:
     * For larger applications, consider:
     * - Separate DTO package (e.g., com.example.dto.auth.LoginRequest)
     * - Shared DTOs if multiple controllers use the same structure
     * - Validation annotations (@Email, @NotBlank, @Size) for automatic validation
     */
    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    /**
     * Data transfer object for registration requests.
     *
     * This inner class defines the structure of JSON payloads sent to the registration
     * endpoint. It extends beyond simple authentication credentials to include personal
     * information needed to create a complete user account in the system.
     *
     * Purpose and design:
     *
     * 1. Complete user profile data:
     *    Unlike LoginRequest which only needs credentials, RegisterRequest collects
     *    all information required to create a new user account, including personal
     *    details that will be stored in the database and used throughout the application.
     *
     * 2. API contract for registration:
     *    Explicitly defines what data must be provided during registration, serving as:
     *    - Documentation for frontend developers
     *    - Validation point for required fields
     *    - Protection against over-posting (clients can't set internal fields)
     *    - Contract that can evolve independently of the User entity
     *
     * 3. Data validation entry point:
     *    While validation is currently minimal, this DTO is the natural place to add
     *    Spring validation annotations in the future:
     *    @Email(message = "Invalid email format")
     *    @NotBlank(message = "Email is required")
     *    @Size(min = 8, message = "Password must be at least 8 characters")
     *
     * Fields:
     *
     * - email (String):
     *   The user's email address, which will be their unique identifier for login.
     *   Requirements:
     *   - Must be unique across all users (enforced at database level)
     *   - Must be valid email format (recommended to validate)
     *   - Will be used for communication (password resets, notifications)
     *   - Case insensitive (recommend normalizing to lowercase)
     *
     *   Example: "user@example.com"
     *   Database column: VARCHAR(255) UNIQUE NOT NULL
     *
     * - password (String):
     *   The user's chosen password, provided in plaintext.
     *   Security handling:
     *   - Transmitted over HTTPS only in production
     *   - Exists in memory only during request processing
     *   - Immediately hashed with BCrypt before database storage
     *   - Never logged or stored in plaintext
     *   - Should meet complexity requirements (recommended to enforce)
     *
     *   Recommended requirements:
     *   - Minimum 8 characters (12+ preferred)
     *   - Mix of uppercase and lowercase letters
     *   - At least one number
     *   - At least one special character
     *   - Not in common password dictionaries
     *
     *   Example: "SecureP@ssw0rd123"
     *   Database column: VARCHAR(60) NOT NULL (BCrypt hash size)
     *
     * - firstName (String):
     *   The user's given name or first name.
     *   Usage:
     *   - Personalization in UI ("Welcome, John!")
     *   - Display in user lists and profiles
     *   - Formal communications and documents
     *   - Search and filtering of users
     *
     *   Considerations:
     *   - Should handle international characters (Unicode support)
     *   - May be optional in some contexts
     *   - Recommend trimming whitespace
     *   - Consider character limits (e.g., 1-100 characters)
     *
     *   Example: "John"
     *   Database column: VARCHAR(100)
     *
     * - lastName (String):
     *   The user's family name or surname.
     *   Usage: Similar to firstName
     *
     *   Cultural considerations:
     *   - Some cultures don't use family names
     *   - Some cultures have multiple family names
     *   - Name order varies by culture
     *   - Consider making optional or using single "fullName" field
     *
     *   Example: "Doe"
     *   Database column: VARCHAR(100)
     *
     * JSON example:
     * {
     *   "email": "john.doe@example.com",
     *   "password": "SecureP@ssw0rd123",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     *
     * Validation strategy:
     *
     * Current state: Minimal validation (null checks in service layer)
     *
     * Recommended additions:
     * @Data
     * public static class RegisterRequest {
     *     @NotBlank(message = "Email is required")
     *     @Email(message = "Invalid email format")
     *     @Size(max = 255, message = "Email too long")
     *     private String email;
     *
     *     @NotBlank(message = "Password is required")
     *     @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
     *     @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
     *              message = "Password must contain uppercase, lowercase, and number")
     *     private String password;
     *
     *     @NotBlank(message = "First name is required")
     *     @Size(min = 1, max = 100, message = "First name must be 1-100 characters")
     *     private String firstName;
     *
     *     @NotBlank(message = "Last name is required")
     *     @Size(min = 1, max = 100, message = "Last name must be 1-100 characters")
     *     private String lastName;
     * }
     *
     * Alternative field considerations:
     *
     * Additional fields that might be useful:
     * - username: Separate from email if desired
     * - phoneNumber: For SMS verification or communication
     * - dateOfBirth: For age verification or personalization
     * - country: For localization or legal compliance
     * - termsAccepted: Boolean to track TOS agreement
     * - marketingOptIn: Boolean for GDPR compliance
     *
     * Security and privacy:
     * - Collect only necessary data (GDPR minimal data principle)
     * - Provide clear privacy policy during registration
     * - Allow users to modify/delete their data later
     * - Consider CAPTCHA to prevent automated registrations
     * - Implement rate limiting on registration endpoint
     *
     * Internationalization:
     * - Support Unicode characters in names
     * - Don't make assumptions about name structure
     * - Consider cultural differences in name formats
     * - Provide locale-appropriate validation messages
     *
     * Testing:
     * Test cases for this DTO structure:
     * - All fields present and valid → successful registration
     * - Missing email → validation error
     * - Invalid email format → validation error
     * - Password too short → validation error
     * - Missing names → validation error or use defaults
     * - Extra unexpected fields → ignored safely
     * - Special characters in names → handled correctly
     *
     * Why an inner class:
     * Same rationale as LoginRequest:
     * - Logical grouping with controller
     * - Clear scope and ownership
     * - Reduced file proliferation
     * - Easy to locate and maintain
     */
    @Data
    public static class RegisterRequest {
        private String email;
        private String password;
        private String firstName;
        private String lastName;
    }
}