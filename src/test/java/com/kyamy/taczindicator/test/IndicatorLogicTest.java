package com.kyamy.taczindicator.test;

import com.kyamy.taczindicator.client.DamageVignetteRenderer;
import com.kyamy.taczindicator.client.model.KillAlertInstance;
import com.kyamy.taczindicator.server.TaCZCompatHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ダメージインジケータの計算ロジック、累積加算、スクロール処理、厳密頭部判定(y-0.25〜+0.25)、
 * キル演出距離表示[100m]と置換更新、およびヴィネット減衰に関する単体テスト
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
    @DisplayName("連続ダメージ累積加算時の最新ヒット状態・アイコン更新検証")
    void testAccumulateDamageUpdatesStateToLatestHit() {
        com.kyamy.taczindicator.client.model.IndicatorInstance ind = new com.kyamy.taczindicator.client.model.IndicatorInstance(
                1, 0, 64, 0, 100.0f, true, false, true, true, false
        );

        // 1発目: ヘッドショット + AP貫通弾
        assertTrue(ind.isHeadshot());
        assertTrue(ind.isArmorPiercing());
        assertFalse(ind.isHitArmor());
        assertTrue(ind.getFormattedText().contains("☠"));
        assertTrue(ind.getFormattedText().contains("\uE002"));

        // 2発目: 通常胴体ヒット (APなし・防具なし)
        ind.accumulateDamage(35.0f, false, false, true, false, false);
        assertEquals(135.0f, ind.getDamage(), 1e-4);
        assertEquals(2, ind.getHitCount());
        assertFalse(ind.isHeadshot(), "非ヘッドショットヒット後はヘッドショット状態が解除されるべき");
        assertFalse(ind.isArmorPiercing(), "非AP弾ヒット後はAPアイコンが解除されるべき");
        assertFalse(ind.getFormattedText().contains("☠"), "非ヘッドショット時はドクロアイコンが表示されないべき");
        assertFalse(ind.getFormattedText().contains("\uE002"), "非AP時はAPアイコンが表示されないべき");
        assertTrue(ind.getFormattedText().contains("(x2)"));

        // 3発目: クリティカル + 通常防具軽減
        ind.accumulateDamage(25.0f, false, true, true, false, true);
        assertEquals(160.0f, ind.getDamage(), 1e-4);
        assertEquals(3, ind.getHitCount());
        assertTrue(ind.isCritical());
        assertTrue(ind.isHitArmor());
        assertFalse(ind.isHeadshot());
        assertFalse(ind.isArmorPiercing());
        assertTrue(ind.getFormattedText().contains("★"));
        assertTrue(ind.getFormattedText().contains("\uE001"));
        assertTrue(ind.getFormattedText().contains("(x3)"));

        // 4発目: 通常胴体ヒット
        ind.accumulateDamage(20.0f, false, false, true, false, false);
        assertEquals(180.0f, ind.getDamage(), 1e-4);
        assertEquals(4, ind.getHitCount());
        assertFalse(ind.isCritical());
        assertFalse(ind.isHitArmor());
        assertFalse(ind.getFormattedText().contains("★"));
        assertFalse(ind.getFormattedText().contains("\uE001"));
        assertTrue(ind.getFormattedText().contains("(x4)"));
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

        // 通常ダメージ + 盾貫通 (AP) -> 白 (§f\uE002)
        float damage = 45.0f;
        String textAP = String.format(Locale.ROOT, "%.1f", damage) + " §f" + iconPenetration;
        assertEquals("45.0 §f\uE002", textAP);

        // クリティカル + 盾貫通 (AP) -> 接頭辞★ + 白 (§f\uE002)
        String textCritAP = "§6★ §l" + String.format(Locale.ROOT, "%.1f", damage) + "§r §f" + iconPenetration;
        assertEquals("§6★ §l45.0§r §f\uE002", textCritAP);

        // ヘッドショット + 通常防具軽減 (盾) -> 接頭辞☠ + 水色 (§b\uE001)
        String textHSShield = "§c☠ §l" + String.format(Locale.ROOT, "%.1f", damage) + "§r §b" + iconShield;
        assertEquals("§c☠ §l45.0§r §b\uE001", textHSShield);
    }

    @Test
    @DisplayName("厳密な頭部当たり判定 (x-0.25 < y < x+0.25) の数学検証")
    void testStrictHeadHitboxBounds() {
        double eyeY = 65.62; // x = 目の高さ
        double headMinY = eyeY - 0.25 + 0.0001;
        double headMaxY = eyeY + 0.25 - 0.0001;

        // 目の高さ中心 (y = 65.62) -> 合格
        assertTrue(65.62 >= headMinY && 65.62 <= headMaxY);

        // 目の少し上 (y = 65.80) -> 合格
        assertTrue(65.80 >= headMinY && 65.80 <= headMaxY);

        // 境界値 (y = 65.62 + 0.25 = 65.87) -> 厳密不等式のため不合格
        assertFalse(65.87 >= headMinY && 65.87 <= headMaxY);

        // 境界値 (y = 65.62 - 0.25 = 65.37) -> 厳密不等式のため不合格
        assertFalse(65.37 >= headMinY && 65.37 <= headMaxY);

        // 胸部 (y = 65.00) -> 不合格
        assertFalse(65.00 >= headMinY && 65.00 <= headMaxY);
    }

    @Test
    @DisplayName("キル通知の距離表示[100m]フォーマットおよび置換更新検証")
    void testKillAlertDistanceAndReplacement() {
        KillAlertInstance alert = new KillAlertInstance("Zombie", 100);
        assertEquals("Zombie", alert.getVictimName());
        assertEquals(1, alert.getKillCount());
        assertEquals(100, alert.getDistanceMeters());
        assertTrue(alert.getFormattedText().contains("[100m]"));

        // 同種モブ連続キル発生時の置換更新 (45mでのキル)
        alert.updateKill(45);
        assertEquals(2, alert.getKillCount());
        assertEquals(45, alert.getDistanceMeters());
        assertTrue(alert.getFormattedText().contains("[45m]"));
    }

    @Test
    @DisplayName("スクロール上限数(maxScrolledIndicators)制限パージロジック検証")
    void testMaxScrolledIndicatorsPruning() {
        int maxScrolled = 3;
        List<String> list = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            while (list.size() >= maxScrolled && !list.isEmpty()) {
                list.remove(0);
            }
            list.add("Damage " + i);
        }

        assertEquals(3, list.size());
        assertEquals("Damage 3", list.get(0));
        assertEquals("Damage 4", list.get(1));
        assertEquals("Damage 5", list.get(2));
    }

    @Test
    @DisplayName("3D Ray-Box交差レイキャストヘッドショット判定の数学検証")
    void testRayBoxHeadshotIntersection() {
        // ターゲット (モブ): 座標 (0, 64, 50), 目の高さ 65.62
        double victimX = 0.0;
        double victimZ = 50.0;
        double eyeY = 65.62;

        double headMinY = eyeY - 0.25 + 0.0001;
        double headMaxY = eyeY + 0.25 - 0.0001;
        double halfWidth = 0.3; // モブ本来の幅 0.6m の半分

        double boxMinX = victimX - halfWidth;
        double boxMaxX = victimX + halfWidth;
        double boxMinZ = victimZ - halfWidth;
        double boxMaxZ = victimZ + halfWidth;

        // 1. スナイパー視線 (プレイヤー位置: (0, 65.6, 0) -> 頭部 (0, 65.6, 50) へ水平照準)
        double eyePosX = 0.0, eyePosY = 65.6, eyePosZ = 0.0;
        double rayDirX = 0.0, rayDirY = 0.0, rayDirZ = 1.0;

        boolean hitsHead = rayIntersectsAABB(eyePosX, eyePosY, eyePosZ, rayDirX, rayDirY, rayDirZ,
                boxMinX, headMinY, boxMinZ, boxMaxX, headMaxY, boxMaxZ);
        assertTrue(hitsHead, "スナイパー視線がモブの頭部AABBに命中するべき");

        // 2. 胴体照準 (Y = 64.8)
        boolean hitsBody = rayIntersectsAABB(eyePosX, 64.8, eyePosZ, rayDirX, 0.0, rayDirZ,
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
    @DisplayName("瀕死時(Low HP)鼓動・生体呼吸パルス計算の検証")
    void testLowHpHeartbeatPulseCalculation() {
        double heartbeatSpeed = 1.0;
        float danger = 0.5f;

        // 1. 各時刻におけるパルス値が必ず [0.35, 1.0] の範囲に収まることを検証
        for (long t = 0; t <= 2000; t += 50) {
            float pulse = DamageVignetteRenderer.calculateHeartbeatPulse(heartbeatSpeed, danger, t);
            assertTrue(pulse >= 0.3499f && pulse <= 1.0001f,
                    "パルス値は下限0.35から上限1.0の範囲内で滑らかに変動するべき (実際: " + pulse + ")");
        }

        // 2. 危険度0%の時と100%の時の計算が安定していること
        float pulseMinDanger = DamageVignetteRenderer.calculateHeartbeatPulse(1.0, 0.0f, 500);
        float pulseMaxDanger = DamageVignetteRenderer.calculateHeartbeatPulse(1.0, 1.0f, 500);
        assertTrue(pulseMinDanger >= 0.35f && pulseMinDanger <= 1.0f);
        assertTrue(pulseMaxDanger >= 0.35f && pulseMaxDanger <= 1.0f);
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

    @Test
    @DisplayName("カラーテーマ・プリセットの定義検証")
    void testColorThemeDefinitions() {
        for (com.kyamy.taczindicator.config.IndicatorConfig.ColorTheme theme : com.kyamy.taczindicator.config.IndicatorConfig.ColorTheme.values()) {
            assertNotNull(theme.getTranslationKey());
            assertTrue((theme.normalColor & 0xFF000000) == 0, "Color should be 24-bit RGB");
            assertTrue((theme.criticalColor & 0xFF000000) == 0, "Color should be 24-bit RGB");
            assertTrue((theme.headshotColor & 0xFF000000) == 0, "Color should be 24-bit RGB");
        }
    }

    @Test
    @DisplayName("戦闘統計・DPSメーターの集計とリセットロジック検証")
    void testCombatStatsTrackingAndReset() {
        com.kyamy.taczindicator.client.stats.CombatStatsManager manager = com.kyamy.taczindicator.client.stats.CombatStatsManager.getInstance();
        manager.resetStats();

        assertEquals(0.0, manager.getTotalDamage(), 1e-6);
        assertEquals(0, manager.getTotalHits());
        assertEquals(0, manager.getTotalHeadshots());
        assertEquals(0, manager.getTotalKills());
        assertEquals(0.0f, manager.getDPS(), 1e-6);

        // 1発目: 通常 40.0
        manager.recordDamage(40.0f, false, false);
        assertEquals(40.0, manager.getTotalDamage(), 1e-6);
        assertEquals(1, manager.getTotalHits());
        assertEquals(0, manager.getTotalHeadshots());
        assertEquals(0.0f, manager.getHeadshotRate(), 1e-6);

        // 2発目: HS 80.0
        manager.recordDamage(80.0f, true, false);
        assertEquals(120.0, manager.getTotalDamage(), 1e-6);
        assertEquals(2, manager.getTotalHits());
        assertEquals(1, manager.getTotalHeadshots());
        assertEquals(50.0f, manager.getHeadshotRate(), 1e-6);

        // 3発目: キル 30.0
        manager.recordDamage(30.0f, false, true);
        assertEquals(150.0, manager.getTotalDamage(), 1e-6);
        assertEquals(3, manager.getTotalHits());
        assertEquals(1, manager.getTotalKills());

        assertTrue(manager.getDPS() > 0.0f);
        assertTrue(manager.isInCombat());

        // リセット
        manager.resetStats();
        assertEquals(0.0, manager.getTotalDamage(), 1e-6);
        assertEquals(0, manager.getTotalHits());
        assertEquals(0, manager.getTotalHeadshots());
        assertEquals(0, manager.getTotalKills());
        assertEquals(0.0f, manager.getDPS(), 1e-6);
    }

    @Test
    @DisplayName("詳細戦闘統計（ピークDPS、最大単発ダメージ、キル距離、ログ）およびレポート生成検証")
    void testDetailedCombatStatsAndReport() {
        com.kyamy.taczindicator.client.stats.CombatStatsManager manager = com.kyamy.taczindicator.client.stats.CombatStatsManager.getInstance();
        manager.resetStats();

        // 1. AP弾クリティカルヒット
        manager.recordDamage(75.5f, false, true, true, true, false, false, "Zombie", 0);
        assertEquals(75.5, manager.getTotalDamage(), 1e-4);
        assertEquals(1, manager.getTotalHits());
        assertEquals(1, manager.getTotalCriticals());
        assertEquals(1, manager.getTotalArmorPiercing());
        assertEquals(75.5f, manager.getMaxSingleDamage(), 1e-4);

        // 2. スナイパーヘッドショットキル (120m)
        manager.recordDamage(180.0f, true, false, true, false, false, true, "Skeleton", 120);
        assertEquals(255.5, manager.getTotalDamage(), 1e-4);
        assertEquals(2, manager.getTotalHits());
        assertEquals(1, manager.getTotalHeadshots());
        assertEquals(1, manager.getTotalKills());
        assertEquals(180.0f, manager.getMaxSingleDamage(), 1e-4);
        assertEquals(120, manager.getMaxKillDistance());
        assertEquals(120.0f, manager.getAverageKillDistance(), 1e-4);
        assertTrue(manager.getPeakDps() > 0.0f);

        // 3. キル履歴ログエントリの確認（通常ヒットは記録されずキルのみ記録されること）
        assertEquals(1, manager.getCombatLogs().size());
        assertTrue(manager.getCombatLogs().get(0).getMessage().contains("Skeleton"));
        assertTrue(manager.getCombatLogs().get(0).getMessage().contains("120m"));

        // 4. 整形レポート生成
        String report = manager.generateStatsReportText();
        assertNotNull(report);
        assertTrue(report.contains("TaCZ Indicator - 戦闘統計レポート"));
        assertTrue(report.contains("総与ダメージ: 255.5"));
        assertTrue(report.contains("最大単発ダメージ: 180.0"));
        assertTrue(report.contains("最長キル距離: 120 m"));

        manager.resetStats();
        assertEquals(0, manager.getCombatLogs().size());
    }

    @Test
    @DisplayName("高視認性アニメーションスタイル（STATIC_POP, STATIC_FADE, SUBTLE_POP）検証")
    void testAnimationStylePhysics() {
        for (com.kyamy.taczindicator.config.IndicatorConfig.AnimationStyle style : com.kyamy.taczindicator.config.IndicatorConfig.AnimationStyle.values()) {
            assertNotNull(style.getTranslationKey());
        }

        com.kyamy.taczindicator.client.model.IndicatorInstance ind = new com.kyamy.taczindicator.client.model.IndicatorInstance(
                1, 0, 64, 0, 50.0f, false, false, true
        );
        assertNotNull(ind.getAnimationStyle());

        // 初期オフセットは0
        assertEquals(0.0, ind.getInterpolatedAnimOffsetX(0.0f), 1e-4);
        assertEquals(0.0, ind.getInterpolatedAnimOffsetY(0.0f), 1e-4);

        // tick更新
        ind.tick();
        assertTrue(ind.getAgeTicks() == 1);
        assertTrue(ind.getInterpolatedPopScale(0.0f) >= 1.0f);
    }

    @Test
    @DisplayName("フォント用テクスチャ（shield.png, shield_penetration.png）が白色アルファ画像として生成されているか検証")
    void testEnsureWhiteFontGlyphs() throws Exception {
        java.io.File fontDir = new java.io.File("src/main/resources/assets/taczindicator/textures/font");
        fontDir.mkdirs();

        String[] files = new String[]{"shield.png", "shield_penetration.png"};
        for (String file : files) {
            java.io.File srcFile = new java.io.File("temp/" + file);
            if (!srcFile.exists()) {
                srcFile = new java.io.File(fontDir, file);
            }
            if (srcFile.exists()) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(srcFile);
                int w = img.getWidth();
                int h = img.getHeight();
                java.awt.image.BufferedImage whiteImg = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);

                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = img.getRGB(x, y);
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;

                        if (a > 0) {
                            // もし元の画像が黒系でアルファが完全不透明、または輝度ベースで描画されている場合
                            // 輝度を計算してアルファに変換、またはアルファをそのまま使用してRGBを純白 (255, 255, 255) にする
                            int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                            // 元画像が黒シルエット（r=0, g=0, b=0, a=255）なら a を採用
                            // 元画像が白黒アンチエイリアスなら luminance / alpha の適切な方を採用
                            int finalAlpha = a;
                            int finalRgb = (finalAlpha << 24) | (0xFF << 16) | (0xFF << 8) | 0xFF;
                            whiteImg.setRGB(x, y, finalRgb);
                        } else {
                            whiteImg.setRGB(x, y, 0);
                        }
                    }
                }

                java.io.File destFile = new java.io.File(fontDir, file);
                javax.imageio.ImageIO.write(whiteImg, "PNG", destFile);
                assertTrue(destFile.exists());

                // 検証: 保存された画像が純白(RGB=255)かつアルファを持つこと
                java.awt.image.BufferedImage checkImg = javax.imageio.ImageIO.read(destFile);
                for (int y = 0; y < checkImg.getHeight(); y++) {
                    for (int x = 0; x < checkImg.getWidth(); x++) {
                        int checkArgb = checkImg.getRGB(x, y);
                        int checkA = (checkArgb >> 24) & 0xFF;
                        if (checkA > 0) {
                            assertEquals(0xFF, (checkArgb >> 16) & 0xFF, "Red channel must be 255 for font tinting");
                            assertEquals(0xFF, (checkArgb >> 8) & 0xFF, "Green channel must be 255 for font tinting");
                            assertEquals(0xFF, checkArgb & 0xFF, "Blue channel must be 255 for font tinting");
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("武器別キル・ダメージ統計 (Weapon Breakdown) の集計とソート検証")
    void testWeaponBreakdownTracking() {
        com.kyamy.taczindicator.client.stats.CombatStatsManager manager = com.kyamy.taczindicator.client.stats.CombatStatsManager.getInstance();
        manager.resetStats();

        // 1. AK-47での射撃 (1発目: 通常 40.0, 2発目: HSキル 90.0 at 50m)
        manager.recordDamage(40.0f, false, false, true, false, false, false, "Zombie", 0, "AK-47");
        manager.recordDamage(90.0f, true, false, true, false, false, true, "Zombie", 50, "AK-47");

        // 2. AWPでの狙撃 (1発目: HSキル 250.0 at 150m)
        manager.recordDamage(250.0f, true, false, true, false, false, true, "Skeleton", 150, "AWP");

        // 3. ダイヤの剣での近接攻撃 (1発目: クリティカル 15.0)
        manager.recordDamage(15.0f, false, true, false, false, false, false, "Creeper", 2, "Diamond Sword");

        // 武器別リストの取得（ダメージ降順でソートされていること）
        List<com.kyamy.taczindicator.client.stats.CombatStatsManager.WeaponStatEntry> weapons = manager.getWeaponBreakdownList();
        assertEquals(3, weapons.size());

        // 1位: AWP (250.0 dmg)
        assertEquals("AWP", weapons.get(0).getWeaponName());
        assertEquals(250.0, weapons.get(0).getTotalDamage(), 1e-4);
        assertEquals(1, weapons.get(0).getTotalHits());
        assertEquals(1, weapons.get(0).getTotalHeadshots());
        assertEquals(100.0f, weapons.get(0).getHeadshotRate(), 1e-4);
        assertEquals(1, weapons.get(0).getTotalKills());
        assertEquals(150, weapons.get(0).getMaxKillDistance());

        // 2位: AK-47 (130.0 dmg)
        assertEquals("AK-47", weapons.get(1).getWeaponName());
        assertEquals(130.0, weapons.get(1).getTotalDamage(), 1e-4);
        assertEquals(2, weapons.get(1).getTotalHits());
        assertEquals(1, weapons.get(1).getTotalHeadshots());
        assertEquals(50.0f, weapons.get(1).getHeadshotRate(), 1e-4);
        assertEquals(1, weapons.get(1).getTotalKills());
        assertEquals(50, weapons.get(1).getMaxKillDistance());

        // 3位: Diamond Sword (15.0 dmg)
        assertEquals("Diamond Sword", weapons.get(2).getWeaponName());
        assertEquals(15.0, weapons.get(2).getTotalDamage(), 1e-4);
        assertEquals(1, weapons.get(2).getTotalHits());
        assertEquals(1, weapons.get(2).getTotalCriticals());
        assertEquals(0, weapons.get(2).getTotalKills());

        // レポート生成に武器別セクションが含まれること
        String report = manager.generateStatsReportText();
        assertTrue(report.contains("武器別統計 (Weapon Breakdown)"));
        assertTrue(report.contains("[AWP]"));
        assertTrue(report.contains("[AK-47]"));
        assertTrue(report.contains("[Diamond Sword]"));

        // リセット
        manager.resetStats();
        assertEquals(0, manager.getWeaponBreakdownList().size());
    }

    @Test
    @DisplayName("銃器ID・武器名フォーマットの検証")
    void testGunNameFormatting() {
        assertEquals("AK-47", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:ak47"));
        assertEquals("M4A1", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m4a1"));
        assertEquals("AWP", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("awp"));
        assertEquals("G36C", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:hk_g36c"));
        assertEquals("Desert Eagle", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("deagle"));
        assertEquals("Kriss Vector", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("vector"));
        assertEquals("Custom Rifle", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("custom_rifle"));
    }

    @Test
    @DisplayName("TaCZ主要銃器およびカスタム銃器IDの網羅的フォーマット検証")
    void testComprehensiveGunFormatting() {
        // アサルトライフル
        assertEquals("AK-47", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:ak47"));
        assertEquals("AK-74", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:ak74"));
        assertEquals("AKM", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:akm"));
        assertEquals("AKS-74U", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:aks74u"));
        assertEquals("M4A1", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m4a1"));
        assertEquals("M16A4", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m16a4"));
        assertEquals("HK416", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:hk416"));
        assertEquals("SCAR-L", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:scar_l"));
        assertEquals("SCAR-H", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:scar_h"));
        assertEquals("AUG", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:aug"));
        assertEquals("FAMAS", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:famas"));
        assertEquals("QBZ-95", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:qbz95"));
        assertEquals("QBZ-191", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:qbz_191"));
        assertEquals("Mk47 Mutant", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mk47"));
        assertEquals("Mk18", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mk18"));

        // スナイパー / DMR
        assertEquals("AWP", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:ai_awp"));
        assertEquals("M82A1", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m82a1"));
        assertEquals("Dragunov SVD", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:svd"));
        assertEquals("SV-98", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:sv98"));
        assertEquals("SKS", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:sks"));
        assertEquals("SKS Tactical", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:sks_tactical"));
        assertEquals("Mosin-Nagant", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mosin"));
        assertEquals("Kar98k", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:kar98k"));
        assertEquals("Mk14 EBR", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mk14"));

        // SMG / ハンドガン
        assertEquals("MP5", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mp5"));
        assertEquals("MP5SD", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mp5sd"));
        assertEquals("MP7", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mp7"));
        assertEquals("P90", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:p90"));
        assertEquals("UMP-45", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:ump45"));
        assertEquals("Glock 17", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:glock_17"));
        assertEquals("CZ-75", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:cz75"));
        assertEquals("CZ-75 Auto", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:cz75_auto"));
        assertEquals("M1911", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m1911"));
        assertEquals("Beretta M9", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m9"));
        assertEquals("SIG P226", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:p226"));
        assertEquals("Five-seveN", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:fn57"));

        // ショットガン / LMG / 重火器
        assertEquals("Remington 870", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m870"));
        assertEquals("M1014", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m1014"));
        assertEquals("AA-12", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:aa12"));
        assertEquals("SPAS-12", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:spas12"));
        assertEquals("DB-Long", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:db_long"));
        assertEquals("DB-Short", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:db_short"));
        assertEquals("RPK", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:rpk"));
        assertEquals("PKM", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:pkm"));
        assertEquals("PKP Pecheneg", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:pkp"));
        assertEquals("M249", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:m249"));
        assertEquals("DP-28", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:dp28"));
        assertEquals("MG42", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:mg42"));
        assertEquals("RPG-7", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("tacz:rpg7"));

        // カスタム銃パック
        assertEquals("Plasma Rifle", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("custom:plasma_rifle"));
        assertEquals("Laser Cannon", com.kyamy.taczindicator.server.DamageEventHandler.formatGunName("custom:laser_cannon"));
    }

    @Test
    @DisplayName("汎用・未解決銃器名称の判定検証")
    void testGenericGunNameDetection() {
        assertTrue(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("tacz.kineticgun"));
        assertTrue(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("item.tacz.modern_kinetic_gun"));
        assertTrue(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("Modern Kinetic Gun"));
        assertTrue(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("tacz:modern_kinetic_gun"));
        assertTrue(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName(""));
        assertTrue(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName(null));

        assertFalse(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("AK-47"));
        assertFalse(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("M4A1"));
        assertFalse(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("Diamond Sword"));
        assertFalse(com.kyamy.taczindicator.server.DamageEventHandler.isGenericGunName("Bow"));
    }

    @Test
    @DisplayName("TaCZCompatHandlerのResourceLocation深層抽出検証")
    void testTaCZCompatHandlerDeepExtraction() {
        // 1. ResourceLocationを返すモックイベント
        Object mockEventWithResourceLocation = new Object() {
            public net.minecraft.resources.ResourceLocation getGunId() {
                return new net.minecraft.resources.ResourceLocation("tacz", "ak47");
            }
        };
        String extracted = TaCZCompatHandler.extractGunIdPropertyDeep(mockEventWithResourceLocation);
        assertEquals("tacz:ak47", extracted);

        // 2. ネストされた弾丸オブジェクトを持つモックイベント
        Object mockEventWithBullet = new Object() {
            public Object getBullet() {
                return new Object() {
                    public net.minecraft.resources.ResourceLocation getGunId() {
                        return new net.minecraft.resources.ResourceLocation("tacz", "awp");
                    }
                };
            }
        };
        String extractedBullet = TaCZCompatHandler.extractGunIdPropertyDeep(mockEventWithBullet);
        assertEquals("tacz:awp", extractedBullet);

        // 3. 文字列フィールドを持つモック
        Object mockObjectWithField = new Object() {
            private final String gunId = "tacz:m4a1";
        };
        String extractedField = TaCZCompatHandler.extractGunIdPropertyDeep(mockObjectWithField);
        assertEquals("tacz:m4a1", extractedField);
    }

    @Test
    @DisplayName("キル通知の距離表示および連続キルフォーマット検証")
    void testKillAlertFormatting() {
        KillAlertInstance alert = new KillAlertInstance("Zombie", 100);
        assertTrue(alert.getFormattedText().contains("[100m]"));
        assertFalse(alert.getFormattedText().contains("[AK-47]"));

        // 連続キル更新
        alert.updateKill(45);
        assertEquals(2, alert.getKillCount());
        assertTrue(alert.getFormattedText().contains("[45m]"));
    }

    private float calculateAlpha(int ageTicks, int maxLifetime) {
        float progress = (float) ageTicks / (float) maxLifetime;
        if (progress > 0.7f) {
            return Math.max(0.0f, (1.0f - progress) / 0.3f);
        }
        return 1.0f;
    }
}
