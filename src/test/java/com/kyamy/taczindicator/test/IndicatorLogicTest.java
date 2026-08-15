package com.kyamy.taczindicator.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ダメージインジケータの計算ロジックおよびフォーマットに関する単体テスト
 */
public class IndicatorLogicTest {

    @Test
    @DisplayName("距離非依存スケール計算の検証")
    void testConstantScaleCalculation() {
        double baseScale = 0.025D;
        double distanceScaleFactor = 1.0D;

        // 至近距離 (1m)
        double distClose = 1.0D;
        double scaleClose = baseScale * Math.max(1.0D, distClose * distanceScaleFactor);
        assertEquals(baseScale, scaleClose, 1e-6);

        // 遠距離 (100m)
        double distFar = 100.0D;
        double scaleFar = baseScale * Math.max(1.0D, distFar * distanceScaleFactor);
        assertEquals(baseScale * 100.0D, scaleFar, 1e-6);

        // スナイパー距離 (500m)
        double distSniper = 500.0D;
        double scaleSniper = baseScale * Math.max(1.0D, distSniper * distanceScaleFactor);
        assertEquals(baseScale * 500.0D, scaleSniper, 1e-6);
    }

    @Test
    @DisplayName("フェードアウトアルファ値計算の検証")
    void testAlphaFadeOutCalculation() {
        int maxLifetime = 35;

        // 開始時 (age = 0)
        float alphaStart = calculateAlpha(0, maxLifetime);
        assertEquals(1.0f, alphaStart, 1e-6);

        // 中間 (age = 15)
        float alphaMid = calculateAlpha(15, maxLifetime);
        assertEquals(1.0f, alphaMid, 1e-6);

        // フェード開始 (70% = age 24.5)
        float alphaFadeStart = calculateAlpha(25, maxLifetime);
        assertTrue(alphaFadeStart >= 0.9f);

        // 終了直前 (age = 34)
        float alphaEnd = calculateAlpha(34, maxLifetime);
        assertTrue(alphaEnd < 0.2f && alphaEnd >= 0.0f);
    }

    @Test
    @DisplayName("ダメージ数値フォーマットの検証")
    void testDamageFormat() {
        float damage = 24.567f;
        int decimalPlaces = 1;
        String formatted = String.format(Locale.ROOT, "%." + decimalPlaces + "f", damage);
        assertEquals("24.6", formatted);

        decimalPlaces = 2;
        formatted = String.format(Locale.ROOT, "%." + decimalPlaces + "f", damage);
        assertEquals("24.57", formatted);
    }

    private float calculateAlpha(int ageTicks, int maxLifetime) {
        float progress = (float) ageTicks / (float) maxLifetime;
        if (progress > 0.7f) {
            return Math.max(0.0f, (1.0f - progress) / 0.3f);
        }
        return 1.0f;
    }
}
