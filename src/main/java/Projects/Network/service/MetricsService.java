package Projects.Network.service;

import Projects.Network.dto.UsageStatisticDto;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final DatabaseClient databaseClient;

    public Flux<UsageStatisticDto> getOverallUsageStatistics() {
        String sql = "SELECT platform_id, doc_type, COUNT(*) as total_logs, " +
                "CAST(SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS DOUBLE PRECISION) as success_rate, " +
                "CAST(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS DOUBLE PRECISION) as failure_rate, " +
                "CAST(AVG(confidence) AS DOUBLE PRECISION) as avg_confidence, " +
                "STRING_AGG(DISTINCT reason, ', ') as rejection_reasons " +
                "FROM verification_logs GROUP BY platform_id, doc_type";
        
        return databaseClient.sql(sql)
                .map((row, metadata) -> UsageStatisticDto.builder()
                        .platformId(row.get("platform_id", Long.class))
                        .docType(row.get("doc_type", String.class))
                        .totalLogs(row.get("total_logs", Long.class))
                        .successRate(row.get("success_rate", Double.class))
                        .failureRate(row.get("failure_rate", Double.class))
                        .avgConfidence(row.get("avg_confidence", Double.class))
                        .rejectionReasons(row.get("rejection_reasons", String.class))
                        .build())
                .all();
    }

    public Flux<UsageStatisticDto> getUsageStatisticsByPlatform(Long platformId) {
        String sql = "SELECT platform_id, doc_type, COUNT(*) as total_logs, " +
                "CAST(SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS DOUBLE PRECISION) as success_rate, " +
                "CAST(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS DOUBLE PRECISION) as failure_rate, " +
                "CAST(AVG(confidence) AS DOUBLE PRECISION) as avg_confidence, " +
                "STRING_AGG(DISTINCT reason, ', ') as rejection_reasons " +
                "FROM verification_logs WHERE platform_id = :platformId GROUP BY platform_id, doc_type";
        
        return databaseClient.sql(sql)
                .bind("platformId", platformId)
                .map((row, metadata) -> UsageStatisticDto.builder()
                        .platformId(row.get("platform_id", Long.class))
                        .docType(row.get("doc_type", String.class))
                        .totalLogs(row.get("total_logs", Long.class))
                        .successRate(row.get("success_rate", Double.class))
                        .failureRate(row.get("failure_rate", Double.class))
                        .avgConfidence(row.get("avg_confidence", Double.class))
                        .rejectionReasons(row.get("rejection_reasons", String.class))
                        .build())
                .all();
    }
}

