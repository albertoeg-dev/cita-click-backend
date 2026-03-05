package com.reservas.service;

import com.reservas.entity.PlantillaEmailConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para envío de emails usando Resend API.
 * Documentación: https://resend.com/docs/api-reference/emails/send-email
 *
 * Soporta dos modos de envío:
 *  - HTML inline:        enviarEmail() — para emails one-off (confirmación, invitación, etc.)
 *  - Classpath template: enviarConTemplate() — carga archivos HTML de
 *    src/main/resources/email-templates/, reemplaza {{variable}} y envía con campo "html"
 */
@Service
@Slf4j
public class EmailService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${resend.from.email:noreply@reservas.com}")
    private String fromEmail;

    @Value("${resend.from.name:Sistema de Reservas}")
    private String fromName;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplate();
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            log.info("[Resend] Email service inicializado - From: {} <{}>", fromName, fromEmail);
        } else {
            log.warn("[Resend] API key no configurada. Los emails no serán enviados.");
        }
    }

    /**
     * Realiza el POST a la API de Resend.
     * Método protegido para facilitar el testing (stubbing vía subclase).
     */
    @SuppressWarnings("unchecked")
    protected ResponseEntity<Map> doPost(String url, HttpEntity<?> entity) {
        return restTemplate.postForEntity(url, entity, Map.class);
    }

    // ══════════════════════════════════════════════════════════════════
    // CARGA Y RENDERIZADO DE TEMPLATES (classpath)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Carga el contenido HTML de un template desde el classpath.
     * Los archivos deben estar en: src/main/resources/email-templates/{nombre}.html
     */
    private String cargarTemplate(String nombre) {
        try {
            ClassPathResource resource = new ClassPathResource("email-templates/" + nombre + ".html");
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[Resend] No se pudo cargar el template '{}': {}", nombre, e.getMessage());
            return null;
        }
    }

    /**
     * Reemplaza los placeholders {@code {{variable}}} del template HTML con los valores del mapa.
     */
    private String renderTemplate(String html, Map<String, String> variables) {
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            html = html.replace("{{" + entry.getKey() + "}}",
                                entry.getValue() != null ? entry.getValue() : "");
        }
        return html;
    }

    /**
     * Carga el template, reemplaza variables y envía con el campo "html".
     * Este es el método principal para emails con diseño personalizado.
     *
     * @param destinatario   Email del destinatario
     * @param asunto         Asunto del email
     * @param templateNombre Nombre del archivo sin extensión (ej: "verificacion-correo")
     * @param variables      Mapa de variables para reemplazar en el template
     * @return true si se envió correctamente
     */
    private boolean enviarConTemplate(String destinatario, String asunto,
                                      String templateNombre, Map<String, String> variables) {
        String html = cargarTemplate(templateNombre);
        if (html == null) {
            log.error("[Resend] Template '{}' no encontrado. Email no enviado a: {}", templateNombre, destinatario);
            return false;
        }
        html = renderTemplate(html, variables);
        return enviarEmail(destinatario, asunto, html);
    }

    /**
     * Devuelve el nombre del archivo de template según el diseño del negocio.
     */
    private String resolverNombreTemplate(PlantillaEmailConfig.TipoDiseno diseno) {
        if (diseno == null) return "recordatorio-clasico";
        return switch (diseno) {
            case MODERNO     -> "recordatorio-moderno";
            case MINIMALISTA -> "recordatorio-minimalista";
            default          -> "recordatorio-clasico";
        };
    }

    // ══════════════════════════════════════════════════════════════════
    // ENVÍO CON HTML INLINE (genérico / fallback)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Envía un email con HTML inline usando la API de Resend.
     * Úsalo cuando no haya un template de classpath o para emails one-off.
     *
     * @param destinatario Email del destinatario
     * @param asunto       Asunto del email
     * @param contenido    Contenido HTML del email
     * @return true si se envió correctamente
     */
    public boolean enviarEmail(String destinatario, String asunto, String contenido) {
        log.info("[Resend] Enviando email a: {} | Asunto: {}", destinatario, asunto);

        if (resendApiKey == null || resendApiKey.isBlank()) {
            log.warn("⚠️ Resend no configurado. Email no enviado.");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(resendApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("from", fromName + " <" + fromEmail + ">");
            body.put("to", List.of(destinatario));
            body.put("subject", asunto);
            body.put("html", contenido);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = doPost(RESEND_API_URL, request);

            if (response.getStatusCode().is2xxSuccessful()) {
                String emailId = response.getBody() != null ? (String) response.getBody().get("id") : null;
                log.info("✅ Email enviado a: {} | id: {}", destinatario, emailId);
                return true;
            } else {
                log.error("❌ Error al enviar email. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (RestClientException e) {
            log.error("❌ Error al enviar email a {}: {}", destinatario, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("❌ Error inesperado al enviar email: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Mantiene compatibilidad con código que usaba templates dinámicos.
     */
    public boolean enviarEmailConTemplate(String destinatario, String templateId, Map<String, Object> templateData) {
        log.info("[Resend] Enviando email (template ref: {}) a: {}", templateId, destinatario);

        StringBuilder html = new StringBuilder(
                "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">"
        );
        templateData.forEach((k, v) ->
                html.append("<p><strong>").append(k).append(":</strong> ").append(v).append("</p>")
        );
        html.append("</div>");

        return enviarEmail(destinatario, "Notificación", html.toString());
    }

    // ══════════════════════════════════════════════════════════════════
    // EMAILS DE NEGOCIO
    // ══════════════════════════════════════════════════════════════════

    /**
     * Envía confirmación de registro.
     */
    public boolean enviarConfirmacionRegistro(String destinatario, String nombreUsuario) {
        String asunto = "Bienvenido a Cita Click";
        String contenido = String.format(
                "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">" +
                "<h1 style=\"color: #7c3aed;\">¡Bienvenido %s!</h1>" +
                "<p>Tu cuenta ha sido creada exitosamente en <strong>Cita Click</strong>.</p>" +
                "<p>Ya puedes comenzar a gestionar tus citas y clientes.</p>" +
                "</div>",
                nombreUsuario
        );
        return enviarEmail(destinatario, asunto, contenido);
    }

    /**
     * Envía recordatorio de cita usando el template Clásico con valores por defecto.
     * Equivale a llamar con config = null.
     */
    public boolean enviarRecordatorioCita(String destinatario, String nombreCliente, String fechaCita,
                                          String horaCita, String nombreServicio, String nombreNegocio) {
        return enviarRecordatorioCita(destinatario, nombreCliente, fechaCita, horaCita,
                                     nombreServicio, nombreNegocio, (PlantillaEmailConfig) null);
    }

    /**
     * Envía recordatorio de cita usando el template del diseño elegido por el negocio.
     * Incluye variables de estilo y texto personalizados (colores, mensajeBienvenida, firma, infoContacto).
     * Si config es null o está inactiva, se usan los valores por defecto del template.
     *
     * @param config Configuración de plantilla del negocio (puede ser null → usa defaults)
     */
    public boolean enviarRecordatorioCita(String destinatario, String nombreCliente, String fechaCita,
                                          String horaCita, String nombreServicio, String nombreNegocio,
                                          PlantillaEmailConfig config) {
        String asunto = String.format("Recordatorio de cita - %s", nombreNegocio);

        // Si el negocio tiene config activa, usarla; sino usar defaults
        boolean usarConfig = config != null && config.isActiva();
        PlantillaEmailConfig.TipoDiseno diseno = usarConfig && config.getDisenoBase() != null
                ? config.getDisenoBase()
                : PlantillaEmailConfig.TipoDiseno.CLASICO;

        Map<String, String> variables = new HashMap<>();
        // Variables de cita
        variables.put("nombreCliente",  nombreCliente);
        variables.put("nombreServicio", nombreServicio);
        variables.put("fechaCita",      fechaCita);
        variables.put("horaCita",       horaCita);
        variables.put("nombreNegocio",  nombreNegocio);

        // Variables de color (con defaults que coinciden con los colores originales del template)
        variables.put("colorPrimario",   usarConfig && config.getColorPrimario()  != null ? config.getColorPrimario()  : "#2563eb");
        variables.put("colorSecundario", usarConfig && config.getColorSecundario() != null ? config.getColorSecundario() : "#7c3aed");
        variables.put("colorFondo",      usarConfig && config.getColorFondo()     != null ? config.getColorFondo()     : "#f4f4f5");

        // Variables de texto personalizado
        String mensajeBienvenida = usarConfig && config.getMensajeBienvenida() != null && !config.getMensajeBienvenida().isBlank()
                ? config.getMensajeBienvenida()
                : "Te recordamos que tienes una cita próxima. ¡Te esperamos con gusto!";
        variables.put("mensajeBienvenida", mensajeBienvenida);

        String firma = usarConfig && config.getFirma() != null && !config.getFirma().isBlank()
                ? config.getFirma()
                : nombreNegocio;
        variables.put("firma", firma);

        // infoContacto como bloque HTML pre-armado (vacío si no hay info)
        String infoContacto = usarConfig && config.getInfoContacto() != null ? config.getInfoContacto() : "";
        String infoContactoHtml = infoContacto.isBlank() ? ""
                : "<p style=\"color:#6b7280;font-size:12px;margin:0 0 6px 0;\">" + infoContacto + "</p>";
        variables.put("infoContactoHtml", infoContactoHtml);

        return enviarConTemplate(destinatario, asunto, resolverNombreTemplate(diseno), variables);
    }

    /**
     * Envía recordatorio usando un diseño específico (con colores y textos por defecto).
     * Mantiene compatibilidad con código que pasa el diseño explícitamente.
     *
     * @param diseno Diseño de la plantilla (CLASICO, MODERNO, MINIMALISTA)
     */
    public boolean enviarRecordatorioCita(String destinatario, String nombreCliente, String fechaCita,
                                          String horaCita, String nombreServicio, String nombreNegocio,
                                          PlantillaEmailConfig.TipoDiseno diseno) {
        String asunto = String.format("Recordatorio de cita - %s", nombreNegocio);

        Map<String, String> variables = new HashMap<>();
        variables.put("nombreCliente",     nombreCliente);
        variables.put("nombreServicio",    nombreServicio);
        variables.put("fechaCita",         fechaCita);
        variables.put("horaCita",          horaCita);
        variables.put("nombreNegocio",     nombreNegocio);
        // Valores por defecto de estilo (sin configuración personalizada)
        variables.put("colorPrimario",     "#2563eb");
        variables.put("colorSecundario",   "#7c3aed");
        variables.put("colorFondo",        "#f4f4f5");
        variables.put("mensajeBienvenida", "Te recordamos que tienes una cita próxima. ¡Te esperamos con gusto!");
        variables.put("firma",             nombreNegocio);
        variables.put("infoContactoHtml",  "");

        return enviarConTemplate(destinatario, asunto, resolverNombreTemplate(diseno), variables);
    }

    /**
     * Sobrecarga por compatibilidad con código legado.
     * @deprecated Usar {@link #enviarRecordatorioCita(String, String, String, String, String, String)}
     */
    @Deprecated
    public boolean enviarRecordatorioCita(String destinatario, String nombreCliente, String fechaHora,
                                          String nombreServicio, String nombreNegocio) {
        return enviarRecordatorioCita(destinatario, nombreCliente, fechaHora, "",
                                     nombreServicio, nombreNegocio);
    }

    /**
     * Envía confirmación de cita.
     */
    public boolean enviarConfirmacionCita(String destinatario, String nombreCliente,
                                          String fechaHora, String nombreServicio) {
        String asunto = "Confirmación de cita - Cita Click";
        String contenido = String.format(
                "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">" +
                "<h2 style=\"color: #7c3aed;\">Cita Confirmada ✓</h2>" +
                "<p>Hola <strong>%s</strong>,</p>" +
                "<p>Tu cita ha sido confirmada exitosamente.</p>" +
                "<table style=\"width: 100%%; border-collapse: collapse; margin: 20px 0;\">" +
                "<tr style=\"background-color: #f5f3ff;\">" +
                "<td style=\"padding: 10px 16px; border-bottom: 1px solid #E5E7EB;\"><strong>Servicio</strong></td>" +
                "<td style=\"padding: 10px 16px; border-bottom: 1px solid #E5E7EB;\">%s</td></tr>" +
                "<tr><td style=\"padding: 10px 16px;\"><strong>Fecha y hora</strong></td>" +
                "<td style=\"padding: 10px 16px;\">%s</td></tr>" +
                "</table>" +
                "<p>Si necesitas hacer algún cambio, por favor contáctanos.</p>" +
                "</div>",
                nombreCliente, nombreServicio, fechaHora
        );
        return enviarEmail(destinatario, asunto, contenido);
    }

    /**
     * Envía email de invitación a un nuevo usuario del negocio.
     */
    public boolean enviarEmailInvitacionUsuario(String destinatario, String nombreUsuario,
                                                String nombreNegocio, String passwordTemporal) {
        String asunto = String.format("Invitación a unirte a %s en Cita Click", nombreNegocio);
        String contenido = String.format(
                "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">" +
                "<h2 style=\"color: #7c3aed;\">¡Has sido invitado!</h2>" +
                "<p>Hola <strong>%s</strong>,</p>" +
                "<p>Has sido invitado a unirte a <strong>%s</strong> en Cita Click.</p>" +
                "<h3 style=\"color: #374151;\">Tus credenciales de acceso:</h3>" +
                "<table style=\"width: 100%%; border-collapse: collapse; margin: 20px 0;\">" +
                "<tr style=\"background-color: #f5f3ff;\">" +
                "<td style=\"padding: 10px 16px; border-bottom: 1px solid #E5E7EB;\"><strong>Email</strong></td>" +
                "<td style=\"padding: 10px 16px; border-bottom: 1px solid #E5E7EB;\">%s</td></tr>" +
                "<tr><td style=\"padding: 10px 16px;\"><strong>Contraseña temporal</strong></td>" +
                "<td style=\"padding: 10px 16px; font-family: monospace;\">%s</td></tr>" +
                "</table>" +
                "<p style=\"color: #DC2626;\"><strong>Importante:</strong> Cambia tu contraseña al iniciar sesión por primera vez.</p>" +
                "<p>¡Bienvenido al equipo!</p>" +
                "</div>",
                nombreUsuario, nombreNegocio, destinatario, passwordTemporal
        );
        return enviarEmail(destinatario, asunto, contenido);
    }

    /**
     * Envía email de verificación de cuenta usando el template HTML del classpath.
     * Variables reemplazadas: {@code {{nombre}}}, {@code {{verificationUrl}}}
     *
     * @param destinatario    Email del destinatario
     * @param nombreUsuario   Nombre del usuario
     * @param verificationUrl URL de verificación (expira en 24 horas)
     * @return true si se envió correctamente
     */
    public boolean enviarEmailVerificacion(String destinatario, String nombreUsuario, String verificationUrl) {
        String asunto = "Verifica tu cuenta en Cita Click";

        Map<String, String> variables = new HashMap<>();
        variables.put("nombre",          nombreUsuario);
        variables.put("verificationUrl", verificationUrl);

        return enviarConTemplate(destinatario, asunto, "verificacion-correo", variables);
    }

    /**
     * Envía aviso de fin de periodo de prueba usando el template HTML {@code prueba-terminando.html}.
     * Variables reemplazadas: {@code {{nombre}}}, {@code {{loginUrl}}}
     *
     * @param destinatario Email del destinatario
     * @param nombre       Nombre del negocio / titular
     * @param loginUrl     URL de login que redirige a la selección de planes
     * @return true si se envió correctamente
     */
    public boolean enviarEmailFinPrueba(String destinatario, String nombre, String loginUrl) {
        String asunto = "Tu periodo de prueba termina mañana — elige tu plan en Cita Click";

        Map<String, String> variables = new HashMap<>();
        variables.put("nombre",   nombre);
        variables.put("loginUrl", loginUrl);

        return enviarConTemplate(destinatario, asunto, "prueba-terminando", variables);
    }
}
