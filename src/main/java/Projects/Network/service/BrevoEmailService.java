package Projects.Network.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailService implements EmailService {

    private final WebClient.Builder webClientBuilder;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    @Override
    public Mono<Void> sendOtp(String to, String code, String platformName) {
        log.info("Sending professional OTP {} to {} for platform {}", code, to, platformName);
        
        String htmlContent = String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "<style>" +
            "  body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #333; line-height: 1.6; }" +
            "  .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px; }" +
            "  .header { border-bottom: 2px solid #0056b3; padding-bottom: 10px; margin-bottom: 20px; }" +
            "  .header h2 { color: #0056b3; margin: 0; }" +
            "  .otp-box { background-color: #f4f7f9; border: 1px dashed #0056b3; padding: 15px; text-align: center; margin: 20px 0; border-radius: 4px; }" +
            "  .otp-code { font-size: 32px; font-weight: bold; letter-spacing: 5px; color: #333; }" +
            "  .footer { font-size: 12px; color: #777; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px; }" +
            "</style>" +
            "</head>" +
            "<body>" +
            "  <div class='container'>" +
            "    <div class='header'>" +
            "      <h2>Vérification d'identité</h2>" +
            "    </div>" +
            "    <p>Bonjour,</p>" +
            "    <p>Vous avez demandé un code de vérification pour accéder à la plateforme <strong>%s</strong>.</p>" +
            "    <p>Veuillez utiliser le code ci-dessous pour finaliser votre connexion :</p>" +
            "    <div class='otp-box'>" +
            "      <div class='otp-code'>%s</div>" +
            "    </div>" +
            "    <p>Ce code est valable pendant 15 minutes. Si vous n'êtes pas à l'origine de cette demande, veuillez ignorer ce message.</p>" +
            "    <div class='footer'>" +
            "      Ceci est un message automatique, merci de ne pas y répondre.<br>" +
            "      Propulsé par VerifID" +
            "    </div>" +
            "  </div>" +
            "</body>" +
            "</html>", platformName, code);

        Map<String, Object> body = Map.of(
            "sender", Map.of("name", senderName, "email", senderEmail),
            "to", List.of(Map.of("email", to)),
            "subject", "Votre code de vérification VerifID - " + platformName,
            "htmlContent", htmlContent
        );

        return webClientBuilder.build()
            .post()
            .uri("https://api.brevo.com/v3/smtp/email")
            .header("api-key", apiKey)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(v -> log.info("Email sent successfully to {}", to))
            .doOnError(e -> log.error("Error sending email to {}: {}", to, e.getMessage()))
            .then();
    }
}
