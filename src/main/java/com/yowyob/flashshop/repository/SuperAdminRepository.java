package com.yowyob.flashshop.repository;

import com.yowyob.flashshop.model.SuperAdmin;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface SuperAdminRepository extends ReactiveCrudRepository<SuperAdmin, Long> {
    /**
     * Spring Data R2DBC will automatically look in the "super_admins" table
     * with this method.
     */
    Mono<SuperAdmin> findByEmail(String email);
}
