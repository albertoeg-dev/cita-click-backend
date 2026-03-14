package com.reservas.entity.enums;

import lombok.Getter;

@Getter
public enum TipoPlan {

    // ── Planes activos ──────────────────────────────────────────────────────
    BASE("base", "Base", 299.00),
    COMPLETO("completo", "Completo", 1199.00),

    // ── Planes legacy (solo para compatibilidad con datos históricos en BD) ─
    // No se usan en lógica nueva; fromCodigo los redirige a BASE o COMPLETO.
    @Deprecated BASICO("basico", "Básico", 299.00),
    @Deprecated PROFESIONAL("profesional", "Profesional", 699.00),
    @Deprecated PREMIUM("premium", "Premium", 1299.00);

    private final String codigo;
    private final String nombre;
    private final double precioMensual;

    TipoPlan(String codigo, String nombre, double precioMensual) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.precioMensual = precioMensual;
    }

    /**
     * Resuelve un código de plan a su TipoPlan correspondiente.
     * Incluye compatibilidad con planes legacy:
     *   basico / profesional → BASE
     *   premium              → COMPLETO
     */
    public static TipoPlan fromCodigo(String codigo) {
        if (codigo == null) throw new IllegalArgumentException("Plan no puede ser null");
        return switch (codigo.toLowerCase()) {
            case "base"                      -> BASE;
            case "completo", "bundle"        -> COMPLETO;
            case "basico", "basic"           -> BASE;        // legacy
            case "profesional", "professional" -> BASE;      // legacy
            case "premium"                   -> COMPLETO;    // legacy
            default -> throw new IllegalArgumentException("Plan no válido: " + codigo);
        };
    }
}
