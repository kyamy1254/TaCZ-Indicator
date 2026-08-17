package com.kyamy.taczindicator.server;

import com.kyamy.taczindicator.TaCZIndicatorMod;
import com.kyamy.taczindicator.network.DamageIndicatorPacket;
import com.kyamy.taczindicator.network.ModMessages;
import com.kyamy.taczindicator.network.ServerHandshakePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

/**
 * サーバー側でダメージイベントおよびキルイベントを高精度に検知し、攻撃元プレイヤーへパケット送信するハンドラ
 * 環境ダメージ誤判定の完全防止、通常殴りヘッドショット除外、厳密頭部判定(y-0.25〜+0.25)、距離計測を完備
 */
public class DamageEventHandler {

    /**
     * プレイヤーがサーバーに参加した際に同期ハンドシェイクパケットを即時送信
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModMessages.sendToPlayer(new ServerHandshakePacket(), player);
            TaCZIndicatorMod.LOGGER.info("[TaCZ Indicator] Sent server handshake packet to player: {}", player.getName().getString());
        }
    }

    /**
     * 最終計算後のダメージイベントのみを購読 (重複・不正確な事前イベントは除外)
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        handleDamage(event.getEntity(), event.getSource(), event.getAmount());
    }

    /**
     * 確実なキル確定イベント (LivingDeathEvent)
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide() || event.getSource() == null) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        Entity directEntity = event.getSource().getDirectEntity();

        ServerPlayer attackingPlayer = resolvePlayerAttacker(victim, event.getSource(), attacker, directEntity);
        if (attackingPlayer == null) {
            return;
        }

        Vec3 eyePos = victim.getEyePosition();
        String victimName = victim.getDisplayName().getString();
        int distanceMeters = (int) Math.round(attackingPlayer.position().distanceTo(victim.position()));

        // TaCZヒット情報またはダメージソースからの判定
        TaCZCompatHandler.TaCZHitRecord taczHit = TaCZCompatHandler.getRecentHit(victim.getId());
        boolean isTaCZ = (taczHit != null) || isTaCZDamage(event.getSource(), directEntity);
        boolean isHeadshot = (taczHit != null && isHeadshotFromRecord(taczHit));
        boolean isCritical = isCriticalDamage(attackingPlayer, event.getSource(), directEntity, isTaCZ);
        boolean isArmorPiercing = (taczHit != null && taczHit.isArmorPiercing()) || isArmorPiercingDamage(event.getSource(), directEntity);
        String weaponName = resolveWeaponName(attackingPlayer, taczHit, event.getSource(), directEntity);

        DamageIndicatorPacket packet = new DamageIndicatorPacket(
                victim.getId(),
                eyePos.x, eyePos.y, eyePos.z,
                0.0f,
                isHeadshot,
                isCritical,
                isTaCZ,
                isArmorPiercing,
                false,
                true,
                victimName,
                distanceMeters,
                weaponName
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
        TaCZIndicatorMod.LOGGER.debug("Sent kill packet: victim={}, player={}, dist={}m, weapon={}, HS={}, Crit={}, AP={}, TaCZ={}",
                victimName, attackingPlayer.getName().getString(), distanceMeters, weaponName, isHeadshot, isCritical, isArmorPiercing, isTaCZ);
    }

    private static void handleDamage(LivingEntity victim, DamageSource source, float damage) {
        if (victim == null || victim.level().isClientSide() || source == null || damage <= 0.001f) {
            return;
        }

        Entity attacker = source.getEntity();
        Entity directEntity = source.getDirectEntity();

        ServerPlayer attackingPlayer = resolvePlayerAttacker(victim, source, attacker, directEntity);
        if (attackingPlayer == null) {
            return;
        }

        // 1. TaCZキャッシュヒット情報の取得
        TaCZCompatHandler.TaCZHitRecord taczHit = TaCZCompatHandler.getRecentHit(victim.getId());

        // 2. TaCZダメージ判定およびヘッドショット・クリティカル判定
        boolean isTaCZ = (taczHit != null) || isTaCZDamage(source, directEntity);

        // 通常殴り（非銃器・近接直接攻撃）はヘッドショット判定から除外
        boolean isProjectileOrGun = isTaCZ || (directEntity instanceof Projectile) || (directEntity != null && directEntity != attackingPlayer);
        boolean isHeadshot = isProjectileOrGun && ((taczHit != null && isHeadshotFromRecord(taczHit)) || isHeadshotDamage(victim, source, directEntity, attackingPlayer));

        boolean isCritical = isCriticalDamage(attackingPlayer, source, directEntity, isTaCZ);
        boolean isArmorPiercing = (taczHit != null && taczHit.isArmorPiercing()) || isArmorPiercingDamage(source, directEntity);
        boolean hitArmor = victim.getArmorValue() > 0;
        String victimName = victim.getDisplayName().getString();
        int distanceMeters = (int) Math.round(attackingPlayer.position().distanceTo(victim.position()));
        String weaponName = resolveWeaponName(attackingPlayer, taczHit, source, directEntity);

        Vec3 eyePos = victim.getEyePosition();
        double posX = eyePos.x;
        double posY = eyePos.y;
        double posZ = eyePos.z;

        DamageIndicatorPacket packet = new DamageIndicatorPacket(
                victim.getId(),
                posX, posY, posZ,
                damage,
                isHeadshot,
                isCritical,
                isTaCZ,
                isArmorPiercing,
                hitArmor,
                false,
                victimName,
                distanceMeters,
                weaponName
        );

        ModMessages.sendToPlayer(packet, attackingPlayer);
        TaCZIndicatorMod.LOGGER.debug("Sent damage packet: victim={}, dmg={}, weapon={}, HS={}, Crit={}, AP={}, TaCZ={}, dist={}m, player={}",
                victim.getId(), damage, weaponName, isHeadshot, isCritical, isArmorPiercing, isTaCZ, distanceMeters, attackingPlayer.getName().getString());
    }

    public static String resolveWeaponName(net.minecraft.world.entity.player.Player attackingPlayer, TaCZCompatHandler.TaCZHitRecord taczHit, DamageSource source, Entity directEntity) {
        // 1. TaCZヒットキャッシュからの解決
        if (taczHit != null && taczHit.gunId() != null && !taczHit.gunId().isBlank()) {
            String formatted = formatGunName(taczHit.gunId());
            if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                return formatted;
            }
        }

        // 2. 直撃弾丸エンティティ (EntityKineticBullet 等) からの深層抽出
        if (directEntity != null && directEntity != attackingPlayer) {
            String directGunId = TaCZCompatHandler.extractGunIdPropertyDeep(directEntity);
            if (!directGunId.isEmpty()) {
                String formatted = formatGunName(directGunId);
                if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                    return formatted;
                }
            }
            if (directEntity.getPersistentData() != null) {
                net.minecraft.nbt.CompoundTag tag = directEntity.getPersistentData();
                for (String key : tag.getAllKeys()) {
                    if (key.equalsIgnoreCase("GunId") || key.equalsIgnoreCase("gun_id") || key.equalsIgnoreCase("gun")) {
                        String val = tag.getString(key);
                        if (!val.isBlank()) {
                            String formatted = formatGunName(val);
                            if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                                return formatted;
                            }
                        }
                    }
                }
            }
        }

        // 3. ダメージソースからの抽出
        if (source != null) {
            String srcGunId = TaCZCompatHandler.extractGunIdPropertyDeep(source);
            if (!srcGunId.isEmpty()) {
                String formatted = formatGunName(srcGunId);
                if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                    return formatted;
                }
            }
        }

        // 4. 攻撃プレイヤーの手持ちアイテム (メインハンド / オフハンド) の詳細走査
        if (attackingPlayer != null) {
            ItemStack mainHand = attackingPlayer.getMainHandItem();
            if (mainHand != null && !mainHand.isEmpty()) {
                String resolved = resolveWeaponNameFromItemStack(mainHand);
                if (!resolved.isBlank() && !isGenericGunName(resolved)) {
                    return resolved;
                }
            }

            ItemStack offHand = attackingPlayer.getOffhandItem();
            if (offHand != null && !offHand.isEmpty()) {
                String resolvedOff = resolveWeaponNameFromItemStack(offHand);
                if (!resolvedOff.isBlank() && !isGenericGunName(resolvedOff)) {
                    return resolvedOff;
                }
            }

            // フォールバック: 表示名
            if (mainHand != null && !mainHand.isEmpty()) {
                String hoverName = mainHand.getHoverName().getString();
                if (hoverName != null && !hoverName.isBlank()) {
                    if (isGenericGunName(hoverName)) {
                        return "TaCZ Gun";
                    }
                    return hoverName;
                }
            }
        }

        // 5. 発射物・召喚エンティティの表示名
        if (directEntity != null && directEntity != attackingPlayer) {
            return directEntity.getDisplayName().getString();
        }

        return "Melee";
    }

    /**
     * ItemStack から武器名・銃器名を高精度に解決
     */
    public static String resolveWeaponNameFromItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        // 1. TaCZ IGun API による動的抽出 (リフレクション)
        try {
            Class<?> iGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            Method getIGunOrNull = null;
            for (Method m : iGunClass.getMethods()) {
                if (m.getName().equals("getIGunOrNull") && m.getParameterCount() == 1) {
                    getIGunOrNull = m;
                    break;
                }
            }
            if (getIGunOrNull != null) {
                Object iGun = getIGunOrNull.invoke(null, stack);
                if (iGun != null) {
                    for (Method m : iGun.getClass().getMethods()) {
                        if (m.getName().equals("getGunId") && m.getParameterCount() == 1) {
                            Object res = m.invoke(iGun, stack);
                            if (res != null) {
                                String formatted = formatGunName(res.toString());
                                if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                                    return formatted;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. ItemStack.getItem() から直接 getGunId(stack) を呼び出し
        try {
            Method m = stack.getItem().getClass().getMethod("getGunId", ItemStack.class);
            Object res = m.invoke(stack.getItem(), stack);
            if (res != null) {
                String formatted = formatGunName(res.toString());
                if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                    return formatted;
                }
            }
        } catch (Throwable ignored) {}

        // 3. ItemStack の NBT タグ深層探索
        if (stack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag != null) {
                String[] candidateKeys = {"GunId", "gun_id", "Gun", "gun", "WeaponId", "weapon_id", "GunIdentifier", "GunIndex", "GunData"};
                for (String key : candidateKeys) {
                    if (tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING)) {
                        String val = tag.getString(key);
                        if (!val.isBlank()) {
                            String formatted = formatGunName(val);
                            if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                                return formatted;
                            }
                        }
                    } else if (tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                        net.minecraft.nbt.CompoundTag nested = tag.getCompound(key);
                        for (String nKey : candidateKeys) {
                            if (nested.contains(nKey, net.minecraft.nbt.Tag.TAG_STRING)) {
                                String val = nested.getString(nKey);
                                if (!val.isBlank()) {
                                    String formatted = formatGunName(val);
                                    if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                                        return formatted;
                                    }
                                }
                            }
                        }
                    }
                }

                // 全キー走査
                for (String key : tag.getAllKeys()) {
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (lower.contains("gunid") || lower.contains("gun_id") || (lower.contains("gun") && !lower.contains("data") && !lower.contains("tag") && !lower.contains("attachment"))) {
                        if (tag.contains(key, net.minecraft.nbt.Tag.TAG_STRING)) {
                            String val = tag.getString(key);
                            if (!val.isBlank()) {
                                String formatted = formatGunName(val);
                                if (!formatted.isEmpty() && !isGenericGunName(formatted)) {
                                    return formatted;
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. アイテムの表示名 (HoverName)
        String hoverName = stack.getHoverName().getString();
        if (hoverName != null && !hoverName.isBlank()) {
            if (!isGenericGunName(hoverName)) {
                return hoverName;
            }
        }

        // 5. レジストリ名からの抽出フォールバック
        try {
            net.minecraft.resources.ResourceLocation regName = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (regName != null) {
                String path = regName.getPath();
                if (!path.equalsIgnoreCase("modern_kinetic_gun") && !path.equalsIgnoreCase("kineticgun")) {
                    return formatGunName(regName.toString());
                }
            }
        } catch (Throwable ignored) {}

        return (hoverName != null && !hoverName.isBlank() && !isGenericGunName(hoverName)) ? hoverName : "TaCZ Gun";
    }

    /**
     * 汎用・未解決の銃器名称かどうか判定
     */
    public static boolean isGenericGunName(String name) {
        if (name == null || name.isBlank()) return true;
        String lower = name.toLowerCase(Locale.ROOT).trim();
        return lower.equals("tacz.kineticgun") ||
                lower.equals("item.tacz.modern_kinetic_gun") ||
                lower.equals("modern_kinetic_gun") ||
                lower.equals("kineticgun") ||
                lower.equals("tacz:modern_kinetic_gun") ||
                lower.equals("modern kinetic gun") ||
                lower.equals("item.tacz.modern_kinetic_gun.desc");
    }

    public static String formatGunName(String gunId) {
        if (gunId == null || gunId.isBlank()) return "";
        if (isGenericGunName(gunId)) return "TaCZ Gun";

        if (gunId.contains(":")) {
            gunId = gunId.substring(gunId.indexOf(':') + 1);
        }
        gunId = gunId.replace('_', ' ').replace('-', ' ').trim();
        String lower = gunId.toLowerCase(Locale.ROOT);

        // 既知の代表的銃器辞書マッピング
        // アサルトライフル / カービン
        if (lower.equals("ak47") || lower.equals("ak 47")) return "AK-47";
        if (lower.equals("ak74") || lower.equals("ak 74")) return "AK-74";
        if (lower.equals("akm")) return "AKM";
        if (lower.equals("aks74u") || lower.equals("aks 74u")) return "AKS-74U";
        if (lower.equals("m4a1") || lower.equals("m4 a1") || lower.equals("m4")) return "M4A1";
        if (lower.equals("m16a4") || lower.equals("m16 a4")) return "M16A4";
        if (lower.equals("m16a1") || lower.equals("m16 a1")) return "M16A1";
        if (lower.equals("m16")) return "M16";
        if (lower.equals("hk g36c") || lower.equals("g36c") || lower.equals("g36")) return "G36C";
        if (lower.equals("hk416") || lower.equals("hk 416")) return "HK416";
        if (lower.equals("hk417") || lower.equals("hk 417")) return "HK417";
        if (lower.equals("scar l") || lower.equals("scarl") || lower.equals("fn scar l") || lower.equals("scar")) return "SCAR-L";
        if (lower.equals("scar h") || lower.equals("scarh") || lower.equals("fn scar h")) return "SCAR-H";
        if (lower.equals("aug") || lower.equals("aug a3") || lower.equals("steyr aug")) return "AUG";
        if (lower.equals("famas") || lower.equals("famas f1")) return "FAMAS";
        if (lower.equals("fal") || lower.equals("fn fal")) return "FN FAL";
        if (lower.equals("type81") || lower.equals("type 81")) return "Type 81";
        if (lower.equals("qbz95") || lower.equals("qbz 95")) return "QBZ-95";
        if (lower.equals("qbz191") || lower.equals("qbz 191")) return "QBZ-191";
        if (lower.equals("mk47") || lower.equals("mk 47") || lower.equals("mk47 mutant")) return "Mk47 Mutant";
        if (lower.equals("mk18") || lower.equals("mk 18")) return "Mk18";

        // スナイパー / DMR
        if (lower.equals("awp") || lower.equals("ai awp")) return "AWP";
        if (lower.equals("awm") || lower.equals("ai awm")) return "AWM";
        if (lower.equals("m82a1") || lower.equals("m82") || lower.equals("barrett m82")) return "M82A1";
        if (lower.equals("m24") || lower.equals("m24 sws")) return "M24";
        if (lower.equals("svd") || lower.equals("dragunov") || lower.equals("dragunov svd")) return "Dragunov SVD";
        if (lower.equals("sv98") || lower.equals("sv 98")) return "SV-98";
        if (lower.equals("sks")) return "SKS";
        if (lower.equals("sks tactical") || lower.equals("sks tac")) return "SKS Tactical";
        if (lower.equals("mosin") || lower.equals("mosin nagant")) return "Mosin-Nagant";
        if (lower.equals("kar98k") || lower.equals("k98k") || lower.equals("kar98")) return "Kar98k";
        if (lower.equals("mk14") || lower.equals("mk14 ebr") || lower.equals("m14")) return "Mk14 EBR";
        if (lower.equals("m110") || lower.equals("m110 sass")) return "M110";

        // サブマシンガン (SMG)
        if (lower.equals("mp5") || lower.equals("hk mp5")) return "MP5";
        if (lower.equals("mp5sd") || lower.equals("mp5 sd") || lower.equals("hk mp5sd")) return "MP5SD";
        if (lower.equals("mp7") || lower.equals("hk mp7")) return "MP7";
        if (lower.equals("mp9")) return "MP9";
        if (lower.equals("vector") || lower.equals("kriss vector")) return "Kriss Vector";
        if (lower.equals("p90") || lower.equals("fn p90")) return "P90";
        if (lower.equals("ump45") || lower.equals("ump 45") || lower.equals("hk ump45")) return "UMP-45";
        if (lower.equals("ump9") || lower.equals("ump 9") || lower.equals("hk ump9")) return "UMP-9";
        if (lower.equals("pp19") || lower.equals("pp 19") || lower.equals("bizon")) return "PP-19 Bizon";
        if (lower.equals("mac10") || lower.equals("mac 10")) return "MAC-10";
        if (lower.equals("uzi")) return "UZI";

        // ハンドガン (Pistol)
        if (lower.equals("deagle") || lower.equals("desert eagle")) return "Desert Eagle";
        if (lower.equals("glock 17") || lower.equals("glock17") || lower.equals("glock")) return "Glock 17";
        if (lower.equals("glock 18") || lower.equals("glock18")) return "Glock 18";
        if (lower.equals("cz75") || lower.equals("cz 75")) return "CZ-75";
        if (lower.equals("cz75 auto") || lower.equals("cz75auto")) return "CZ-75 Auto";
        if (lower.equals("m1911") || lower.equals("colt 1911") || lower.equals("1911")) return "M1911";
        if (lower.equals("m9") || lower.equals("beretta m9") || lower.equals("beretta 92fs") || lower.equals("92fs")) return "Beretta M9";
        if (lower.equals("usp") || lower.equals("hk usp")) return "USP";
        if (lower.equals("p226") || lower.equals("sig p226") || lower.equals("sig sauer p226")) return "SIG P226";
        if (lower.equals("fn57") || lower.equals("five seven") || lower.equals("five_seven")) return "Five-seveN";
        if (lower.equals("tt33") || lower.equals("tt 33") || lower.equals("tokarev")) return "TT-33";

        // ショットガン (Shotgun)
        if (lower.equals("m870") || lower.equals("remington 870") || lower.equals("remington870")) return "Remington 870";
        if (lower.equals("m1014") || lower.equals("benelli m1014") || lower.equals("benelli m4")) return "M1014";
        if (lower.equals("aa12") || lower.equals("aa 12")) return "AA-12";
        if (lower.equals("spas12") || lower.equals("spas 12")) return "SPAS-12";
        if (lower.equals("db long") || lower.equals("dblong")) return "DB-Long";
        if (lower.equals("db short") || lower.equals("dbshort")) return "DB-Short";
        if (lower.equals("saiga12") || lower.equals("saiga 12")) return "Saiga-12";

        // マシンガン (LMG)
        if (lower.equals("rpk") || lower.equals("rpk 74") || lower.equals("rpk74")) return "RPK";
        if (lower.equals("pkm")) return "PKM";
        if (lower.equals("pkp") || lower.equals("pkp pecheneg")) return "PKP Pecheneg";
        if (lower.equals("m249") || lower.equals("m249 saw")) return "M249";
        if (lower.equals("dp28") || lower.equals("dp 28")) return "DP-28";
        if (lower.equals("mg42") || lower.equals("mg 42")) return "MG42";
        if (lower.equals("mg3") || lower.equals("mg 3")) return "MG3";

        // 重火器 (Heavy)
        if (lower.equals("rpg7") || lower.equals("rpg 7") || lower.equals("rpg")) return "RPG-7";
        if (lower.equals("m79")) return "M79";

        // 一般的な単語フォーマット
        String[] parts = gunId.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                String pLower = part.toLowerCase(Locale.ROOT);
                if (pLower.equals("hk") || pLower.equals("fn") || pLower.equals("cz") || pLower.equals("ak") ||
                        pLower.equals("ar") || pLower.equals("smg") || pLower.equals("lmg") || pLower.equals("dmr") ||
                        pLower.equals("rpg") || pLower.equals("sv") || pLower.equals("db") || pLower.equals("aa") ||
                        pLower.equals("spas") || pLower.equals("mp") || pLower.equals("mk") || pLower.equals("ebr")) {
                    sb.append(part.toUpperCase(Locale.ROOT)).append(" ");
                } else if (pLower.matches("^[a-z]+[0-9]+[a-z0-9]*$")) {
                    sb.append(formatAlphanumericWord(part)).append(" ");
                } else {
                    sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
                }
            }
        }
        return sb.toString().trim();
    }

    private static String formatAlphanumericWord(String word) {
        if (word == null || word.isEmpty()) return "";
        int firstDigit = -1;
        for (int i = 0; i < word.length(); i++) {
            if (Character.isDigit(word.charAt(i))) {
                firstDigit = i;
                break;
            }
        }
        if (firstDigit > 0) {
            String prefix = word.substring(0, firstDigit).toUpperCase(Locale.ROOT);
            String suffix = word.substring(firstDigit).toUpperCase(Locale.ROOT);
            if (prefix.length() >= 2) {
                return prefix + "-" + suffix;
            } else {
                return prefix + suffix;
            }
        }
        return word.toUpperCase(Locale.ROOT);
    }

    private static boolean isHeadshotFromRecord(TaCZCompatHandler.TaCZHitRecord record) {
        return record.isHeadshot() || record.headshotMultiplier() > 1.05f;
    }

    private static boolean isArmorPiercingDamage(DamageSource source, Entity directEntity) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            return true;
        }
        if (directEntity != null) {
            if (TaCZCompatHandler.extractBooleanPropertyDeep(directEntity, "armorpiercing", "armor_piercing", "armorignore", "armor_ignore", "piercing", "bypassesarmor")) {
                return true;
            }
        }
        if (TaCZCompatHandler.extractBooleanPropertyDeep(source, "armorpiercing", "armor_piercing", "armorignore", "armor_ignore", "piercing", "bypassesarmor")) {
            return true;
        }
        return false;
    }

    /**
     * TaCZ銃器ダメージかどうかの判定
     */
    private static boolean isTaCZDamage(DamageSource source, Entity directEntity) {
        String msgId = source.getMsgId();
        if (msgId != null) {
            String lower = msgId.toLowerCase(Locale.ROOT);
            if (lower.contains("tacz") || lower.contains("bullet") || lower.contains("gun") || lower.contains("kinetic")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            ResourceKey<DamageType> key = source.typeHolder().unwrapKey().get();
            String location = key.location().toString().toLowerCase(Locale.ROOT);
            if (location.contains("tacz") || location.contains("bullet") || location.contains("gun") || location.contains("kinetic")) {
                return true;
            }
        }

        if (directEntity != null) {
            String directClassName = directEntity.getClass().getName().toLowerCase(Locale.ROOT);
            if (directClassName.contains("tacz") || directClassName.contains("bullet") || directClassName.contains("gun") || directClassName.contains("kinetic")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 多層高精度ヘッドショット判定
     */
    public static boolean isHeadshotDamage(LivingEntity victim, DamageSource source, Entity directEntity, ServerPlayer attackingPlayer) {
        if (victim == null) return false;

        // 1. DamageSourceのMsgIdおよびDamageType判定
        String msgId = source.getMsgId();
        if (msgId != null) {
            String lower = msgId.toLowerCase(Locale.ROOT);
            if (lower.contains("headshot") || lower.contains("head_shot")) {
                return true;
            }
        }

        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            String location = source.typeHolder().unwrapKey().get().location().toString().toLowerCase(Locale.ROOT);
            if (location.contains("headshot") || location.contains("head_shot")) {
                return true;
            }
        }

        // 2. DirectEntity (弾丸など) のヘッドショットプロパティ判定
        if (directEntity != null) {
            if (TaCZCompatHandler.extractHeadshotProperty(directEntity)) {
                return true;
            }
        }

        // 3. DamageSourceのリフレクション判定
        if (TaCZCompatHandler.extractHeadshotProperty(source)) {
            return true;
        }

        // 4. 幾何学的3D Ray-Box交差レイキャスト判定 (厳密頭部当たり判定)
        if (checkGeometricHeadshot(victim, source, directEntity, attackingPlayer)) {
            return true;
        }

        return false;
    }

    /**
     * 攻撃者視線ベクトルおよび弾丸軌道を用いた高精度3D Ray-AABBヘッドショット判定
     */
    public static boolean checkGeometricHeadshot(LivingEntity victim, DamageSource source, Entity directEntity, ServerPlayer attackingPlayer) {
        if (victim == null) return false;

        // 対象エンティティの頭部バウンディングボックス (AABB) の厳密構築 (x-0.25 < y < x+0.25)
        AABB headBox = calculateEntityHeadBox(victim);

        // 1. 攻撃元プレイヤーの3D視線レイキャスト判定
        if (attackingPlayer != null) {
            Vec3 eyePos = attackingPlayer.getEyePosition(1.0f);
            Vec3 lookVec = attackingPlayer.getViewVector(1.0f).normalize();
            Vec3 rayEnd = eyePos.add(lookVec.scale(300.0));

            Optional<Vec3> headHit = headBox.clip(eyePos, rayEnd);
            if (headHit.isPresent()) {
                TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Headshot confirmed by player eye raycast: victim={}", victim.getId());
                return true;
            }
        }

        // 2. 弾丸（DirectEntity）の移動ベクトル軌道交差判定
        if (directEntity != null) {
            Vec3 bulletPos = directEntity.position();
            Vec3 bulletVel = directEntity.getDeltaMovement();
            double speed = bulletVel.length();
            double lookback = Math.max(1.0, speed * 2.0);

            Vec3 rayStart = bulletPos.subtract(bulletVel.normalize().scale(lookback));
            Vec3 rayEnd = bulletPos.add(bulletVel.normalize().scale(lookback));

            Optional<Vec3> bulletHit = headBox.clip(rayStart, rayEnd);
            if (bulletHit.isPresent()) {
                TaCZIndicatorMod.LOGGER.debug("[TaCZ Indicator] Headshot confirmed by bullet trajectory: victim={}", victim.getId());
                return true;
            }

            // 弾丸の現在位置自体が頭部領域内にある場合
            if (headBox.contains(bulletPos)) {
                return true;
            }
        }

        // 3. エンダードラゴン等マルチパートエンティティ対応
        if (victim instanceof EnderDragon dragon) {
            if (dragon.head != null) {
                AABB dragonHeadBox = dragon.head.getBoundingBox();
                if (attackingPlayer != null) {
                    Vec3 eyePos = attackingPlayer.getEyePosition(1.0f);
                    Vec3 lookVec = attackingPlayer.getViewVector(1.0f).normalize();
                    if (dragonHeadBox.clip(eyePos, eyePos.add(lookVec.scale(300.0))).isPresent()) {
                        return true;
                    }
                }
            }
        }

        // 4. 着弾座標が直接渡されている場合の検証
        if (source != null && source.getSourcePosition() != null) {
            Vec3 srcPos = source.getSourcePosition();
            if (headBox.contains(srcPos)) {
                return true;
            }
        }

        return false;
    }

    /**
     * エンティティの頭部当たり判定AABBを算出
     * 仕様: 高さ x-0.25 < y < x+0.25 (x = 目の高さ, イコールなしの厳密不等式), 平面はモブの水平AABBと同一
     */
    public static AABB calculateEntityHeadBox(LivingEntity victim) {
        double eyeY = victim.getEyeY();

        // 厳密な不等式 (x-0.25 < y < x+0.25)
        double headMinY = eyeY - 0.25 + 0.0001;
        double headMaxY = eyeY + 0.25 - 0.0001;

        // 水平面 (X, Z) はモブ本来の当たり判定と完全に同一
        AABB mobBox = victim.getBoundingBox();

        return new AABB(
                mobBox.minX,
                headMinY,
                mobBox.minZ,
                mobBox.maxX,
                headMaxY,
                mobBox.maxZ
        );
    }

    /**
     * クリティカル判定
     */
    private static boolean isCriticalDamage(ServerPlayer player, DamageSource source, Entity directEntity, boolean isTaCZ) {
        if (directEntity != null) {
            if (TaCZCompatHandler.extractBooleanPropertyDeep(directEntity, "crit", "critical")) {
                return true;
            }
        }

        // バニラ近接クリティカル
        if (!isTaCZ && player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() && !player.isInWater()) {
            return true;
        }

        return false;
    }

    /**
     * DamageSource / 直接エンティティから攻撃者 ServerPlayer を厳密に解決
     * 環境ダメージ（炎・落下・窒息等）の誤加算を防ぐため、プレイヤー本人の直接/射撃原因のみに限定
     */
    private static ServerPlayer resolvePlayerAttacker(LivingEntity victim, DamageSource source, Entity attacker, Entity directEntity) {
        // 環境ダメージソース（炎、溶岩、落下、窒息、サボテン等）の除外
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.DRY_OUT) || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.HOT_FLOOR)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.GENERIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.MAGIC)) {
            // アタッカーや弾丸が明示的に存在しない環境ダメージはプレイヤー起因とみなさない
            if (attacker == null && directEntity == null) {
                return null;
            }
        }

        if (attacker instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (directEntity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        if (directEntity instanceof TraceableEntity traceable) {
            if (traceable.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }
        if (attacker instanceof TraceableEntity traceable) {
            if (traceable.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }

        if (directEntity instanceof Projectile projectile) {
            if (projectile.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }
        if (attacker instanceof Projectile projectile) {
            if (projectile.getOwner() instanceof ServerPlayer serverPlayer) {
                return serverPlayer;
            }
        }

        if (directEntity != null) {
            ServerPlayer shooter = tryExtractPlayer(directEntity);
            if (shooter != null) {
                return shooter;
            }
        }
        if (attacker != null) {
            ServerPlayer shooter = tryExtractPlayer(attacker);
            if (shooter != null) {
                return shooter;
            }
        }

        // ※ victim.getLastHurtByMob() は環境ダメージ（炎・毒等）の誤加算を引き起こすため完全除外

        return null;
    }

    private static ServerPlayer tryExtractPlayer(Entity entity) {
        for (String methodName : new String[]{"getShooter", "getOwner", "getShootingEntity", "getThrower"}) {
            try {
                Method method = entity.getClass().getMethod(methodName);
                Object result = method.invoke(entity);
                if (result instanceof ServerPlayer sp) {
                    return sp;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
