package com.donutautobuild.build;

import com.donutautobuild.BuildConfig;
import com.donutautobuild.order.ShopBrowser;
import java.util.ArrayDeque;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

/** A separate shop job so a survival purchase cannot overwrite a material order. */
public final class SurvivalSupplies {
    // 3.0.121: lấy vật phẩm qua Registries (cách của cả mod), không dùng trường Items mới
    private static Item item(String id) {
        return (Item) Registries.ITEM.get(net.minecraft.util.Identifier.tryParse("minecraft:" + id));
    }
    public static final Item STEAK = item("cooked_beef");
    public static final Item NETHERITE_PICKAXE = item("netherite_pickaxe");
    /** trần giá riêng cho cúp (giá shop DonutSMP đổi theo cung cầu) */
    public static final long PICKAXE_MAX_PRICE = 15_000_000L;
    public static final Set<Item> FOOD = Set.of(STEAK, item("cooked_porkchop"),
        item("golden_carrot"), item("cooked_chicken"), item("cooked_mutton"),
        item("cooked_salmon"), item("cooked_cod"), item("bread"), item("baked_potato"));
    private final ShopBrowser buyer;
    private final BuildConfig config;
    private boolean active;
    private boolean food;
    private int foodRetryAt;
    private int toolRetryAt;
    /** 3.0.128: lúc (tick) các lần đã mua cúp — chặn mua dồn */
    private final ArrayDeque<Integer> pickaxeBuys = new ArrayDeque<>();
    private boolean pickaxeCapped;
    private int bucketRetryAt;
    private int kind;
    private String status = "";

    public SurvivalSupplies(BuildConfig config) {
        this.config = config;
        buyer = new ShopBrowser(config);
    }

    public static boolean usablePickaxe(ItemStack stack) {
        return !stack.isEmpty()
            && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_pickaxe")
            && (!stack.isDamageable() || stack.getMaxDamage() - stack.getDamage() >= 3);
    }

    public static boolean hasPickaxe(MinecraftClient mc) {
        for (int slot = 0; slot < 36; slot++)
            if (usablePickaxe(mc.player.getInventory().getStack(slot))) return true;
        // 3.0.128: cúp ở tay trái hoặc đang nằm trên con trỏ cũng là có cúp
        return usablePickaxe(mc.player.getOffHandStack())
            || usablePickaxe(mc.player.playerScreenHandler.getCursorStack());
    }

    /** 3.0.128: cúp rơi trên đất gần đây (đồ rơi lúc chết) → đi qua là nhặt lại, không mua cái mới */
    public static boolean pickaxeDroppedNear(MinecraftClient mc, double radius) {
        if (mc.world == null || mc.player == null) return false;
        return !mc.world.getEntitiesByClass(ItemEntity.class, mc.player.getBoundingBox().expand(radius),
            e -> usablePickaxe(e.getStack())).isEmpty();
    }

    public boolean active() { return active; }
    public String status() { return status; }

    public boolean start(MinecraftClient mc, int ticks, boolean needFood, boolean needPickaxe) {
        return start(mc, ticks, needFood, needPickaxe, false);
    }

    public boolean start(MinecraftClient mc, int ticks, boolean needFood, boolean needPickaxe, boolean needBucket) {
        if (active || mc.currentScreen != null) return active;
        if (needFood && ticks >= foodRetryAt) {
            food = true;
            kind = 0;
            buyer.start(mc, STEAK, FOOD, 32, config.shopMaxPrice);
        } else if (needPickaxe && ticks >= toolRetryAt && !pickaxeCapped(ticks)) {
            food = false;
            kind = 1;
            // 3.0.121: cúp netherite ở /shop giá ~5,1M (log 30/09 11:27:21 "You bought 1 Netherite Pickaxe for $ 5.1M"), trần chung
            // 500K làm bản 110–120 luôn từ chối "giá > trần → không mua" rồi thử lại mãi. Cúp dùng trần riêng.
            buyer.start(mc, NETHERITE_PICKAXE, Set.of(NETHERITE_PICKAXE), 1, Math.max(config.shopMaxPrice, PICKAXE_MAX_PRICE));
        } else if (needBucket && ticks >= bucketRetryAt) {
            // 3.0.122: xô rỗng để múc nước khỏi khối trước khi đập (không đập khối có nước bên trong)
            food = false;
            kind = 2;
            buyer.start(mc, Items.BUCKET, Set.of(Items.BUCKET), 1, config.shopMaxPrice);
        } else return false;
        active = true;
        status = buyer.status();
        return true;
    }

    public boolean tick(MinecraftClient mc, int ticks) {
        if (!active) return false;
        buyer.tick(mc);
        status = buyer.status();
        if (!buyer.active()) {
            active = false;
            // Retry availability failures, without sending /shop every game tick.
            boolean bought = buyer.result() == ShopBrowser.Result.BOUGHT;
            int retry = ticks + (bought ? 40 : 400);
            if (kind == 1 && bought) {
                // 3.0.128 (log 27/10 02:34–02:57: 15 cúp trong 23 phút): vừa mua cúp → 5 phút không mua cúp nữa
                pickaxeBuys.addLast(ticks);
                retry = ticks + PICKAXE_GAP_TICKS;
            }
            if (kind == 0) foodRetryAt = retry; else if (kind == 1) toolRetryAt = retry; else bucketRetryAt = retry;
        }
        return active;
    }

    /** 3.0.128: sau 1 lần mua cúp phải chờ 5 phút; 1 giờ tối đa 3 cái */
    public static final int PICKAXE_GAP_TICKS = 6000;
    public static final int PICKAXE_MAX_PER_HOUR = 3;

    /** đã mua đủ PICKAXE_MAX_PER_HOUR cúp trong 1 giờ qua → không mua nữa (báo 1 lần) */
    private boolean pickaxeCapped(int ticks) {
        while (!pickaxeBuys.isEmpty() && ticks - pickaxeBuys.peekFirst() > 72000) pickaxeBuys.pollFirst();
        boolean capped = pickaxeBuys.size() >= PICKAXE_MAX_PER_HOUR;
        if (capped && !pickaxeCapped) {
            status = "Đã mua " + pickaxeBuys.size() + " cúp trong 1 giờ → không mua thêm (xem vì sao cúp bị mất: chết/rơi đồ?)";
            com.donutautobuild.AutoBuildMod.LOGGER.warn("[AutoBuild] {}", status);
        }
        pickaxeCapped = capped;
        return capped;
    }

    public void cancel(MinecraftClient mc) {
        if (active) buyer.cancel(mc);
        active = false;
    }
}
