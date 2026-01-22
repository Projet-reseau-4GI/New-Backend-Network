package Projects.Network.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class ResendEmailService implements EmailService {

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.name:Network Project}")
    private String appName;

    @Value("${app.support.email:support@network.com}")
    private String supportEmail;

    @Override
    public Mono<Void> sendPasswordResetCode(String to, String code) {
        return Mono.fromCallable(() -> {
                    try {
                        Resend resend = new Resend(apiKey);

                        CreateEmailOptions params = CreateEmailOptions.builder()
                                .from(fromEmail)
                                .to(to)
                                .subject("Réinitialisation de mot de passe - " + appName)
                                .html(buildEmailContent(code))
                                .build();

                        CreateEmailResponse data = resend.emails().send(params);
                        log.info("✅ Email envoyé via Resend: {}", data.getId());
                        return null;

                    } catch (ResendException e) {
                        log.error("❌ Erreur Resend: {}", e.getMessage(), e);
                        throw new RuntimeException("Erreur lors de l'envoi: " + e.getMessage(), e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private String buildEmailContent(String code) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; }
                    .container { background-color: #f9f9f9; border-radius: 10px; padding: 30px; }
                    .header { text-align: center; color: #2c3e50; margin-bottom: 30px; }
                    .code-box { background-color: #fff; border: 2px dashed #3498db; border-radius: 8px; 
                                padding: 20px; text-align: center; margin: 25px 0; }
                    .code { font-size: 32px; font-weight: bold; color: #3498db; letter-spacing: 5px; 
                            font-family: 'Courier New', monospace; }
                    .warning { background-color: #fff3cd; border-left: 4px solid #ffc107; 
                               padding: 12px; margin: 20px 0; border-radius: 4px; }
                    .footer { text-align: center; margin-top: 30px; padding-top: 20px; 
                              border-top: 1px solid #ddd; font-size: 12px; color: #777; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔐 Réinitialisation de mot de passe</h1>
                    </div>
                    
                    <p>Bonjour,</p>
                    <p>Vous avez demandé la réinitialisation de votre mot de passe sur <strong>%s</strong>.</p>
                    <p>Voici votre code de vérification :</p>
                    
                    <div class="code-box">
                        <div class="code">%s</div>
                    </div>
                    
                    <div class="warning">
                        <strong>⚠️ Important :</strong>
                        <ul style="margin: 10px 0; padding-left: 20px;">
                            <li>Ce code est valide pendant <strong>15 minutes</strong></li>
                            <li>Ne partagez jamais ce code avec personne</li>
                            <li>Si vous n'avez pas demandé cette réinitialisation, ignorez cet email</li>
                        </ul>
                    </div>
                    
                    <div class="footer">
                        <p>Cet email a été envoyé automatiquement, merci de ne pas y répondre.</p>
                        <p>Pour toute question, contactez-nous à <a href="mailto:%s">%s</a></p>
                        <p>&copy; 2025 %s. Tous droits réservés.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(appName, code, supportEmail, supportEmail, appName);
    }
}