package com.donutautobuild.build;

import com.donutautobuild.BuildConfig;
import com.donutautobuild.order.ShopBrowser;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
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
        return false;
    }

    public boolean active() { return active; }
    public String status() { return status; }

    public boolean start(MinecraftClient mc, int ticks, boolean needFood, boolean needPickaxe) {
        if (active || mc.currentScreen != null) return active;
        if (needFood && ticks >= foodRetryAt) {
            food = true;
            buyer.start(mc, STEAK, FOOD, 32, config.shopMaxPrice);
        } else if (needPickaxe && ticks >= toolRetryAt) {
            food = false;
            // 3.0.121: cúp netherite ở /shop giá ~5,1M (log 30/09 11:27:21 "You bought 1 Netherite Pickaxe for $ 5.1M"), trần chung
            // 500K làm bản 110–120 luôn từ chối "giá > trần → không mua" rồi thử lại mãi. Cúp dùng trần riêng.
            buyer.start(mc, NETHERITE_PICKAXE, Set.of(NETHERITE_PICKAXE), 1, Math.max(config.shopMaxPrice, PICKAXE_MAX_PRICE));
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
            int retry = ticks + (buyer.result() == ShopBrowser.Result.BOUGHT ? 40 : 400);
            if (food) foodRetryAt = retry; else toolRetryAt = retry;
        }
        return active;
    }

    public void cancel(MinecraftClient mc) {
        if (active) buyer.cancel(mc);
        active = false;
    }
}
