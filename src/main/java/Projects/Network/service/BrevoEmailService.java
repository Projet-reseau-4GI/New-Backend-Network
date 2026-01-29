package Projects.Network.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * BrevoEmailService - Implementation of EmailService using Brevo API v3.
 * Using API is preferred over SMTP in cloud environments (like Render)
 * to avoid port blocking and connection timeouts.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BrevoEmailService implements EmailService {

    private final WebClient.Builder webClientBuilder;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    @Value("${app.name:VerifID}")
    private String appName;

    @Value("${app.support.email:support@network.com}")
    private String supportEmail;

    @Override
    public Mono<Void> sendPasswordResetCode(String to, String code) {
        WebClient webClient = webClientBuilder.baseUrl("https://api.brevo.com/v3").build();

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", "Réinitialisation de mot de passe - " + appName,
                "htmlContent", buildEmailContent(code));

        log.info("📧 Tentative d'envoi d'e-mail via Brevo API à: {}", to);

        return webClient.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            log.error("❌ Brevo API Error Detail: {}", errorBody);
                            return Mono.error(new RuntimeException("Brevo API error: " + errorBody));
                        }))
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ Email envoyé avec succès à {} via Brevo API", to))
                .doOnError(e -> log.error("❌ Erreur lors de l'envoi via Brevo API: {}", e.getMessage()));
    }

    private String buildEmailContent(String code) {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #333; }
                        .container { background-color: #ffffff; border: 1px solid #eee; border-radius: 12px; padding: 40px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); }
                        .header { text-align: center; margin-bottom: 30px; }
                        .logo { font-size: 28px; font-weight: bold; color: #2ecc71; margin: 0; }
                        .code-box { background-color: #f8f9fa; border: 2px solid #2ecc71; border-radius: 8px;
                                    padding: 25px; text-align: center; margin: 30px 0; }
                        .code { font-size: 36px; font-weight: bold; color: #2c3e50; letter-spacing: 8px; }
                        .footer { text-align: center; margin-top: 40px; font-size: 13px; color: #95a5a6; border-top: 1px solid #eee; padding-top: 20px; }
                        .btn { display: inline-block; padding: 12px 24px; background-color: #2ecc71; color: white; text-decoration: none; border-radius: 6px; font-weight: bold; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <p class="logo">%s</p>
                            <h2 style="color: #2c3e50;">Réinitialisation de mot de passe</h2>
                        </div>

                        <p>Bonjour,</p>
                        <p>Vous avez demandé un code pour réinitialiser le mot de passe de votre compte sur <strong>%s</strong>.</p>
                        <p>Veuillez utiliser le code de sécurité suivant :</p>

                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>

                        <p style="font-size: 14px; color: #7f8c8d;">Ce code est valable pendant 15 minutes. Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet e-mail en toute sécurité.</p>

                        <div class="footer">
                            <p>Besoin d'aide ? Contactez-nous à <a href="mailto:%s" style="color: #2ecc71;">%s</a></p>
                            <p>&copy; 2026 %s. Tous droits réservés.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(appName, appName, code, supportEmail, supportEmail, appName);
    }
}
