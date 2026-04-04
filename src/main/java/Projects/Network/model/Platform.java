package Projects.Network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity representing a Tenant/Platform in the system.
 * Used for multi-tenancy and API key authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("platforms")
public class Platform {

    @Id
    private Long id;

    @Column("name")
    private String name;

    @Column("email")
    private String email;

    @Column("api_key")
    private String apiKey;

    @Column("otp_code")
    private String otpCode;

    @Column("otp_expiry")
    private java.time.LocalDateTime otpExpiry;

    @Column("active")
    private Boolean active;

    @Column("created_at")
    private java.time.LocalDateTime createdAt;

    @Column("updated_at")
    private java.time.LocalDateTime updatedAt;

}
