package com.library.management.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * リスト内の文字列が環境依存文字を含まないことを検証するアノテーション
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoPlatformDependentCharsInListValidator.class)
@Documented
public @interface NoPlatformDependentCharsInList {
    String message() default "著者名に環境依存文字を使用しないでください";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
