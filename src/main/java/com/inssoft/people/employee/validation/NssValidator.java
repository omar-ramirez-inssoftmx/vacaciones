package com.inssoft.people.employee.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NssValidator implements ConstraintValidator<Nss, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return MexicanIdentifiers.isValidNss(value);
    }
}
