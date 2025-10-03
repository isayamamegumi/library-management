package com.library.management.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

/**
 * リスト内文字列の環境依存文字バリデータ
 */
public class NoPlatformDependentCharsInListValidator implements ConstraintValidator<NoPlatformDependentCharsInList, List<String>> {

    private final NoPlatformDependentCharsValidator stringValidator = new NoPlatformDependentCharsValidator();

    @Override
    public void initialize(NoPlatformDependentCharsInList constraintAnnotation) {
    }

    @Override
    public boolean isValid(List<String> values, ConstraintValidatorContext context) {
        if (values == null || values.isEmpty()) {
            return true;
        }

        for (String value : values) {
            if (!stringValidator.isValid(value, context)) {
                return false;
            }
        }

        return true;
    }
}
