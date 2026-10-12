package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.build.SchematicPlan.Target;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.client.network.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Drives actual merged EQUIP/AIM/WAIT_BLOCK with separate block/item uses and server ACK delay. */
public final class WaterlogTransactionRegression {
    static int checks;
    static void check(boolean value,String why) { checks++; if(!value) throw new AssertionError(why); }
    static Object call(BuildController c,String name,Class<?>[] types,Object... args) throws Exception {
        return RecoveryRegression.call(c,name,types,args);
    }
    static void set(BuildController c,String name,Object value) throws Exception { MovementMiningRegression.set(c,name,value); }
    static Object get(BuildController c,String name) throws Exception { return MovementMiningRegression.get(c,name); }
    public static final class World extends ClientWorld {
        Map<BlockPos,BlockState> blocks; PendingUpdateManager updates;
        private World() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.AIR.getDefaultState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean isChunkLoaded(int x,int z) { return true; }
        @Override public long getTime() { return 0; }
    }
    public static final class Interaction extends ClientPlayerInteractionManager {
        Fixture f; int blocks,items,swaps,requestedAt; boolean reject,predict;
        private Interaction() { super(null,null); }
        @Override public ActionResult interactBlock(ClientPlayerEntity p,Hand hand,BlockHitResult hit) {
            blocks++; return ActionResult.SUCCESS; // An accepted block interaction does NOT pour a BucketItem.
        }
        @Override public ActionResult interactItem(PlayerEntity p,Hand hand) {
            items++; requestedAt=f.now;
            check(p.getMainHandStack().isOf(Items.WATER_BUCKET),"the real water use always owns the correct hand");
            if(reject) return ActionResult.PASS;
            f.w.updates.incrementSequence().addPendingUpdate(f.pos,f.w.getBlockState(f.pos),f.s.player);
            f.w.updates.close();
            if(predict) {
                f.w.blocks.put(f.pos,f.w.getBlockState(f.pos).with(Properties.WATERLOGGED,true));
                f.s.player.inv.setStack(f.s.player.inv.selectedSlot,new ItemStack(Items.BUCKET));
                f.s.player.inv.setStack(5,new ItemStack(Items.BUCKET));
            }
            return ActionResult.SUCCESS;
        }
    }
    static final class Fixture {
        final RecoveryRegression.Session s; final BuildController c; final World w; final Interaction interaction;
        final BlockPos pos=new BlockPos(0,64,2); final Target target=new Target(pos,Blocks.OAK_LEAVES.getDefaultState().with(Properties.WATERLOGGED,true));
        int now=100;
        Fixture() throws Exception {
            s=new RecoveryRegression.Session(Items.WATER_BUCKET,1,100); c=MovementMiningRegression.controller(s);
            w=RecoveryRegression.allocate(World.class); w.blocks=new HashMap<>(); w.updates=new PendingUpdateManager();
            RecoveryRegression.object(w,ClientWorld.class,"pendingUpdateManager",w.updates);
            w.blocks.put(pos,Blocks.OAK_LEAVES.getDefaultState()); w.blocks.put(new BlockPos(0,63,0),Blocks.COBBLESTONE.getDefaultState());
            s.mc.world=w; RecoveryRegression.object(s.player,Entity.class,"world",w);
            interaction=RecoveryRegression.allocate(Interaction.class); interaction.f=this; s.mc.interactionManager=interaction;
            s.player.inv.setStack(0,new ItemStack(Items.WATER_BUCKET)); s.player.inv.setStack(3,new ItemStack(Items.WATER_BUCKET));
            set(c,"target",target); set(c,"stage",BuildController.Stage.EQUIP);
            RecoveryRegression.field(ServerAck.class,"broken").setBoolean(null,false);
            RecoveryRegression.field(ServerAck.class,"getter").set(null,null); RecoveryRegression.field(ServerAck.class,"mapField").set(null,null);
            tick(100); call(c,"qualityPrimeWaterlog",new Class<?>[0]); tick(110);
        }
        void tick(int t) throws Exception {
            set(c,"cooldown",Math.max(0,(int)get(c,"cooldown")-Math.max(0,t-now)));
            now=t; set(c,"ticks",t); set(c,"stageTicks",t-100);
            set(c,"unacked",ServerAck.pendingCount(s.mc)); set(c,"ackPending",ServerAck.pending(s.mc));
            set(c,"sentYaw",s.player.getYaw()); set(c,"sentPitch",s.player.getPitch());
            s.mc.crosshairTarget=PlacementSolver.ray(s.mc,c.config.reach);
        }
        void use() throws Exception { call(c,"equip",new Class<?>[0]); }
        void ack(boolean wet) throws Exception {
            @SuppressWarnings("unchecked") Long2ObjectMap<Object> pending=(Long2ObjectMap<Object>)RecoveryRegression.field(PendingUpdateManager.class,"blockPosToPendingUpdate").get(w.updates);
            pending.clear();
            w.blocks.put(pos,Blocks.OAK_LEAVES.getDefaultState().with(Properties.WATERLOGGED,wet));
            if(wet) s.player.inv.setStack(s.player.inv.selectedSlot,new ItemStack(Items.BUCKET));
        }
        @SuppressWarnings("unchecked") Map<BlockPos,Integer> map(String name) throws Exception { return (Map<BlockPos,Integer>)get(c,name); }
        void retained(boolean water) throws Exception {
            check(!(boolean)call(c,"stackBuckets",new Class<?>[0]),"empty-bucket cleanup cannot move the selected bucket before water confirmation");
            check(get(c,"target")==target&&get(c,"pending")==target,"same leaf remains pending until its water is confirmed");
            check(get(c,"stage")==BuildController.Stage.WAIT_BLOCK,"water waits instead of switching to another job");
            check(s.player.inv.selectedSlot==0,"no early hotbar switch while water is pending");
            check(s.player.getMainHandStack().isOf(water?Items.WATER_BUCKET:Items.BUCKET),"hand remains owned by this pour, including predicted empty bucket");
            check(interaction.items==1&&interaction.blocks==0,"exactly one native item use, no swallowing block click or duplicate pour");
            check(map("waterAsks").isEmpty()&&(int)get(c,"placeFails")==0,"waiting/ACK is not counted as a failed pour");
            check((int)get(c,"placedCount")==0,"dry/predicted unacknowledged leaf is not recorded as complete");
        }
    }
    static void recentAndDelayed(boolean prediction) throws Exception {
        var f=new Fixture(); f.interaction.predict=prediction;
        f.map("recentPlaced").put(f.pos,100);
        check((boolean)call(f.c,"bucketReady",new Class<?>[]{Target.class},f.target),"freshly placed but confirmed dry leaf can receive water immediately");
        f.use(); check(f.interaction.items==1&&f.interaction.blocks==0,"filled water bucket uses native interactItem directly");
        for(int t=111;t<117;t++) { f.tick(t); call(f.c,"waitBlock",new Class<?>[0]); f.retained(!prediction); }
        f.ack(true); f.tick(117); call(f.c,"waitBlock",new Class<?>[0]);
        check(f.w.getBlockState(f.pos).get(Properties.WATERLOGGED),"delayed server response fills the original leaf");
        check(get(f.c,"pending")==null&&get(f.c,"target")!=f.target,"target advances only after water and ACK arrive");
        check((int)get(f.c,"placedCount")==1&&f.interaction.items==1,"wet leaf completes once after one item use");
    }
    static void dryBodyAndActualFailures() throws Exception {
        var f=new Fixture(); set(f.c,"pending",f.target); set(f.c,"stage",BuildController.Stage.WAIT_BLOCK);
        for(int n=0;n<7;n++) {
            f.tick(110+n); set(f.c,"pending",f.target); set(f.c,"stage",BuildController.Stage.WAIT_BLOCK);
            call(f.c,"waitBlock",new Class<?>[0]);
            check(get(f.c,"target")==f.target&&get(f.c,"stage")==BuildController.Stage.EQUIP,"dry leaf body transitions to water preparation without losing target");
            check(f.map("waterAsks").isEmpty()&&f.map("deferred").isEmpty(),"seven dry-body transitions cannot trigger false five-pour defer/resync");
            check((int)get(f.c,"placedCount")==0&&((Collection<?>)get(f.c,"verify")).isEmpty(),"dry body never queues a successful wet-leaf verification");
        }
        f.tick(120); f.use(); check(f.interaction.items==1,"dry body proceeds to genuine bucket use");
        f.ack(false); int deadline=120+8+3*(int)call(f.c,"latencyTicks",new Class<?>[0]);
        for(int t=121;t<deadline;t++) { f.tick(t); call(f.c,"waitBlock",new Class<?>[0]); f.retained(true); }
        f.tick(deadline); call(f.c,"waitBlock",new Class<?>[0]);
        check((int)get(f.c,"placeFails")==1&&f.map("waterAsks").get(f.pos)==1,"one genuine unconfirmed water use counts one failed request");
        var plan=RecoveryRegression.allocate(SchematicPlan.class);
        RecoveryRegression.object(plan,SchematicPlan.class,"blocks",new HashMap<>(Map.of(f.pos,f.target)));
        plan.targets=new ArrayList<>(List.of(f.target)); plan.loaded=true; set(f.c,"plan",plan);
        call(f.c,"notePlacementVerified",new Class<?>[]{BlockPos.class},f.pos);
        check((int)get(f.c,"placeFails")==1,"verification of the dry leaf body cannot erase an outstanding water failure");
        set(f.c,"plan",null);
        check(get(f.c,"target")==f.target&&get(f.c,"pending")==null&&get(f.c,"stage")==BuildController.Stage.EQUIP,"timeout retries the same leaf without premature abandonment");
        f.interaction.predict=true;
        f.tick(deadline+1); f.use(); check(f.interaction.items==2,"retry promptly uses water at the original receiver");
        set(f.c,"plan",plan); call(f.c,"notePlacementVerified",new Class<?>[]{BlockPos.class},f.pos);
        check((int)get(f.c,"placeFails")==1,"predicted water awaiting ACK cannot erase the previous real failure");
        set(f.c,"plan",null);
        f.ack(true); f.tick(deadline+4); call(f.c,"waitBlock",new Class<?>[0]);
        check(f.map("waterAsks").isEmpty(),"successful water confirmation clears this leaf's water retry count");
        call(f.c,"notePlacementVerified",new Class<?>[]{BlockPos.class},f.pos);
        check((int)get(f.c,"placeFails")==0,"confirmed wet leaf clears only its own recovered failure");

        var rejected=new Fixture(); rejected.interaction.reject=true; rejected.use();
        check(rejected.interaction.items==1&&rejected.interaction.blocks==0,"a declined use still invokes the actual item path");
        check(get(rejected.c,"target")==rejected.target&&get(rejected.c,"pending")==null&&get(rejected.c,"stage")==BuildController.Stage.AIM,"declined native use does not enter fake confirmation or lose the leaf");
        check((int)get(rejected.c,"placeFails")==1,"actual rejected item request participates in the 25-failure recovery budget");
        check(rejected.s.player.getMainHandStack().isOf(Items.WATER_BUCKET)&&rejected.map("waterAsks").isEmpty(),"declined use keeps its filled bucket and cannot count successful pour bookkeeping");
    }
    static void ackAndWrongReceiver() throws Exception {
        var f=new Fixture(); f.w.updates.incrementSequence().addPendingUpdate(f.pos,f.w.getBlockState(f.pos),f.s.player); f.w.updates.close();
        for(int t=110;t<115;t++) {
            f.tick(t); f.use();
            check(f.interaction.items==0&&get(f.c,"stage")==BuildController.Stage.AIM,"unconfirmed leaf waits in AIM before sending water");
            check(!((boolean)get(f.c,"bucketMiss"))&&f.map("waterAsks").isEmpty()&&(int)get(f.c,"placeFails")==0,"ACK wait cannot be mistaken for wrong ray or failed pour");
        }
        f.ack(false); f.tick(115); f.use(); check(f.interaction.items==1,"confirmed leaf uses water immediately after ACK clears");
        var wrong=new Fixture(); wrong.s.player.setYaw(90); wrong.tick(110);
        check(!(boolean)call(wrong.c,"bucketReady",new Class<?>[]{Target.class},wrong.target),"genuinely wrong ray still prevents waterlogging another cell");
        check(wrong.interaction.items==0&&wrong.s.player.getMainHandStack().isOf(Items.WATER_BUCKET),"wrong receiver never sends a pour or changes the water bucket");
        Navigation.GHOSTS.add(wrong.pos); wrong.s.player.setYaw(0); wrong.tick(111);
        check(!(boolean)call(wrong.c,"bucketReady",new Class<?>[]{Target.class},wrong.target),"known ghost receiver keeps its safety veto"); Navigation.GHOSTS.clear();
    }
    public static int run() throws Exception {
        checks=0; Navigation.GHOSTS.clear(); Navigation.AVOID.clear();
        recentAndDelayed(false); recentAndDelayed(true); dryBodyAndActualFailures(); ackAndWrongReceiver();
        System.out.println("PASS: "+checks+" native water-item use, retained hand, delayed ACK and real-pour accounting assertions."); return checks;
    }
}
