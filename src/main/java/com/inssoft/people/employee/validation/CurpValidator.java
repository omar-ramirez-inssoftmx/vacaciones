package com.inssoft.people.employee.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CurpValidator implements ConstraintValidator<Curp, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return MexicanIdentifiers.isValidCurp(value);
    }
}
