package com.inssoft.people.employee.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RfcValidator implements ConstraintValidator<Rfc, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return MexicanIdentifiers.isValidRfc(value);
    }
}
