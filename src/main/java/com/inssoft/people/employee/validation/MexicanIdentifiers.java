package com.inssoft.people.employee.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Identificadores mexicanos: formato, normalización y enmascarado.
 *
 * <p>La validación de dígito verificador se incorporará en la fase de
 * expedientes; por ahora se valida estructura y se normaliza a mayúsculas.</p>
 */
public final class MexicanIdentifiers {

    private static final Pattern CURP = Pattern.compile(
            "^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$", Pattern.CASE_INSENSITIVE);

    private static final Pattern RFC = Pattern.compile(
            "^[A-Z&Ñ]{3,4}\\d{6}[A-Z0-9]{3}$", Pattern.CASE_INSENSITIVE);

    private static final Pattern NSS = Pattern.compile(
            "^\\d{11}$");

    private MexicanIdentifiers() {
    }

    public static boolean isValidCurp(String value) {
        return value == null || value.isBlank() || CURP.matcher(value.trim()).matches();
    }

    public static boolean isValidRfc(String value) {
        return value == null || value.isBlank() || RFC.matcher(value.trim()).matches();
    }

    public static boolean isValidNss(String value) {
        return value == null || value.isBlank() || NSS.matcher(value.trim()).matches();
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= 4) {
            return "****";
        }
        return "****" + normalized.substring(normalized.length() - 4);
    }
}
