package com.yowyob.flashshop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Implementation of EmailService using Brevo (formerly Sendinblue) API.
 * Handles transactional emails like OTP and password resets.
 *
 * @author Thomas Djotio Ndié
 * @version 0.1
 * @since 2026-05-27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailService implements EmailService {

  private final WebClient.Builder web_client_builder;

  @Value("${brevo.api.key}")
  private String api_key;

  @Value("${brevo.sender.email}")
  private String sender_email;

  @Value("${brevo.sender.name}")
  private String sender_name;

  // ─────────────────────────────────────────────────────────────────────────
  // COMMON helpers
  // ─────────────────────────────────────────────────────────────────────────

  private Mono<Void> sendEmail(String to, String subject, String html_content) {
    Map<String, Object> body = Map.of(
        "sender", Map.of("name", sender_name, "email", sender_email),
        "to", List.of(Map.of("email", to)),
        "subject", subject,
        "htmlContent", html_content);
    return web_client_builder.build()
        .post()
        .uri("https://api.brevo.com/v3/smtp/email")
        .header("api-key", api_key)
        .bodyValue(body)
        .retrieve()
        .toBodilessEntity()
        .doOnSuccess(v -> log.info("Email '{}' sent to {}", subject, to))
        .doOnError(e -> log.error("Failed to send email to {}: {}", to, e.getMessage()))
        .then();
  }

  private String wrapHtml(String title, String body_content) {
    return """
        <!DOCTYPE html>
        <html lang="en">
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
              <p>Identity Verification Platform</p>
            </div>
            <div class="body">
              <h2 style="color:#1a237e;margin:0 0 18px;">""" + title + """
          </h2>
        """ + body_content + """
            </div>
            <div class="footer">
              This is an automated message, please do not reply.<br/>
              &copy; 2025 VerifID &mdash; All rights reserved.
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
  public Mono<Void> sendOtp(String to, String code, String platform_name) {
    log.info("Sending OTP to {} for platform {}", to, platform_name);
    String body = """
        <p>Hello,</p>
        <p>You requested a verification code for your account <strong>%s</strong> on VerifID.</p>
        <p>Please use the code below:</p>
        <div class="code-box"><div class="code">%s</div></div>
        <div class="warning-box">⏱ This code is valid for <strong>15 minutes</strong>. Do not share it with anyone.</div>
        <p>If you did not request this, please ignore this email.</p>
        """
        .formatted(platform_name, code);
    return sendEmail(to,
        "VerifID Verification Code — " + platform_name,
        wrapHtml("Verification Code", body));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 2. Password Reset Email
  // ─────────────────────────────────────────────────────────────────────────

  @Override
  public Mono<Void> sendPasswordReset(String to, String code, String platform_name) {
    log.info("Sending password-reset code to {}", to);
    String body = """
        <p>Hello,</p>
        <p>A password reset request has been made for your account
           <strong>%s</strong> on VerifID.</p>
        <p>Use the code below to set a new password:</p>
        <div class="code-box"><div class="code">%s</div></div>
        <div class="warning-box">⏱ This code expires in <strong>15 minutes</strong> and can only be
           used once.</div>
        <p>If you did not make this request, your account may be at risk. Change your
           password immediately.</p>
        """.formatted(platform_name, code);
    return sendEmail(to,
        "Password Reset — VerifID",
        wrapHtml("Password Reset", body));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Password Changed Notification
  // ─────────────────────────────────────────────────────────────────────────

  @Override
  public Mono<Void> sendPasswordChangedNotification(String to, String platform_name) {
    log.info("Sending password-changed notification to {}", to);
    String body = """
        <p>Hello,</p>
        <p>The password for your account <strong>%s</strong> on VerifID has been successfully changed.</p>
        <div class="info-box">✅ If you initiated this change, no further action is required.</div>
        <div class="warning-box">⚠️ If you did <strong>not</strong> make this change,
           contact our support immediately: <a href="mailto:support@network.com">support@network.com</a></div>
        """.formatted(platform_name);
    return sendEmail(to,
        "Your VerifID password has been modified",
        wrapHtml("Password Modification", body));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 4. API Key Regenerated Notification
  // ─────────────────────────────────────────────────────────────────────────

  @Override
  public Mono<Void> sendApiKeyRegeneratedNotification(String to, String platform_name) {
    log.info("Sending API-key-regenerated notification to {}", to);
    String body = """
        <p>Hello,</p>
        <p>Your VerifID API key for account <strong>%s</strong> has been successfully regenerated.</p>
        <div class="info-box">
          🔑 Your new API key is available in your VerifID portal.<br/>
          Log in to view it and update your integrations.
        </div>
        <div class="warning-box">
          ⚠️ The old key is now <strong>invalidated</strong>. Make sure to update
          all your applications that use it.
        </div>
        <p>If you did not authorize this action, contact our support immediately.</p>
        """.formatted(platform_name);
    return sendEmail(to,
        "Your VerifID API Key has been regenerated",
        wrapHtml("API Key Regeneration", body));
  }
}
