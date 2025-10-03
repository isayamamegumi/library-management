package com.library.management.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 環境依存文字バリデータ
 * PDF生成時にエラーになる可能性のある文字をチェック
 */
public class NoPlatformDependentCharsValidator implements ConstraintValidator<NoPlatformDependentChars, String> {

    @Override
    public void initialize(NoPlatformDependentChars constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        // 環境依存文字のチェック
        for (char c : value.toCharArray()) {
            if (isPlatformDependentChar(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 環境依存文字かどうかを判定
     */
    private boolean isPlatformDependentChar(char c) {
        // 機種依存文字の範囲
        // ① 丸囲み数字: U+2460-U+24FF, U+3251-U+32BF
        if ((c >= 0x2460 && c <= 0x24FF) || (c >= 0x3251 && c <= 0x32BF)) {
            return true;
        }

        // ② ローマ数字: U+2160-U+217F
        if (c >= 0x2160 && c <= 0x217F) {
            return true;
        }

        // ③ 単位記号: U+3300-U+3357
        if (c >= 0x3300 && c <= 0x3357) {
            return true;
        }

        // ④ 全角記号: U+FFE0-U+FFEF
        if (c >= 0xFFE0 && c <= 0xFFEF) {
            return true;
        }

        // ⑤ IBM拡張文字・NEC選定IBM拡張文字
        // U+F860-U+F8FF, U+FA0E-U+FA0F, U+FA11, U+FA13-U+FA14, U+FA1F, U+FA21, U+FA23-U+FA24, U+FA27-U+FA29
        if ((c >= 0xF860 && c <= 0xF8FF) ||
            (c >= 0xFA0E && c <= 0xFA0F) ||
            c == 0xFA11 ||
            (c >= 0xFA13 && c <= 0xFA14) ||
            c == 0xFA1F ||
            c == 0xFA21 ||
            (c >= 0xFA23 && c <= 0xFA24) ||
            (c >= 0xFA27 && c <= 0xFA29)) {
            return true;
        }

        // ⑥ サロゲートペア（基本多言語面外の文字）
        if (Character.isSurrogate(c)) {
            return true;
        }

        // ⑦ 外字領域: U+E000-U+F8FF (Private Use Area)
        if (c >= 0xE000 && c <= 0xF8FF) {
            return true;
        }

        return false;
    }
}
