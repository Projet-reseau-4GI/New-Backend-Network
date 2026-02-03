package Projects.Network.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("email_verification_tokens")
public class EmailVerificationToken {
    @Id
    @Column("token_id")
    private UUID tokenId;

    @Column("token_hash")
    private String tokenHash;

    @Column("user_id")
    private UUID userId;

    @Column("expiry_date")
    private Instant expiryDate;

    @Column("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
