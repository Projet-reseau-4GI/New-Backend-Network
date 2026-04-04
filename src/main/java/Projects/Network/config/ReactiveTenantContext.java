package Projects.Network.config;

import Projects.Network.model.Platform;
import reactor.core.publisher.Mono;

/**
 * Context helper for the reactive Spring WebFlux environment.
 * Stores and retrieves the current Platform (Tenant) for the request stream.
 */
public class ReactiveTenantContext {

    public static final String TENANT_KEY = "CURRENT_PLATFORM_TENANT";

    /**
     * Put the platform into the reactor context.
     * @param context existing context
     * @param platform platform to store
     * @return new context
     */
    public static reactor.util.context.Context putPlatform(reactor.util.context.Context context, Platform platform) {
        return context.put(TENANT_KEY, platform);
    }

    /**
     * Retrieve the platform from the current execution context.
     * @return Mono of Platform, empty if not found.
     */
    public static Mono<Platform> getPlatform() {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(TENANT_KEY)) {
                return Mono.just(ctx.get(TENANT_KEY));
            } else {
                return Mono.empty();
            }
        });
    }
}
