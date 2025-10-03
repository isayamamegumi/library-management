package com.library.management.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 環境依存文字を含まないことを検証するアノテーション
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoPlatformDependentCharsValidator.class)
@Documented
public @interface NoPlatformDependentChars {
    String message() default "環境依存文字を使用しないでください";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
