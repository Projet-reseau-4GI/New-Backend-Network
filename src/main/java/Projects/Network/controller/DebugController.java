package Projects.Network.controller;

import Projects.Network.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Debug controller for development and testing purposes.
 *
 * This controller provides utility endpoints that expose internal application data
 * for debugging, testing, and development purposes. It allows developers to quickly
 * inspect the state of the user database without requiring database client tools
 * or complex queries. These endpoints bypass normal authentication and authorization
 * mechanisms to provide direct, unfiltered access to data.
 *
 * CRITICAL SECURITY WARNING:
 * This controller is intended ONLY for development and testing environments.
 * It MUST be disabled, removed, or properly secured before deploying to production.
 * Leaving these endpoints accessible in production creates severe security risks.
 *
 * Security risks of exposing these endpoints in production:
 *
 * 1. User enumeration:
 *    Attackers can retrieve complete lists of registered users, including:
 *    - Email addresses (can be used for phishing or spam)
 *    - User IDs (can be used to probe other endpoints)
 *    - Names (can be used for social engineering)
 *    This information significantly aids targeted attacks.
 *
 * 2. Information disclosure:
 *    Reveals internal system structure and data organization:
 *    - Database schema details (field names, data types)
 *    - UUID format and generation patterns
 *    - Naming conventions and patterns
 *    - System architecture insights
 *    This information helps attackers craft more effective attacks.
 *
 * 3. No authentication or authorization:
 *    These endpoints are completely open, accessible to anyone with network access.
 *    No JWT tokens, API keys, or other credentials are required.
 *
 * 4. Privacy violations:
 *    Exposes personal data (names, emails) without user consent or legitimate purpose.
 *    Violates GDPR, CCPA, and other privacy regulations.
 *    Could result in significant legal penalties and reputational damage.
 *
 * 5. Performance impact:
 *    Endpoints like listUsers() return ALL users without pagination.
 *    Can be abused for denial-of-service by repeatedly requesting large datasets.
 *
 * Proper approaches for different environments:
 *
 * Development environment:
 * - Keep these endpoints enabled for rapid testing and debugging
 * - Use only on local machines or secure development networks
 * - Never expose development servers to public internet
 *
 * Testing/Staging environment:
 * - Protect with HTTP Basic Auth or IP whitelisting
 * - Use separate database with test data only
 * - Implement rate limiting to prevent abuse
 * - Log all access to debug endpoints for audit trails
 *
 * Production environment (CHOOSE ONE):
 *
 * Option 1: Complete removal (RECOMMENDED)
 * - Delete this entire file before production deployment
 * - Remove from version control for production branches
 * - Ensure build process excludes debug controllers
 *
 * Option 2: Profile-based disabling
 * Add to class: @Profile({"dev", "test"})
 * This prevents Spring from loading the controller in production profile.
 *
 * Option 3: Security protection (if must keep)
 * - Implement strict IP whitelisting (only internal IPs)
 * - Require authentication with admin-level permissions
 * - Add rate limiting (e.g., 10 requests per hour)
 * - Log all access with full audit trail
 * - Encrypt responses containing sensitive data
 *
 * Usage during development:
 *
 * These endpoints are useful for:
 * - Verifying user registration works correctly
 * - Checking if user data is stored as expected
 * - Debugging authentication issues
 * - Testing database connectivity
 * - Populating test data
 * - Verifying data migrations
 *
 * Alternative approaches for production:
 *
 * Instead of debug endpoints, use:
 * - Proper logging with structured log analysis tools
 * - Application monitoring and metrics (Prometheus, Grafana)
 * - Admin dashboards with proper authentication
 * - Database query tools on secure admin networks
 * - Health check endpoints that don't expose sensitive data
 *
 * Implementation notes:
 *
 * 1. Reactive programming:
 *    All methods return Flux or Mono types for non-blocking operations.
 *    This is consistent with the WebFlux architecture used throughout the application.
 *
 * 2. Direct repository access:
 *    Unlike production endpoints that go through service layers with business logic,
 *    these endpoints directly query the UserRepository. This is acceptable for
 *    debugging as it provides raw, unfiltered data views.
 *
 * 3. Data masking:
 *    Even in debug mode, sensitive fields like passwords are NOT exposed.
 *    The hasPassword field only indicates presence, not the actual value.
 *
 * 4. Response format:
 *    Returns simple Map structures rather than full entity objects.
 *    This provides control over exactly what data is exposed and in what format.
 *
 * Testing strategy:
 * While this is a debug controller, it should still have basic tests:
 * - Verify endpoints return expected data structure
 * - Test with empty database returns empty results
 * - Test with multiple users returns all users
 * - Verify password values are never exposed
 * - Test that endpoints are disabled in production profile
 *
 * Code review checklist before production:
 * [ ] Verify this controller is disabled or removed
 * [ ] Check no similar debug endpoints exist in other controllers
 * [ ] Confirm no sensitive data is logged
 * [ ] Verify authentication is required on all production endpoints
 * [ ] Review that error messages don't leak internal details
 * [ ] Ensure no development-only features are accessible
 *
 * @author Thomas Djotio Ndié
 * @since 02.01.2026
 * @version 0.1
 */
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    /**
     * Response map key constants for consistent JSON structure.
     *
     * These constants define the keys used in JSON responses returned by debug endpoints.
     * Using constants provides compile-time checking and makes it easier to maintain
     * consistent response structures across all debug endpoints.
     *
     * Key naming follows camelCase convention consistent with JavaScript/JSON conventions,
     * making the API responses more natural for frontend developers to consume.
     */
    private static final String EMAIL_KEY = "email";
    private static final String FIRST_NAME_KEY = "firstName";
    private static final String LAST_NAME_KEY = "lastName";
    private static final String USER_ID_KEY = "userId";
    private static final String FOUND_KEY = "found";
    private static final String HAS_PASSWORD_KEY = "hasPassword";
    private static final String MESSAGE_KEY = "message";

    /**
     * Predefined message constants for common response scenarios.
     *
     * These constants ensure consistent messaging across the debug endpoints and make
     * it easier to update messages in a centralized location if needed.
     */
    private static final String USER_NOT_FOUND_MESSAGE = "User not found";

    /**
     * Test data constants used for specific test scenarios.
     *
     * TEST_USER_EMAIL defines the email address checked by the testUser() endpoint.
     * This allows testing of specific known users in the system.
     */
    private static final String TEST_USER_EMAIL = "test@example.com";

    /**
     * Injected UserRepository for direct database access.
     *
     * This repository provides reactive database operations for the User entity.
     * Unlike production controllers that go through service layers, debug endpoints
     * access the repository directly for simpler, unfiltered data access.
     *
     * The repository is injected via constructor injection (enabled by @RequiredArgsConstructor)
     * and marked as final to ensure immutability and thread safety.
     *
     * Available operations used in this controller:
     * - findAll(): Retrieves all user records from database
     * - findByEmail(): Retrieves a specific user by email address
     *
     * All repository methods return reactive types (Mono or Flux) for non-blocking I/O.
     */
    private final UserRepository userRepository;

    /**
     * Lists all users in the system with basic information.
     *
     * This endpoint provides a quick way to view all registered users during development
     * and testing. It returns essential user information in a simplified format, making
     * it easy to verify user registration, inspect data correctness, and debug
     * authentication issues without requiring database client tools.
     *
     * Endpoint details:
     * - HTTP Method: GET (idempotent, read-only operation)
     * - URL: /api/debug/users
     * - Authentication: None (completely open access)
     * - Response: JSON array of user objects with email, firstName, lastName
     *
     * Data flow:
     *
     * 1. Repository query:
     *    userRepository.findAll() executes a SELECT * FROM users query.
     *    Returns a Flux<User> representing the stream of all user records.
     *    The query is non-blocking and executes on the R2DBC connection pool.
     *
     * 2. Entity to DTO mapping:
     *    The .map() operator transforms each User entity into a simplified Map structure.
     *    This transformation:
     *    - Extracts only the fields we want to expose
     *    - Excludes sensitive data like passwords
     *    - Creates a clean, predictable JSON structure
     *    - Protects against accidental exposure of internal fields
     *
     * 3. Reactive streaming:
     *    Results are streamed as they arrive from the database rather than buffering
     *    all users in memory. This is more efficient for large datasets.
     *    Each user is converted to JSON and sent to the client as it's processed.
     *
     * Response structure:
     * The endpoint returns a JSON array where each element contains:
     * - email: User's email address (used for login)
     * - firstName: User's given name
     * - lastName: User's family name
     *
     * Example response:
     * [
     *   {
     *     "email": "john.doe@example.com",
     *     "firstName": "John",
     *     "lastName": "Doe"
     *   },
     *   {
     *     "email": "jane.smith@example.com",
     *     "firstName": "Jane",
     *     "lastName": "Smith"
     *   }
     * ]
     *
     * Empty database response:
     * If no users exist, returns an empty array: []
     * This is handled naturally by the reactive stream without special logic.
     *
     * Use cases during development:
     *
     * 1. Verify registration:
     *    After registering new users via /api/auth/register, call this endpoint
     *    to confirm users were created successfully and data was stored correctly.
     *
     * 2. Check data integrity:
     *    Verify that names and emails are stored without corruption, encoding issues,
     *    or unexpected transformations (e.g., unwanted trimming or case changes).
     *
     * 3. Test database connectivity:
     *    Quick way to verify the application can connect to and query the database.
     *    If this endpoint fails, indicates database connection or configuration issues.
     *
     * 4. Debug authentication issues:
     *    If login fails, check this endpoint to verify the user account exists and
     *    the email is exactly as expected (no typos, extra spaces, case differences).
     *
     * 5. Populate test data:
     *    After running database migration or seed scripts, verify test users
     *    were created correctly.
     *
     * Performance considerations:
     *
     * 1. No pagination:
     *    This endpoint returns ALL users without any limit. For databases with
     *    thousands or millions of users, this could:
     *    - Consume significant memory and bandwidth
     *    - Take a long time to return
     *    - Impact database performance
     *    In production, pagination would be mandatory (e.g., ?page=1&size=20)
     *
     * 2. Reactive streaming mitigates some issues:
     *    While all users are queried, reactive streaming means they're processed
     *    one at a time rather than loading everything into memory at once.
     *    However, the database still must scan the entire table.
     *
     * 3. Database load:
     *    Full table scans are expensive. If this endpoint is called frequently,
     *    it can impact overall application performance.
     *
     * Security implications:
     *
     * 1. User enumeration:
     *    Exposes all registered email addresses, which can be used for:
     *    - Spam and phishing campaigns
     *    - Credential stuffing attacks (trying known emails with common passwords)
     *    - Social engineering attacks
     *    - Competitive intelligence (identifying customer base)
     *
     * 2. Privacy violations:
     *    Exposes personal information (names, emails) without user consent.
     *    Violates privacy principles and potentially regulations like GDPR.
     *
     * 3. Information disclosure:
     *    Reveals system scale (number of users) and naming patterns that could
     *    aid attackers in understanding system architecture.
     *
     * Production alternative:
     * For production user management, implement a proper admin endpoint:
     * - Require authentication (JWT token with admin role)
     * - Implement pagination (limit results per page)
     * - Add filtering and search capabilities
     * - Include proper authorization checks
     * - Log all access for audit purposes
     * - Rate limit to prevent abuse
     *
     * Example production endpoint:
     * GET /api/admin/users?page=0&size=20&sort=email,asc&search=john
     * Requires: Authorization: Bearer <admin-jwt-token>
     *
     * @return a Flux that emits Maps containing user information (email, firstName, lastName)
     *         for each user in the database, streamed as results become available from the
     *         database query, automatically serialized to JSON array by Spring
     */
    @GetMapping("/users")
    public Flux<Map<String, String>> listUsers() {
        return userRepository.findAll()
                .map(user -> {
                    Map<String, String> userMap = new HashMap<>();
                    userMap.put(EMAIL_KEY, user.getEmail());
                    userMap.put(FIRST_NAME_KEY, user.getFirstName());
                    userMap.put(LAST_NAME_KEY, user.getLastName());
                    return userMap;
                });
    }

    /**
     * Lists all users with their system-generated unique identifiers.
     *
     * This endpoint extends the basic listUsers() functionality by including the
     * internal user ID (UUID) in the response. User IDs are often needed during
     * development for testing API endpoints that require user identification,
     * debugging relationships between entities, or verifying ID generation.
     *
     * Endpoint details:
     * - HTTP Method: GET
     * - URL: /api/debug/users-with-id
     * - Authentication: None
     * - Response: JSON array of user objects including userId, email, firstName, lastName
     *
     * Key differences from listUsers():
     *
     * 1. Includes userId field:
     *    The system-generated UUID that uniquely identifies each user in the database.
     *    This is the primary key used for all user-related operations and relationships.
     *
     * 2. More complete data view:
     *    Provides all essential user identifiers, making it easier to construct test
     *    requests for other endpoints that require user IDs.
     *
     * Response structure:
     * Each user object includes four fields:
     *
     * - userId: The UUID string (36 characters with hyphens)
     *   Format: "550e8400-e29b-41d4-a716-446655440000"
     *   Purpose: Unique identifier used in URLs and relationships
     *
     * - email: The user's email address
     *   Purpose: Human-readable identifier and login credential
     *
     * - firstName: The user's given name
     *   Purpose: Personalization and display
     *
     * - lastName: The user's family name
     *   Purpose: Identification and display
     *
     * Example response:
     * [
     *   {
     *     "userId": "550e8400-e29b-41d4-a716-446655440000",
     *     "email": "john.doe@example.com",
     *     "firstName": "John",
     *     "lastName": "Doe"
     *   },
     *   {
     *     "userId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
     *     "email": "jane.smith@example.com",
     *     "firstName": "Jane",
     *     "lastName": "Smith"
     *   }
     * ]
     *
     * Use cases during development:
     *
     * 1. Testing user-specific endpoints:
     *    Many API endpoints require a user ID in the URL path or request body.
     *    This endpoint provides the IDs needed for constructing test requests.
     *
     *    Example: Testing document upload
     *    GET /api/debug/users-with-id → get userId
     *    POST /api/documents/upload with userId in body
     *
     * 2. Debugging relationship issues:
     *    When debugging issues with related entities (documents, orders, etc.),
     *    seeing the user IDs helps verify that foreign key relationships are correct.
     *
     * 3. Manual database operations:
     *    When writing SQL queries for testing or data fixes, having the UUIDs
     *    readily available saves time looking them up in database client tools.
     *
     * 4. Postman/API testing:
     *    Store returned user IDs in Postman environment variables for use in
     *    subsequent test requests across different endpoints.
     *
     * 5. Verifying UUID generation:
     *    Confirm that UUIDs are being generated correctly and are truly unique.
     *    Check for any patterns that might indicate weak random number generation.
     *
     * UUID format and properties:
     *
     * The userId field contains a UUID (Universally Unique Identifier) with properties:
     * - 128-bit value (16 bytes)
     * - Represented as 36-character string with hyphens
     * - Format: 8-4-4-4-12 hexadecimal digits
     * - Virtually guaranteed to be unique (2^128 possible values)
     * - Generated without coordination between systems
     * - Non-sequential (doesn't reveal creation order or count)
     *
     * Security benefits of UUIDs:
     * - Non-sequential IDs prevent enumeration attacks
     * - Can't guess other user IDs by incrementing/decrementing
     * - Difficult to determine system scale from ID values
     * - Can be generated offline without database access
     *
     * Implementation detail:
     * The .toString() call on userId converts the UUID object to its standard
     * string representation. This is necessary because Map values must be strings
     * for consistent JSON serialization across all fields.
     *
     * Performance note:
     * Same considerations as listUsers() apply:
     * - No pagination (returns all users)
     * - Full table scan on database
     * - Should not be used with large user bases
     * - Reactive streaming provides some efficiency
     *
     * Security implications:
     *
     * Exposing user IDs adds another dimension to the security risks:
     *
     * 1. Internal ID exposure:
     *    Reveals the internal identifier structure used by the application.
     *    While UUIDs are better than sequential IDs, exposure still aids attackers
     *    in understanding the system.
     *
     * 2. Targeted attacks:
     *    With both email and ID, attackers can craft very specific attacks:
     *    - Test authorization bypasses on specific user IDs
     *    - Attempt to access or modify specific user data
     *    - Create social engineering attacks with exact user information
     *
     * 3. Relationship discovery:
     *    If attackers gain access to other endpoints, knowing user IDs helps them
     *    discover and exploit relationships between users and other entities.
     *
     * Production alternative:
     * In production admin interfaces:
     * - Paginate results (limit exposure per request)
     * - Require authentication with admin role
     * - Mask or truncate IDs in list views (show full ID only in detail view)
     * - Log all access to user ID listings
     * - Implement field-level access control
     * - Consider using separate "public ID" vs "internal ID"
     *
     * @return a Flux that emits Maps containing comprehensive user information including
     *         the system-generated UUID, streamed from the database and automatically
     *         serialized to a JSON array by Spring's message converters
     */
    @GetMapping("/users-with-id")
    public Flux<Map<String, String>> listUsersWithId() {
        return userRepository.findAll()
                .map(user -> {
                    Map<String, String> userMap = new HashMap<>();
                    userMap.put(USER_ID_KEY, user.getUserId().toString());
                    userMap.put(EMAIL_KEY, user.getEmail());
                    userMap.put(FIRST_NAME_KEY, user.getFirstName());
                    userMap.put(LAST_NAME_KEY, user.getLastName());
                    return userMap;
                });
    }

    /**
     * Tests if a specific test user exists in the database and verifies password storage.
     *
     * This endpoint serves a very specific debugging purpose: verifying that a known
     * test user account exists in the system and that its password field is properly
     * populated. This is particularly useful after database migrations, seed scripts,
     * or when debugging authentication issues with specific test accounts.
     *
     * Endpoint details:
     * - HTTP Method: GET
     * - URL: /api/debug/test-user
     * - Authentication: None
     * - Target: Specifically looks for user with email "test@example.com"
     * - Response: JSON object indicating if user was found and password status
     *
     * Purpose and use cases:
     *
     * 1. Post-migration verification:
     *    After running database migrations or schema changes, verify that test user
     *    accounts are still intact and accessible. Confirms that migration scripts
     *    didn't accidentally delete or corrupt test data.
     *
     * 2. Seed script validation:
     *    After running seed scripts that create default or test users, confirm that
     *    the test@example.com account was created successfully with a hashed password.
     *
     * 3. Password hashing verification:
     *    Ensures that passwords are being hashed before storage rather than stored
     *    in plaintext. The hasPassword field confirms the password field is not null,
     *    indicating BCrypt hashing occurred during registration.
     *
     * 4. Authentication debugging:
     *    If login fails for test@example.com, this endpoint quickly determines whether
     *    the issue is:
     *    - User doesn't exist in database (found: false)
     *    - User exists but password is missing (hasPassword: false)
     *    - User and password exist (issue is elsewhere, like wrong password or token generation)
     *
     * 5. Integration test support:
     *    Automated integration tests can call this endpoint to verify test fixture
     *    setup before running authentication test suites.
     *
     * Response scenarios:
     *
     * Scenario 1: Test user exists with password (normal case)
     * {
     *   "found": true,
     *   "email": "test@example.com",
     *   "hasPassword": true
     * }
     *
     * Interpretation: Test user account is properly configured and ready for use.
     * Can proceed with authentication tests using this account.
     *
     * Scenario 2: Test user exists without password (data corruption)
     * {
     *   "found": true,
     *   "email": "test@example.com",
     *   "hasPassword": false
     * }
     *
     * Interpretation: User record exists but password field is null. This indicates:
     * - Registration process failed partway through
     * - Database migration removed password data
     * - Manual database modification corrupted the record
     * - Bug in registration logic that skips password hashing
     *
     * Action required: Recreate the test user or fix the password field.
     *
     * Scenario 3: Test user doesn't exist
     * {
     *   "found": false,
     *   "message": "User not found"
     * }
     *
     * Interpretation: No user with email test@example.com exists in database.
     *
     * Action required: Create the test user via registration endpoint or seed script.
     *
     * Technical implementation details:
     *
     * 1. Email lookup:
     *    userRepository.findByEmail(TEST_USER_EMAIL) queries the database:
     *    SELECT * FROM users WHERE email = 'test@example.com'
     *
     *    This assumes:
     *    - Email column has an index for efficient lookup
     *    - Email comparison is case-sensitive (depends on database collation)
     *    - Returns at most one user (email should have UNIQUE constraint)
     *
     * 2. Reactive mapping:
     *    The .map() operator processes the found user:
     *    - Executed only if user exists
     *    - Transforms User entity into response Map
     *    - Extracts email and checks password field presence
     *
     * 3. Default handling:
     *    The .defaultIfEmpty() operator handles the not-found case:
     *    - Triggered if findByEmail() returns empty Mono
     *    - Provides fallback response without throwing exception
     *    - Returns clean "not found" message
     *
     * 4. Password checking:
     *    user.getPassword() != null checks if password field has a value.
     *
     *    Important: This does NOT verify the password is correct or properly hashed.
     *    It only confirms the field is not null. A malicious or broken system could
     *    store plaintext passwords that would still pass this check.
     *
     *    For true security verification, you would need to:
     *    - Check password starts with BCrypt identifier ($2a$ or $2b$)
     *    - Verify password length is exactly 60 characters
     *    - Validate the hash structure matches BCrypt format
     *
     * Test user conventions:
     *
     * Using test@example.com as the test account email follows best practices:
     * - example.com is reserved for documentation/testing (RFC 2606)
     * - "test" prefix clearly indicates this is not a real user
     * - Easy to remember and identify in logs and debugging
     *
     * In real projects, consider having multiple test users:
     * - test-admin@example.com (admin role)
     * - test-user@example.com (regular user)
     * - test-premium@example.com (premium/paid user)
     * - test-disabled@example.com (disabled/banned user)
     *
     * Security considerations:
     *
     * 1. Exposes test account existence:
     *    Confirms that test@example.com is a valid account in the system.
     *    Attackers could target this known account for brute force attacks.
     *
     * 2. Information leakage:
     *    Reveals whether specific email addresses have accounts, enabling
     *    user enumeration attacks even for non-test accounts if the email
     *    constant is changed or if attackers probe with different emails.
     *
     * 3. Password field verification:
     *    While we don't expose the actual password, revealing that a password
     *    exists (or doesn't) provides information about account state.
     *
     * Production considerations:
     *
     * 1. Remove test accounts:
     *    Production databases should never contain test@example.com or similar
     *    obvious test accounts. Clean them up before deployment.
     *
     * 2. Disable this endpoint:
     *    This entire debug controller should be disabled in production.
     *
     * 3. Health checks instead:
     *    For production monitoring, implement proper health check endpoints:
     *    - Database connectivity check (without exposing data)
     *    - Application readiness check
     *    - Liveness probe for container orchestration
     *
     * Example health check (production alternative):
     * GET /actuator/health
     * {
     *   "status": "UP",
     *   "components": {
     *     "db": {"status": "UP"},
     *     "diskSpace": {"status": "UP"}
     *   }
     * }
     *
     * Testing this endpoint:
     *
     * Test cases:
     * 1. With test@example.com existing → returns found: true, hasPassword: true
     * 2. Without test@example.com existing → returns found: false
     * 3. With test user but null password → returns found: true, hasPassword: false
     * 4. Response structure matches expected format
     * 5. Email in response matches query email
     *
     * Manual testing steps:
     * 1. Create test user: POST /api/auth/register with test@example.com
     * 2. Verify creation: GET /api/debug/test-user → should show found: true
     * 3. Verify password: Check hasPassword: true confirms BCrypt hashing worked
     * 4. Test login: POST /api/auth/login with test@example.com credentials
     * 5. Delete test user: DELETE from users where email = 'test@example.com'
     * 6. Verify deletion: GET /api/debug/test-user → should show found: false
     *
     * @return a Mono that emits a Map containing test results: whether the test user
     *         was found, their email if found, and whether they have a password stored,
     *         or a not-found message if the test user doesn't exist in the database
     */
    @GetMapping("/test-user")
    public Mono<Map<String, Object>> testUser() {
        return userRepository.findByEmail(TEST_USER_EMAIL)
                .map(user -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put(FOUND_KEY, true);
                    result.put(EMAIL_KEY, user.getEmail());
                    result.put(HAS_PASSWORD_KEY, user.getPassword() != null);
                    return result;
                })
                .defaultIfEmpty(Map.of(
                        FOUND_KEY, false,
                        MESSAGE_KEY, USER_NOT_FOUND_MESSAGE
                ));
    }
}