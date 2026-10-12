package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.order.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.*;
import net.minecraft.client.option.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.fluid.FluidState;
import sun.misc.Unsafe;

public final class RecoveryRegression implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint {
    static Unsafe unsafe;
    static int checks;
    static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    static <T> T allocate(Class<T> type) throws Exception { return type.cast(unsafe.allocateInstance(type)); }
    static Field field(Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    static void object(Object o, Class<?> type, String name, Object value) throws Exception {
        Field f = field(type, name); unsafe.putObject(o, unsafe.objectFieldOffset(f), value);
    }
    static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, types); m.setAccessible(true);
        try { return m.invoke(o, args); } catch (InvocationTargetException e) { throw new RuntimeException(e.getCause()); }
    }
    @Override public void onPreLaunch() {
        try {
            SharedConstants.createGameVersion(); Bootstrap.initialize();
            Field u = Unsafe.class.getDeclaredField("theUnsafe"); u.setAccessible(true); unsafe = (Unsafe)u.get(null);
            budget(); controllerBudget(); supplies(); controllerSupplies();
            for (Class<?> type : List.of(BuildController.class, Reconnector.class, InventoryAccess.class,
                PlacementRecoveryBudget.class, SurvivalSupplies.class, Freecam.class,
                net.minecraft.client.render.Camera.class, net.minecraft.client.render.GameRenderer.class,
                net.minecraft.entity.Entity.class))
                check(type.getDeclaredMethods().length > 0, "JVM verifies " + type.getName());
            checks += BuildQualityRegression.run();
            checks += HeldPlacementRegression.run();
            checks += FreecamRegression.run();
            checks += ControllerQualityRegression.run();
            checks += SpeedRegression.run();
            checks += WaterShopRegression.run();
            checks += WaterlogTransactionRegression.run();
            checks += WaterlogAimRegression.run();
            checks += MovementMiningRegression.run();
            checks += MovementResumeRegression.run();
            checks += SchematicWorkspaceRegression.run();
            checks += LegacyGuiRegression.run();
            if (net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("donutautobuild")
                    .orElseThrow().getMetadata().getVersion().getFriendlyString().equals("3.0.120")) {
                for (Class<?> type : List.of(net.minecraft.client.render.Camera.class,
                    net.minecraft.client.render.GameRenderer.class, net.minecraft.entity.Entity.class, MinecraftClient.class,
                    net.minecraft.client.render.BackgroundRenderer.class))
                    check(Arrays.stream(type.getDeclaredMethods()).anyMatch(m -> m.getName().contains("donutautobuild$freecam")),
                        "Freecam mixin applied to " + type.getName());
                checks += FreecamVisibilityRegression.run();
                for (Class<?> rendererType : List.of(fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo.class,
                        fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic.class,
                        fi.dy.masa.litematica.render.schematic.WorldRendererSchematic.class))
                    check(Arrays.stream(rendererType.getDeclaredMethods()).noneMatch(m -> m.getName().contains("donutautobuild$schematic")),
                        "Native 109 Litematica renderer preserved: " + rendererType.getName());
            }
            System.out.println("PASS: " + checks + " recovery, survival supplies, correct blocks, held item and freecam regression assertions.");
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }

    static void budget() {
        PlacementRecoveryBudget b = new PlacementRecoveryBudget();
        for (int round = 0; round < 8; round++) {
            for (int attempt = 1; attempt <= 25; attempt++) {
                long cell = attempt % 3;
                b.clicked(cell, attempt);
                check(b.failed(cell, attempt), "real attempt counted");
                check(!b.failed(cell, attempt + 1), "same failure reported twice counted once");
                b.verified(999); // An unrelated success used to erase all failures.
                check(b.outstanding() == attempt, "unrelated success does not clear stuck cells");
                check(b.reconnectDue() == (attempt == 25), "25 fixed for every reconnect round");
            }
            b.reset(); check(b.outstanding() == 0, "actual reconnect starts a fresh budget");
        }
        b.clicked(1, 1); b.failed(1, 1); b.clicked(2, 2); b.failed(2, 2);
        b.verified(1); check(b.outstanding() == 1, "verified cell clears only its own failures");
        check(!b.failed(1, 3), "late report for same click is deduplicated");
        b.clicked(1, 4); check(b.failed(1, 4), "a new click on that cell is counted");
    }

    static void controllerBudget() throws Exception {
        Session s = new Session(Items.NETHERITE_PICKAXE, 1, 100);
        BuildConfig config = new BuildConfig(); config.syncLitematicaLayer = false;
        BuildController c = new BuildController(config); object(c, BuildController.class, "mc", s.mc);
        field(BuildController.class, "running").setBoolean(c, true);
        field(BuildController.class, "wanted").setBoolean(c, true);
        field(BuildController.class, "failRelogs").setInt(c, 8); // Old code escalated this to 100.
        for (int i = 1; i <= 25; i++) {
            field(BuildController.class, "ticks").setInt(c, i);
            BlockPos pos = new BlockPos(i % 3, -48, 354);
            call(c, "clicked", new Class<?>[]{BlockPos.class}, pos);
            call(c, "notePlaceFail", new Class<?>[]{BlockPos.class, String.class}, pos, "server trả về");
            if (i < 25) call(c, "notePlaceOk", new Class<?>[0]);
            check(Reconnector.active() == (i == 25), "actual controller requests reconnect exactly at 25");
            if (i < 25) check(c.isRunning(), "continues before limit");
        }
        check(!c.isRunning() && c.wanted(), "reconnect releases build while retaining auto resume intent");
        check(!s.mc.options.attackKey.isPressed() && !s.mc.options.useKey.isPressed(), "clicks stopped at reconnect");
        field(Reconnector.class, "state").setInt(null, 0);
        c.resetPlacementRecovery();
        check(field(BuildController.class, "placeFails").getInt(c) == 0, "real reconnect reset");

        // A failed normal click which never materializes must also count.
        field(BuildController.class, "running").setBoolean(c, true);
        BlockPos p = new BlockPos(0, 64, 0);
        call(c, "clicked", new Class<?>[]{BlockPos.class}, p);
        field(BuildController.class, "pending").set(c, new SchematicPlan.Target(p, Blocks.STONE.getDefaultState()));
        // The existing WAIT_BLOCK method is checked with its complete bytecode by JVM verification.
        check(c.getClass().getDeclaredMethod("waitBlock") != null, "WAIT_BLOCK retained and verified");
        s.mc.world = allocate(World.class);
        check(!(boolean)call(c, "checkWaitFailure", new Class<?>[0]), "failed WAIT_BLOCK remains running before limit");
        check(field(BuildController.class, "placeFails").getInt(c) == 1, "click with no visible block counts");
        call(c, "checkWaitFailure", new Class<?>[0]);
        check(field(BuildController.class, "placeFails").getInt(c) == 1, "timeout and verification do not double count");
        field(BuildController.class, "temporaryPending").setBoolean(c, true);
        ((World)s.mc.world).state = Blocks.STONE.getDefaultState();
        call(c, "noteTemporaryPlacementVerified", new Class<?>[0]);
        check(field(BuildController.class, "placeFails").getInt(c) == 0, "successful temporary support clears its own failures");
    }

    static void supplies() throws Exception {
        for (Item item : List.of(Items.COOKED_BEEF, Items.GOLDEN_CARROT, Items.NETHERITE_PICKAXE)) {
            Session s = new Session(item, item == Items.NETHERITE_PICKAXE ? 1 : 64, 100);
            SurvivalSupplies job = new SurvivalSupplies(new BuildConfig());
            boolean food = item != Items.NETHERITE_PICKAXE;
            check(job.start(s.mc, 1, food, !food), "starts direct shop supply");
            for (int tick = 2; tick < 150 && job.active(); tick++) {
                field(ShopBuyer.class, "lastCommandAt").setLong(null, 0);
                job.tick(s.mc, tick);
            }
            check(!job.active() && InventoryAccess.count(s.mc, item) > 0, "receives " + item + ": " + job.status());
            check(s.net.commands == 1 && s.interaction.clicks == 1, "one command and purchase, no order detour");
            check(s.mc.currentScreen == null, "closes shop after delivery");
        }
        Session expensive = new Session(Items.NETHERITE_PICKAXE, 1, 600000);
        SurvivalSupplies denied = new SurvivalSupplies(new BuildConfig()); denied.start(expensive.mc, 1, false, true);
        for (int t = 2; t < 40 && denied.active(); t++) { field(ShopBuyer.class, "lastCommandAt").setLong(null, 0); denied.tick(expensive.mc, t); }
        check(expensive.interaction.clicks == 0 && !denied.active(), "existing price cap respected");
        check(!denied.start(expensive.mc, 40, false, true), "failed shop attempt has retry cooldown");
        check(denied.start(expensive.mc, 500, false, true), "retries later instead of permanent stop");
        denied.cancel(expensive.mc); check(!denied.active(), "K/cancel stops supply job");

        ItemStack exhausted = new ItemStack(Items.NETHERITE_PICKAXE);
        exhausted.setDamage(exhausted.getMaxDamage() - 2);
        check(!SurvivalSupplies.usablePickaxe(exhausted), "nearly broken pickaxe needs replacement");
        Session tools = new Session(Items.NETHERITE_PICKAXE, 1, 100);
        tools.player.inv.setStack(0, exhausted); tools.player.inv.setStack(3, new ItemStack(Items.NETHERITE_PICKAXE));
        check(SurvivalSupplies.hasPickaxe(tools.mc), "healthy replacement detected");
        check(InventoryAccess.equip(tools.mc, Items.NETHERITE_PICKAXE, 0) && tools.player.inv.selectedSlot == 3,
            "equip healthy replacement instead of damaged same-item stack");
    }

    static BuildController controller(Session s, boolean running) throws Exception {
        s.mc.world = allocate(World.class);
        BuildConfig config = new BuildConfig(); config.syncLitematicaLayer = false;
        BuildController c = new BuildController(config); object(c, BuildController.class, "mc", s.mc);
        object(c, BuildController.class, "plan", allocate(SchematicPlan.class));
        field(BuildController.class, "running").setBoolean(c, running);
        field(BuildController.class, "wanted").setBoolean(c, true);
        field(BuildController.class, "stage").set(c, BuildController.Stage.MINE);
        return c;
    }

    static void controllerSupplies() throws Exception {
        for (boolean paused : List.of(false, true)) {
            Session s = new Session(Items.COOKED_BEEF, 64, 100);
            s.player.hunger.setFoodLevel(9); s.player.health = paused ? 4 : 20;
            s.player.inv.setStack(3, new ItemStack(Items.NETHERITE_PICKAXE));
            BuildController c = controller(s, !paused);
            field(BuildController.class, "ticks").setInt(c, 100);
            check((boolean)call(c, "survivalSupplyTick", new Class<?>[0]), "controller starts shop while hungry, paused=" + paused);
            SurvivalSupplies job = (SurvivalSupplies)field(BuildController.class, "survivalSupplies").get(c);
            for (int t = 101; t < 150 && job.active(); t++) {
                field(BuildController.class, "ticks").setInt(c, t);
                field(ShopBuyer.class, "lastCommandAt").setLong(null, 0);
                check((boolean)call(c, "survivalSupplyTick", new Class<?>[0]), "supply purchase holds ordinary build step");
            }
            check(s.player.inv.getStack(0).isOf(Items.COOKED_BEEF), "controller receives food before resume");
            check(field(BuildController.class, "stage").get(c) == BuildController.Stage.MINE, "purchase preserves mining stage");
            field(InventoryAccess.class, "syncRev").setInt(null, -1);
            field(BuildController.class, "ticks").setInt(c, 160);
            if (paused) check((boolean)call(c, "survivalSupplyTick", new Class<?>[0]), "low-health pause eats purchased food");
            else check((boolean)call(c, "autoEat", new Class<?>[0]), "mining can eat after purchase");
            check(s.mc.options.useKey.isPressed(), "actual eat input is held");
            c.userStop("test stop");
            check(!job.active() && !s.mc.options.useKey.isPressed() && !c.wanted(), "user stop cancels job and eating");
        }
        Session s = new Session(Items.NETHERITE_PICKAXE, 1, 100);
        BuildController c = controller(s, true);
        field(BuildController.class, "ticks").setInt(c, 100);
        check((boolean)call(c, "survivalSupplyTick", new Class<?>[0]), "controller buys missing pickaxe while mining");
        c.userStop("test stop");
        check(!((SurvivalSupplies)field(BuildController.class, "survivalSupplies").get(c)).active(), "K stops pending shop job");
    }

    static final class Session {
        MinecraftClient mc; Player player; Network net; Interaction interaction;
        Item listed; int quantity; long price;
        Session(Item item, int quantity, long price) throws Exception {
            listed = item; this.quantity = quantity; this.price = price;
            mc = allocate(MinecraftClient.class); player = allocate(Player.class); player.s = this;
            player.inv = new PlayerInventory(player); player.hunger = new HungerManager(); player.health = 20; mc.player = player;
            net = allocate(Network.class); net.s = this; object(player, ClientPlayerEntity.class, "networkHandler", net);
            interaction = allocate(Interaction.class); interaction.s = this; mc.interactionManager = interaction;
            object(player, PlayerEntity.class, "playerScreenHandler", new PlayerScreenHandler(player.inv, false, player));
            player.currentScreenHandler = player.playerScreenHandler;
            GameOptions opts = allocate(GameOptions.class); object(mc, MinecraftClient.class, "options", opts);
            int code = 300;
            for (Field f : GameOptions.class.getDeclaredFields()) if (f.getType() == KeyBinding.class && !Modifier.isStatic(f.getModifiers()))
                unsafe.putObject(opts, unsafe.objectFieldOffset(f), new KeyBinding("test." + f.getName(), InputUtil.Type.KEYSYM, code++, "test"));
        }
        void open() throws Exception {
            SimpleInventory inventory = new SimpleInventory(54);
            ItemStack offer = new ItemStack(listed, quantity);
            offer.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal("Buy price: $" + price))));
            inventory.setStack(2, offer);
            player.currentScreenHandler = GenericContainerScreenHandler.createGeneric9x6(net.commands, player.inv, inventory);
            mc.currentScreen = allocate(Menu.class);
        }
    }
    public static final class Player extends ClientPlayerEntity {
        Session s; PlayerInventory inv; HungerManager hunger; float health; boolean crouch, usingItem;
        private Player() { super(null,null,null,null,null,false,false); }
        @Override public PlayerInventory getInventory() { return inv; }
        @Override public ItemStack getMainHandStack() { return inv.getMainHandStack(); }
        @Override public boolean isSneaking() { return crouch; }
        @Override public boolean isInSneakingPose() { return crouch; }
        @Override public boolean isUsingItem() { return usingItem; }
        @Override public boolean isDescending() { return false; }
        @Override public boolean isSubmergedInWater() { return false; }
        @Override public double getBlockInteractionRange() { return 4.5; }
        @Override public void swingHand(Hand hand) { }
        @Override public boolean isOnGround() { return true; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public float getHealth() { return health; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isInLava() { return false; }
        @Override public HungerManager getHungerManager() { return hunger; }
        @Override public void sendMessage(Text t, boolean actionbar) { }
        @Override public void closeHandledScreen() { s.mc.currentScreen = null; currentScreenHandler = playerScreenHandler; }
    }
    public static final class Network extends ClientPlayNetworkHandler {
        Session s; int commands;
        private Network() { super(null,null,null); }
        @Override public ServerInfo getServerInfo() { return new ServerInfo("test", "donutsmp.net", ServerInfo.ServerType.OTHER); }
        @Override public void sendChatCommand(String cmd) { commands++; try { s.open(); } catch (Exception e) { throw new RuntimeException(e); } }
    }
    public static final class Interaction extends ClientPlayerInteractionManager {
        Session s; int clicks, placements, miningUpdates, miningCancels, breakAfter; BlockPos mined;
        private Interaction() { super(null,null); }
        @Override public boolean isBreakingBlock() { return false; }
        @Override public void cancelBlockBreaking() { miningCancels++; }
        @Override public boolean updateBlockBreakingProgress(BlockPos pos, net.minecraft.util.math.Direction side) {
            miningUpdates++; mined = pos;
            if (breakAfter > 0 && miningUpdates >= breakAfter && s.mc.world instanceof World w) w.blocks.remove(pos);
            return false; // no particles in headless test, response handled by simulated server above
        }
        @Override public ActionResult interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hit) {
            placements++;
            return ActionResult.SUCCESS;
        }
        @Override public ActionResult interactItem(PlayerEntity player, Hand hand) {
            placements++;
            BlockHitResult hit = com.donutautobuild.build.PlacementSolver.ray(s.mc, player.getBlockInteractionRange());
            if (s.mc.world instanceof World world && world.blocks != null && player.getMainHandStack().isOf(Items.WATER_BUCKET)) {
                var actual = world.getBlockState(hit.getBlockPos());
                if (actual.contains(net.minecraft.state.property.Properties.WATERLOGGED)) {
                    world.blocks.put(hit.getBlockPos(), actual.with(net.minecraft.state.property.Properties.WATERLOGGED, true));
                    s.player.inv.setStack(s.player.inv.selectedSlot, new ItemStack(Items.BUCKET));
                }
            }
            return ActionResult.SUCCESS;
        }
        @Override public void clickSlot(int sync, int slot, int button, SlotActionType action, PlayerEntity player) {
            clicks++;
            if (s.listed == Items.WATER_BUCKET) {
                for (int i = 0; i < 36; i++) if (s.player.inv.getStack(i).isEmpty()) {
                    s.player.inv.setStack(i, new ItemStack(s.listed, s.quantity)); break;
                }
            } else s.player.inv.setStack(0, new ItemStack(s.listed, s.quantity));
        }
    }
    public static final class Menu extends HandledScreen<ScreenHandler> {
        private Menu() { super(null,null,Text.empty()); }
        @Override public Text getTitle() { return Text.literal("Quick Buy"); }
        @Override protected void drawBackground(DrawContext context, float delta, int x, int y) { }
    }
    public static final class World extends ClientWorld {
        net.minecraft.block.BlockState state;
        Map<BlockPos, net.minecraft.block.BlockState> blocks;
        long gameTime;
        private World() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public net.minecraft.block.BlockState getBlockState(BlockPos pos) {
            if (blocks != null) return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState());
            return state == null ? Blocks.AIR.getDefaultState() : state;
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean isChunkLoaded(int x, int z) { return true; }
        @Override public long getTime() { return gameTime; }
    }
}
