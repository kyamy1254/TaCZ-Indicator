package com.kyamy.taczindicator.client.util;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 3Dワールド座標から2D画面GUI座標への高精度透視投影変換ユーティリティ
 */
public class ScreenProjectionUtil {

    public static class ProjectionResult {
        private final boolean visible;
        private final double screenX;
        private final double screenY;
        private final double distance;

        public ProjectionResult(boolean visible, double screenX, double screenY, double distance) {
            this.visible = visible;
            this.screenX = screenX;
            this.screenY = screenY;
            this.distance = distance;
        }

        public boolean isVisible() { return visible; }
        public double getScreenX() { return screenX; }
        public double getScreenY() { return screenY; }
        public double getDistance() { return distance; }
    }

    /**
     * 3Dワールド座標をMinecraft GUI画面座標（ピクセル）に投影
     * JOMLのビュー投影行列演算により全方位（360度・全Yaw/Pitch）で正確に計算
     */
    public static ProjectionResult projectToScreen(double worldX, double worldY, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return new ProjectionResult(false, 0, 0, 0);
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Window window = mc.getWindow();

        double relX = worldX - cameraPos.x;
        double relY = worldY - cameraPos.y;
        double relZ = worldZ - cameraPos.z;
        double distance = Math.sqrt(relX * relX + relY * relY + relZ * relZ);

        if (distance < 0.05) {
            return new ProjectionResult(false, 0, 0, distance);
        }

        int guiWidth = window.getGuiScaledWidth();
        int guiHeight = window.getGuiScaledHeight();
        if (guiWidth <= 0 || guiHeight <= 0) {
            return new ProjectionResult(false, 0, 0, distance);
        }

        // FOV取得（設定FOV + ズーム/エイム効果）
        double fovDegrees = mc.options.fov().get();
        double fovRad = Math.toRadians(fovDegrees);

        // 透視投影行列とビュー行列の構築
        Matrix4f projMatrix = new Matrix4f().perspective(
                (float) fovRad,
                (float) guiWidth / (float) guiHeight,
                0.05f,
                1000.0f
        );

        Matrix4f viewMatrix = new Matrix4f().rotation(camera.rotation());
        Matrix4f viewProjMatrix = new Matrix4f(projMatrix).mul(viewMatrix);

        // 4次元クリップ座標への変換
        Vector4f clipPos = new Vector4f((float) relX, (float) relY, (float) relZ, 1.0f);
        viewProjMatrix.transform(clipPos);

        // クリップ空間W値が0以下 = カメラの背面にあるため非表示
        if (clipPos.w <= 0.05f) {
            return new ProjectionResult(false, 0, 0, distance);
        }

        // 正規化デバイス座標 (NDC: -1.0 ~ +1.0)
        float ndcX = clipPos.x / clipPos.w;
        float ndcY = clipPos.y / clipPos.w;

        // 画面外チェック（画面端から一定マージン外なら非表示）
        if (Math.abs(ndcX) > 1.8f || Math.abs(ndcY) > 1.8f) {
            return new ProjectionResult(false, 0, 0, distance);
        }

        // 2D GUI画面座標へマッピング（NDC: Y上向き -> GUI: Y下向き）
        double screenX = (guiWidth / 2.0) * (1.0 + ndcX);
        double screenY = (guiHeight / 2.0) * (1.0 - ndcY);

        return new ProjectionResult(true, screenX, screenY, distance);
    }
}
