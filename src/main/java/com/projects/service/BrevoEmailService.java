package com.projects.service;

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

    // ─────────────────────────────────────────────────────────────────────────
    // COMMON helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Void> sendEmail(String to, String subject, String htmlContent) {
        Map<String, Object> body = Map.of(
            "sender",      Map.of("name", senderName, "email", senderEmail),
            "to",          List.of(Map.of("email", to)),
            "subject",     subject,
            "htmlContent", htmlContent
        );
        return webClientBuilder.build()
            .post()
            .uri("https://api.brevo.com/v3/smtp/email")
            .header("api-key", apiKey)
            .bodyValue(body)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(v -> log.info("Email '{}' sent to {}", subject, to))
            .doOnError(e -> log.error("Failed to send email to {}: {}", to, e.getMessage()))
            .then();
    }

    private String wrapHtml(String title, String bodyContent) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8"/>
              <style>
                body{font-family:'Segoe UI',Tahoma,Geneva,Verdana,sans-serif;background:#f5f6fa;margin:0;padding:0;}
                .wrapper{max-width:600px;margin:40px auto;background:#fff;border-radius:10px;
                         box-shadow:0 2px 12px rgba(0,0,0,.08);overflow:hidden;}
                .header{background:linear-gradient(135deg,#1a237e,#283593);padding:32px 40px;text-align:center;}
                .header h1{color:#fff;margin:0;font-size:22px;letter-spacing:1px;}
                .header p{color:#90caf9;margin:6px 0 0;font-size:13px;}
                .body{padding:36px 40px;color:#333;}
                .body p{line-height:1.7;margin:0 0 14px;}
                .code-box{background:#f0f4ff;border:2px dashed #3949ab;border-radius:8px;
                           text-align:center;padding:20px;margin:24px 0;}
                .code{font-size:36px;font-weight:700;letter-spacing:8px;color:#1a237e;}
                .btn{display:inline-block;background:#3949ab;color:#fff;text-decoration:none;
                     padding:12px 32px;border-radius:6px;font-size:15px;margin:10px 0;}
                .info-box{background:#e8f5e9;border-left:4px solid #43a047;padding:14px 18px;
                           border-radius:4px;margin:18px 0;font-size:14px;color:#2e7d32;}
                .warning-box{background:#fff8e1;border-left:4px solid #f9a825;padding:14px 18px;
                              border-radius:4px;margin:18px 0;font-size:14px;color:#f57f17;}
                .footer{background:#f5f6fa;padding:20px 40px;text-align:center;
                         font-size:12px;color:#9e9e9e;border-top:1px solid #e0e0e0;}
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1>VerifID</h1>
                  <p>Plateforme de vérification de documents</p>
                </div>
                <div class="body">
                  <h2 style="color:#1a237e;margin:0 0 18px;">""" + title + """
                  </h2>
                """ + bodyContent + """
                </div>
                <div class="footer">
                  Ce message est envoyé automatiquement, merci de ne pas y répondre.<br/>
                  &copy; 2025 VerifID &mdash; Tous droits réservés.
                </div>
              </div>
            </body>
            </html>
            """;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. OTP Email (email verification / API key regeneration request)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> sendOtp(String to, String code, String platformName) {
        log.info("Sending OTP to {} for platform {}", to, platformName);
        String body = """
            <p>Bonjour,</p>
            <p>Vous avez demandé un code de vérification pour votre compte <strong>%s</strong> sur VerifID.</p>
            <p>Veuillez utiliser le code ci-dessous :</p>
            <div class="code-box"><div class="code">%s</div></div>
            <div class="warning-box">⏱ Ce code est valable <strong>15 minutes</strong>. Ne le partagez avec personne.</div>
            <p>Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.</p>
            """.formatted(platformName, code);
        return sendEmail(to,
            "Code de vérification VerifID — " + platformName,
            wrapHtml("Code de vérification", body));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Password Reset Email
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> sendPasswordReset(String to, String code, String platformName) {
        log.info("Sending password-reset code to {}", to);
        String body = """
            <p>Bonjour,</p>
            <p>Une demande de réinitialisation de mot de passe a été effectuée pour votre compte
               <strong>%s</strong> sur VerifID.</p>
            <p>Utilisez le code ci-dessous pour définir un nouveau mot de passe :</p>
            <div class="code-box"><div class="code">%s</div></div>
            <div class="warning-box">⏱ Ce code expire dans <strong>15 minutes</strong> et ne peut être
               utilisé qu'une seule fois.</div>
            <p>Si vous n'avez pas fait cette demande, votre compte est peut-être en danger. Changez votre
               mot de passe immédiatement.</p>
            """.formatted(platformName, code);
        return sendEmail(to,
            "Réinitialisation de mot de passe — VerifID",
            wrapHtml("Réinitialisation de mot de passe", body));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Password Changed Notification
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> sendPasswordChangedNotification(String to, String platformName) {
        log.info("Sending password-changed notification to {}", to);
        String body = """
            <p>Bonjour,</p>
            <p>Le mot de passe de votre compte <strong>%s</strong> sur VerifID a bien été modifié.</p>
            <div class="info-box">✅ Si vous êtes à l'origine de cette modification, aucune action n'est requise.</div>
            <div class="warning-box">⚠️ Si vous n'avez <strong>pas</strong> effectué cette modification,
               contactez immédiatement notre support : <a href="mailto:support@network.com">support@network.com</a></div>
            """.formatted(platformName);
        return sendEmail(to,
            "Votre mot de passe VerifID a été modifié",
            wrapHtml("Modification de mot de passe", body));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. API Key Regenerated Notification
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> sendApiKeyRegeneratedNotification(String to, String platformName) {
        log.info("Sending API-key-regenerated notification to {}", to);
        String body = """
            <p>Bonjour,</p>
            <p>Votre clé d'API VerifID pour le compte <strong>%s</strong> a été régénérée avec succès.</p>
            <div class="info-box">
              🔑 Votre nouvelle clé d'API est disponible dans votre portail VerifID.<br/>
              Connectez-vous pour la consulter et mettre à jour vos intégrations.
            </div>
            <div class="warning-box">
              ⚠️ L'ancienne clé est désormais <strong>invalidée</strong>. Pensez à mettre à jour
              toutes vos applications qui l'utilisent.
            </div>
            <p>Si vous n'êtes pas à l'origine de cette action, contactez notre support immédiatement.</p>
            """.formatted(platformName);
        return sendEmail(to,
            "Votre clé API VerifID a été régénérée",
            wrapHtml("Régénération de clé API", body));
    }
}
