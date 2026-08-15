package com.kyamy.taczindicator.client.util;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 3Dワールド座標から2D画面GUI座標への数学的投影ユーティリティ
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
     * 3Dワールド座標をMinecraft GUI座標系（ピクセル）に投影
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

        // カメラ回転の逆変換（共役クォータニオン）を適用してカメラローカル空間へ変換
        Vector3f viewPos = new Vector3f((float) relX, (float) relY, (float) relZ);
        Quaternionf invRot = new Quaternionf(camera.rotation()).conjugate();
        viewPos.rotate(invRot);

        // Minecraftのカメラ空間ではカメラ前方が -Z 方向
        float zDepth = -viewPos.z;
        if (zDepth <= 0.05f) {
            // カメラ後方にある場合は非表示
            return new ProjectionResult(false, 0, 0, distance);
        }

        // FOVと画面比率の計算
        double fovDegrees = mc.options.fov().get();
        double fovRad = Math.toRadians(fovDegrees);
        double tanHalfFov = Math.tan(fovRad / 2.0);

        int guiWidth = window.getGuiScaledWidth();
        int guiHeight = window.getGuiScaledHeight();
        double aspectRatio = (double) guiWidth / (double) guiHeight;

        // 正規化デバイス座標 (NDC) 計算
        double ndcX = viewPos.x / (zDepth * tanHalfFov * aspectRatio);
        double ndcY = viewPos.y / (zDepth * tanHalfFov);

        // GUI画面座標へマッピング（Y軸は上がマイナス）
        double screenX = (guiWidth / 2.0) * (1.0 + ndcX);
        double screenY = (guiHeight / 2.0) * (1.0 - ndcY);

        return new ProjectionResult(true, screenX, screenY, distance);
    }
}
