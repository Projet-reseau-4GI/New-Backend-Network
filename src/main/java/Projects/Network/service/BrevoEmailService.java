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
                        body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #333; line-height: 1.6; }
                        .container { background-color: #ffffff; border: 1px solid #e1e4e8; border-radius: 12px; padding: 40px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
                        .header { text-align: center; margin-bottom: 30px; border-bottom: 2px solid #2ecc71; padding-bottom: 20px; }
                        .logo { font-size: 32px; font-weight: 800; color: #2ecc71; margin: 0; letter-spacing: -1px; }
                        .content { padding: 20px 0; }
                        .code-box { background-color: #f8f9fa; border: 1px solid #2ecc71; border-radius: 8px;
                                    padding: 30px; text-align: center; margin: 30px 0; }
                        .code { font-size: 42px; font-weight: bold; color: #2c3e50; letter-spacing: 10px;
                                white-space: nowrap; display: inline-block; font-family: 'Courier New', Courier, monospace; }
                        .footer { text-align: center; margin-top: 40px; font-size: 13px; color: #7f8c8d; border-top: 1px solid #eee; padding-top: 25px; }
                        .highlight { color: #2ecc71; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <p class="logo">%s</p>
                        </div>

                        <div class="content">
                            <h2 style="color: #2c3e50; text-align: center;">Réinitialisation de mot de passe</h2>
                            <p>Bonjour,</p>
                            <p>Vous avez demandé un code de sécurité pour réinitialiser le mot de passe de votre compte sur <span class="highlight">%s</span>.</p>
                            <p>Voici votre code de vérification :</p>

                            <div class="code-box">
                                <div class="code">%s</div>
                            </div>

                            <p style="font-size: 14px; color: #e74c3c; background-color: #fdf2f2; padding: 10px; border-radius: 6px; text-align: center;">
                                <strong>Attention :</strong> Ce code expire dans 15 minutes.
                            </p>

                            <p style="font-size: 14px; color: #95a5a6; margin-top: 20px;">Si vous n'avez pas demandé cette réinitialisation, vous pouvez ignorer cet e-mail en toute sécurité. Votre mot de passe restera inchangé.</p>
                        </div>

                        <div class="footer">
                            <p>Besoin d'assistance ? Contactez-nous à <a href="mailto:%s" style="color: #2ecc71; text-decoration: none;">%s</a></p>
                            <p>&copy; 2026 %s. Tous droits réservés.</p>
                        </div>
                    </div>
                </body>
                </html>
                """

    @Override
    public Mono<Void> sendEmailVerificationCode(String to, String code) {
        WebClient webClient = webClientBuilder.baseUrl("https://api.brevo.com/v3").build();

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", senderName, "email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", "Vérification de votre email - " + appName,
                "htmlContent", buildVerificationEmailContent(code));

        return webClient.post()
                .uri("/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class);
    }

    private String buildVerificationEmailContent(String code) {
        return """
                <!DOCTYPE html>
                <html>
                <head><style>
                    body { font-family: sans-serif; max-width: 600px; margin: 0 auto; color: #333; }
                    .code { font-size: 32px; font-weight: bold; color: #3498db; letter-spacing: 5px; text-align: center; padding: 20px; background: #f8f9fa; border-radius: 8px; }
                </style></head>
                <body>
                    <h2>Vérifiez votre email sur %s</h2>
                    <p>Voici votre code de vérification :</p>
                    <div class="code">%s</div>
                    <p>Ce code expire dans 15 minutes.</p>
                </body>
                </html>
                """
                .formatted(appName, code);
    }
}
