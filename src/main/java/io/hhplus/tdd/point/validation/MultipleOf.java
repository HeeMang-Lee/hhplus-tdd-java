package io.hhplus.tdd.point.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultipleOfValidator.class)
public @interface MultipleOf {
    String message() default "값은 {value}의 배수여야 합니다";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    long value();
}
