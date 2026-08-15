package com.kyamy.taczindicator.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ダメージインジケータの計算ロジック、累積加算、スクロール処理、投影計算に関する単体テスト
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

    @Test
    @DisplayName("連続ダメージ累積加算ロジックの検証")
    void testCumulativeDamageAccumulation() {
        float initialDamage = 15.5f;
        int hitCount = 1;
        boolean isHeadshot = false;

        // 2発目のヒット (12.0ダメージ)
        float hit2Damage = 12.0f;
        initialDamage += hit2Damage;
        hitCount++;
        assertEquals(27.5f, initialDamage, 1e-6);
        assertEquals(2, hitCount);

        // 3発目のヒット (ヘッドショット 30.0ダメージ)
        float hit3Damage = 30.0f;
        initialDamage += hit3Damage;
        hitCount++;
        isHeadshot = true;
        assertEquals(57.5f, initialDamage, 1e-6);
        assertEquals(3, hitCount);
        assertTrue(isHeadshot);

        // フォーマット検証
        String formatted = String.format(Locale.ROOT, "%.1f x%d", initialDamage, hitCount);
        assertEquals("57.5 x3", formatted);
    }

    @Test
    @DisplayName("上方スクロール（はけ）オフセット計算の検証")
    void testScrollPushOffset() {
        double scrollSpacing = 12.0D;

        double indicator1ScrollY = 0.0D;

        // 2発目発生時: 1発目が押し上げられる
        indicator1ScrollY += scrollSpacing;
        double indicator2ScrollY = 0.0D;
        assertEquals(12.0D, indicator1ScrollY, 1e-6);
        assertEquals(0.0D, indicator2ScrollY, 1e-6);

        // 3発目発生時: 1発目と2発目がさらに押し上げられる
        indicator1ScrollY += scrollSpacing;
        indicator2ScrollY += scrollSpacing;
        double indicator3ScrollY = 0.0D;
        assertEquals(24.0D, indicator1ScrollY, 1e-6);
        assertEquals(12.0D, indicator2ScrollY, 1e-6);
        assertEquals(0.0D, indicator3ScrollY, 1e-6);
    }

    @Test
    @DisplayName("3Dから2D画面座標への透視投影数学モデルの検証")
    void testProjectionMath() {
        org.joml.Matrix4f proj = new org.joml.Matrix4f().perspective(
                (float) Math.toRadians(70.0),
                854.0f / 480.0f,
                0.05f,
                1000.0f
        );

        // 前方 (0, 0, -10)
        org.joml.Vector4f clip = new org.joml.Vector4f(0.0f, 0.0f, -10.0f, 1.0f);
        proj.transform(clip);
        assertTrue(clip.w > 0.0f);
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        assertEquals(0.0f, ndcX, 1e-4);
        assertEquals(0.0f, ndcY, 1e-4);

        // 右上 (2, 1, -10)
        org.joml.Vector4f clipRightUp = new org.joml.Vector4f(2.0f, 1.0f, -10.0f, 1.0f);
        proj.transform(clipRightUp);
        assertTrue(clipRightUp.w > 0.0f);
        float ndcRight = clipRightUp.x / clipRightUp.w;
        float ndcUp = clipRightUp.y / clipRightUp.w;
        assertTrue(ndcRight > 0.0f);
        assertTrue(ndcUp > 0.0f);
    }

    private float calculateAlpha(int ageTicks, int maxLifetime) {
        float progress = (float) ageTicks / (float) maxLifetime;
        if (progress > 0.7f) {
            return Math.max(0.0f, (1.0f - progress) / 0.3f);
        }
        return 1.0f;
    }
}
