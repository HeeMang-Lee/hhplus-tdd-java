package io.hhplus.tdd.point.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MultipleOfValidator implements ConstraintValidator<MultipleOf, Long> {

    private long multipleOf;

    @Override
    public void initialize(MultipleOf constraintAnnotation) {
        this.multipleOf = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Long value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value % multipleOf == 0;
    }
}
