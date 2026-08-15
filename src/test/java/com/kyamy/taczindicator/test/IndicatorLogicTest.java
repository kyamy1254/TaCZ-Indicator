package com.kyamy.taczindicator.test;

import com.kyamy.taczindicator.server.TaCZCompatHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ダメージインジケータの計算ロジック、累積加算、スクロール処理、3D Ray-Box幾何ヘッドショット判定、およびヴィネット減衰に関する単体テスト
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
    @DisplayName("盾・盾貫通フォントの末尾（接尾辞）配置フォーマット検証")
    void testShieldAndPenetrationSuffixFormatting() {
        String iconShield = "\uE001";
        String iconPenetration = "\uE002";

        // 通常ダメージ + 盾貫通 (AP) -> 末尾に \uE002
        float damage = 45.0f;
        String textAP = String.format(Locale.ROOT, "%.1f", damage) + " §b" + iconPenetration;
        assertEquals("45.0 §b\uE002", textAP);

        // クリティカル + 盾貫通 (AP) -> 接頭辞★ + 末尾に \uE002
        String textCritAP = "§6★ §l" + String.format(Locale.ROOT, "%.1f", damage) + "§r §b" + iconPenetration;
        assertEquals("§6★ §l45.0§r §b\uE002", textCritAP);

        // ヘッドショット + 通常防具軽減 (盾) -> 接頭辞☠ + 末尾に \uE001
        String textHSShield = "§c☠ §l" + String.format(Locale.ROOT, "%.1f", damage) + "§r §f" + iconShield;
        assertEquals("§c☠ §l45.0§r §f\uE001", textHSShield);
    }

    @Test
    @DisplayName("3D Ray-Box交差レイキャストヘッドショット判定の数学検証")
    void testRayBoxHeadshotIntersection() {
        // ターゲット (モブ): 座標 (0, 64, 50), 身長 1.8m, 幅 0.6m
        double victimX = 0.0;
        double victimY = 64.0;
        double victimZ = 50.0;
        double height = 1.8;
        double eyeY = 65.62;

        double headMinY = Math.max(victimY + height * 0.68, eyeY - 0.35); // 65.27
        double headMaxY = victimY + height + 0.25; // 66.05
        double halfWidth = 0.45;

        double boxMinX = victimX - halfWidth;
        double boxMaxX = victimX + halfWidth;
        double boxMinZ = victimZ - halfWidth;
        double boxMaxZ = victimZ + halfWidth;

        // 1. スナイパー視線 (プレイヤー位置: (0, 65.5, 0) -> 頭部 (0, 65.5, 50) へ水平照準)
        double eyePosX = 0.0, eyePosY = 65.5, eyePosZ = 0.0;
        double rayDirX = 0.0, rayDirY = 0.0, rayDirZ = 1.0;

        boolean hitsHead = rayIntersectsAABB(eyePosX, eyePosY, eyePosZ, rayDirX, rayDirY, rayDirZ,
                boxMinX, headMinY, boxMinZ, boxMaxX, headMaxY, boxMaxZ);
        assertTrue(hitsHead, "スナイパー視線がモブの頭部AABBに命中するべき");

        // 2. 胴体照準 (Y = 64.5)
        boolean hitsBody = rayIntersectsAABB(eyePosX, 64.5, eyePosZ, rayDirX, 0.0, rayDirZ,
                boxMinX, headMinY, boxMinZ, boxMaxX, headMaxY, boxMaxZ);
        assertFalse(hitsBody, "胴体狙いの射撃は頭部AABBに命中しないべき");
    }

    @Test
    @DisplayName("被ダメージ画面赤色効果（ヴィネット）の減衰計算検証")
    void testDamageVignetteAlphaEasing() {
        int maxDuration = 14;
        double baseOpacity = 0.45;
        float damageIntensity = 1.0f;

        // 開始直後 (progress = 1.0)
        int remaining = 14;
        float progressStart = remaining / (float) maxDuration;
        float alphaStart = progressStart * progressStart * (float) baseOpacity * damageIntensity;
        assertEquals(0.45f, alphaStart, 1e-4);

        // 中間 (progress = 0.5)
        remaining = 7;
        float progressMid = remaining / (float) maxDuration;
        float alphaMid = progressMid * progressMid * (float) baseOpacity * damageIntensity;
        assertEquals(0.1125f, alphaMid, 1e-4);

        // 終了時 (progress = 0.0)
        remaining = 0;
        float progressEnd = remaining / (float) maxDuration;
        float alphaEnd = progressEnd * progressEnd * (float) baseOpacity * damageIntensity;
        assertEquals(0.0f, alphaEnd, 1e-4);
    }

    @Test
    @DisplayName("包括的リフレクション抽出ヘルパーの動作検証")
    void testDeepReflectionExtraction() {
        // モックイベントクラス
        class MockTaCZEvent {
            public boolean isHeadShot() { return true; }
            public float getHeadshotMultiplier() { return 2.5f; }
            public boolean isArmorPiercing() { return true; }
        }

        MockTaCZEvent mockEvent = new MockTaCZEvent();
        assertTrue(TaCZCompatHandler.extractHeadshotProperty(mockEvent));
        assertTrue(TaCZCompatHandler.extractBooleanPropertyDeep(mockEvent, "armorpiercing"));
        assertEquals(2.5f, TaCZCompatHandler.extractFloatPropertyDeep(mockEvent, "headshotmultiplier"), 1e-4);
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

    private static boolean rayIntersectsAABB(double rX, double rY, double rZ,
                                             double dX, double dY, double dZ,
                                             double minX, double minY, double minZ,
                                             double maxX, double maxY, double maxZ) {
        double tmin = (minX - rX) / (dX == 0 ? 1e-9 : dX);
        double tmax = (maxX - rX) / (dX == 0 ? 1e-9 : dX);
        if (tmin > tmax) { double temp = tmin; tmin = tmax; tmax = temp; }

        double tymin = (minY - rY) / (dY == 0 ? 1e-9 : dY);
        double tymax = (maxY - rY) / (dY == 0 ? 1e-9 : dY);
        if (tymin > tymax) { double temp = tymin; tymin = tymax; tymax = temp; }

        if ((tmin > tymax) || (tymin > tmax)) return false;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        double tzmin = (minZ - rZ) / (dZ == 0 ? 1e-9 : dZ);
        double tzmax = (maxZ - rZ) / (dZ == 0 ? 1e-9 : dZ);
        if (tzmin > tzmax) { double temp = tzmin; tzmin = tzmax; tzmax = temp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return false;
        if (tzmin > tmin) tmin = tzmin;
        if (tzmax < tmax) tmax = tzmax;

        return tmax >= 0;
    }

    private float calculateAlpha(int ageTicks, int maxLifetime) {
        float progress = (float) ageTicks / (float) maxLifetime;
        if (progress > 0.7f) {
            return Math.max(0.0f, (1.0f - progress) / 0.3f);
        }
        return 1.0f;
    }
}
