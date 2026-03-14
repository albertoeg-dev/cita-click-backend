package com.reservas.notifications.twilio;

import com.reservas.exception.NotificationException;
import com.reservas.notifications.domain.Notification;
import com.reservas.notifications.domain.NotificationChannel;
import com.reservas.notifications.domain.NotificationResult;
import com.reservas.notifications.dto.SendNotificationRequest;
import com.reservas.notifications.provider.NotificationProvider;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación de NotificationProvider usando Twilio para WhatsApp.
 *
 * IMPORTANTE:
 * - Los números deben incluir código de país: +5215512345678
 * - El remitente debe ser un número aprobado por Twilio
 * - Para producción necesitas una cuenta aprobada de WhatsApp Business
 * - En desarrollo usa el sandbox de Twilio
 */
@Slf4j
@Service("twilioWhatsAppProvider")
public class TwilioWhatsAppProvider implements NotificationProvider {

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @Value("${twilio.whatsapp.from:}")
    private String whatsappFrom;

    private boolean configured = false;

    @PostConstruct
    public void init() {
        try {
            if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
                Twilio.init(accountSid, authToken);
                configured = true;
                log.info("[Twilio WhatsApp]  Inicializado correctamente - From: {}", whatsappFrom);
            } else {
                log.warn("[Twilio WhatsApp]  No configurado - credenciales faltantes");
            }
        } catch (Exception e) {
            log.error("[Twilio WhatsApp]  Error en inicialización: {}", e.getMessage(), e);
            configured = false;
        }
    }

    @Override
    public NotificationResult send(SendNotificationRequest request) {
        if (!configured) {
            throw new NotificationException(
                    "Twilio WhatsApp no está configurado",
                    "TWILIO_NOT_CONFIGURED"
            );
        }

        try {
            log.info("[Twilio WhatsApp] Enviando mensaje a: {}", request.getRecipient());

            // Validar que el número tenga formato correcto
            String recipient = formatPhoneNumber(request.getRecipient());

            // Crear mensaje - Ambos números deben tener prefijo "whatsapp:"
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + recipient),
                    new PhoneNumber("whatsapp:" + whatsappFrom),
                    request.getContent()
            ).create();

            log.info("[Twilio WhatsApp]  Mensaje enviado - SID: {} - Status: {}",
                    message.getSid(), message.getStatus());

            return NotificationResult.builder()
                    .success(true)
                    .providerId(message.getSid())
                    .recipient(request.getRecipient())
                    .channel(NotificationChannel.WHATSAPP)
                    .message("Mensaje WhatsApp enviado exitosamente")
                    .sentAt(LocalDateTime.now())
                    .build();

        } catch (com.twilio.exception.TwilioException e) {
            log.error("[Twilio WhatsApp]  Error enviando mensaje: {}",
                    e.getMessage(), e);

            return NotificationResult.builder()
                    .success(false)
                    .recipient(request.getRecipient())
                    .channel(NotificationChannel.WHATSAPP)
                    .errorCode("TWILIO_ERROR")
                    .errorMessage(e.getMessage())
                    .build();

        } catch (Exception e) {
            log.error("[Twilio WhatsApp]  Error inesperado: {}", e.getMessage(), e);

            throw new NotificationException(
                    "Error al enviar WhatsApp: " + e.getMessage(),
                    "TWILIO_SEND_ERROR"
            );
        }
    }

    @Override
    public List<NotificationResult> sendBatch(List<SendNotificationRequest> requests) {
        List<NotificationResult> results = new ArrayList<>();

        for (SendNotificationRequest request : requests) {
            try {
                NotificationResult result = send(request);
                results.add(result);
            } catch (Exception e) {
                log.error("[Twilio WhatsApp]  Error en batch para {}: {}",
                        request.getRecipient(), e.getMessage());

                results.add(NotificationResult.builder()
                        .success(false)
                        .recipient(request.getRecipient())
                        .channel(NotificationChannel.WHATSAPP)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        log.info("[Twilio WhatsApp] Batch completado - Total: {} - Exitosos: {}",
                results.size(),
                results.stream().filter(NotificationResult::getSuccess).count());

        return results;
    }

    @Override
    public Notification getStatus(String notificationId) {
        if (!configured) {
            throw new NotificationException(
                    "Twilio WhatsApp no está configurado",
                    "TWILIO_NOT_CONFIGURED"
            );
        }

        try {
            Message message = Message.fetcher(notificationId).fetch();

            Notification.NotificationStatus status = mapTwilioStatus(message.getStatus().toString());

            return Notification.builder()
                    .id(message.getSid())
                    .channel(NotificationChannel.WHATSAPP)
                    .providerId(message.getSid())
                    .recipient(message.getTo())
                    .content(message.getBody())
                    .status(status)
                    .sentAt(message.getDateCreated() != null
                            ? LocalDateTime.ofInstant(message.getDateCreated().toInstant(), ZoneId.systemDefault())
                            : null)
                    .errorMessage(message.getErrorMessage())
                    .build();

        } catch (com.twilio.exception.TwilioException e) {
            log.error("[Twilio WhatsApp]  Error obteniendo estado: {}", e.getMessage(), e);
            throw new NotificationException(
                    "Error al obtener estado de WhatsApp: " + e.getMessage(),
                    "TWILIO_STATUS_ERROR",
                    e.getMessage()
            );
        }
    }

    @Override
    public NotificationChannel getSupportedChannel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Formatea un número de teléfono para WhatsApp.
     * Asegura que tenga el formato correcto con código de país.
     */
    private String formatPhoneNumber(String phoneNumber) {
        // Remover espacios y caracteres especiales
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        // Si no empieza con +, agregar +
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }

        return cleaned;
    }

    /**
     * Mapea estados de Twilio a nuestros estados de dominio.
     */
    private Notification.NotificationStatus mapTwilioStatus(String twilioStatus) {
        return switch (twilioStatus.toLowerCase()) {
            case "queued" -> Notification.NotificationStatus.QUEUED;
            case "sent" -> Notification.NotificationStatus.SENT;
            case "delivered" -> Notification.NotificationStatus.DELIVERED;
            case "read" -> Notification.NotificationStatus.READ;
            case "failed", "undelivered" -> Notification.NotificationStatus.FAILED;
            default -> Notification.NotificationStatus.PENDING;
        };
    }
}
