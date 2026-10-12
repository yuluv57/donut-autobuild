package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.build.SchematicPlan.Target;
import net.minecraft.block.*;
import net.minecraft.client.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import java.util.*;

/** Actual controller input, vanilla raycasts and MinecraftClient's native held-attack path. */
public final class MovementMiningRegression {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception { return RecoveryRegression.call(o,name,types,args); }
    static void set(BuildController c, String name, Object value) throws Exception { RecoveryRegression.field(BuildController.class,name).set(c,value); }
    static Object get(BuildController c, String name) throws Exception { return RecoveryRegression.field(BuildController.class,name).get(c); }
    static boolean moving(RecoveryRegression.Session s) { var o=s.mc.options; return o.forwardKey.isPressed()||o.backKey.isPressed()||o.leftKey.isPressed()||o.rightKey.isPressed(); }
    static void velocity(RecoveryRegression.Player p, Vec3d value) throws Exception { RecoveryRegression.object(p,Entity.class,"velocity",value); }
    static BuildController controller(RecoveryRegression.Session s) throws Exception {
        var cfg=new BuildConfig(); cfg.syncLitematicaLayer=false; cfg.reach=4.25;
        var c=new BuildController(cfg); RecoveryRegression.object(c,BuildController.class,"mc",s.mc);
        set(c,"running",true); set(c,"ticks",100); set(c,"stage",BuildController.Stage.AIM);
        s.mc.world=RecoveryRegression.allocate(RecoveryRegression.World.class);
        RecoveryRegression.object(s.player,Entity.class,"world",s.mc.world);
        ((RecoveryRegression.World)s.mc.world).blocks=new HashMap<>();
        WaterlogAimRegression.position(s.player,new Vec3d(.5,64,.5));
        s.player.setYaw(0); s.player.setPitch(35);
        RecoveryRegression.object(s.mc,MinecraftClient.class,"mouse",RecoveryRegression.allocate(Mouse.class));
        RecoveryRegression.field(Mouse.class,"cursorLocked").setBoolean(s.mc.mouse,true);
        RecoveryRegression.field(InventoryAccess.class,"syncRev").setInt(null,-1);
        RecoveryRegression.field(InventoryAccess.class,"resyncSentAt").setLong(null,0);
        return c;
    }
    static void floor(RecoveryRegression.Session s, int minX,int maxX,int minZ,int maxZ) {
        var w=(RecoveryRegression.World)s.mc.world; w.blocks.clear();
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) w.blocks.put(new BlockPos(x,63,z),Blocks.COBBLESTONE.getDefaultState());
    }
    static void movement() throws Exception {
        var s=new RecoveryRegression.Session(Items.WATER_BUCKET,1,100); var c=controller(s);
        floor(s,-3,3,-3,3);
        for (double speed : new double[]{.3,.053,.022,-.022}) {
            Vec3d v=new Vec3d(speed,0,speed/2); int elapsed=0;
            while(v.horizontalLengthSquared()>1.0E-4 && elapsed++<16) {
                velocity(s.player,v); s.mc.options.forwardKey.setPressed(true);
                call(c,"brake",new Class<?>[0]);
                check(!moving(s),"braking releases all direction keys, never initiates an opposite step");
                v=v.multiply(.546,1,.546);
            }
            check(elapsed<=7,"observed motion settles under normal ground friction within seven ticks");
        }
        set(c,"stage",BuildController.Stage.MOVE);
        velocity(s.player,new Vec3d(0,0,.22)); s.mc.options.forwardKey.setPressed(true);
        check(!(boolean)call(c,"qualityMovementGuard",new Class<?>[0]) && s.mc.options.forwardKey.isPressed(),"solid straight path keeps uninterrupted forward input");
        check(!s.mc.options.sneakKey.isPressed(),"solid straight path is not slowed by blanket crouching");
        // Fast approach can slow once near an edge, then must stop before the unsupported footprint.
        floor(s,0,0,0,0); WaterlogAimRegression.position(s.player,new Vec3d(.5,64,1.10));
        velocity(s.player,new Vec3d(0,0,.22)); s.mc.options.forwardKey.setPressed(true);
        check((boolean)call(c,"qualityMovementGuard",new Class<?>[0]),"momentum toward a hole is rejected before stepping off");
        check(!moving(s)&&!s.mc.options.jumpKey.isPressed()&&s.mc.options.sneakKey.isPressed(),"edge guard releases direction and jump, holds sneak");
        var w=(RecoveryRegression.World)s.mc.world;
        w.blocks.put(new BlockPos(0,63,1),Blocks.LAVA.getDefaultState());
        w.blocks.put(new BlockPos(0,62,1),Blocks.COBBLESTONE.getDefaultState());
        set(c,"ticks",101); s.mc.options.forwardKey.setPressed(true); s.mc.options.jumpKey.setPressed(true);
        check((boolean)call(c,"qualityMovementGuard",new Class<?>[0])&&!moving(s),"lava with a solid block below is still rejected");
        check(!s.mc.options.jumpKey.isPressed(),"stuck-jump cannot bypass the lava guard");
        // A diagonal corner cut can fall even if both path endpoint cells have floors.
        floor(s,0,1,0,1); w.blocks.remove(new BlockPos(1,63,0));
        WaterlogAimRegression.position(s.player,new Vec3d(.8,64,.5)); s.player.setYaw(-45);
        velocity(s.player,new Vec3d(.20,0,.20)); s.mc.options.forwardKey.setPressed(true);
        set(c,"ticks",102);
        check(!(boolean)call(c,"qualityMovementGuard",new Class<?>[0]),"supported corner contact is not falsely stopped");
        // Exact yaw/strafe signs, all four cardinal orientations, both A and D.
        for(int yaw : new int[]{0,90,180,270}) for(boolean left : new boolean[]{true,false}) {
            floor(s,-2,2,-2,2); WaterlogAimRegression.position(s.player,new Vec3d(.5,64,.5)); s.player.setYaw(yaw);
            call(c,"release",new Class<?>[0]); set(c,"qualityCautiousUntil",0); set(c,"qualityUnsafeSince",-1); set(c,"ticks",200);
            s.mc.options.leftKey.setPressed(left); s.mc.options.rightKey.setPressed(!left);
            double sign=left?1:-1, r=Math.toRadians(yaw);
            int hx=(int)Math.floor(.5+sign*Math.cos(r)*.9), hz=(int)Math.floor(.5+sign*Math.sin(r)*.9);
            w.blocks.remove(new BlockPos(hx,63,hz));
            WaterlogAimRegression.position(s.player,new Vec3d(.5+sign*Math.cos(r)*.6,64,.5+sign*Math.sin(r)*.6));
            velocity(s.player,new Vec3d(sign*Math.cos(r)*.22,0,sign*Math.sin(r)*.22));
            check((boolean)call(c,"qualityMovementGuard",new Class<?>[0]),"actual strafe direction toward hole checked at yaw "+yaw+" A="+left);
        }
        floor(s,-2,2,-2,2); WaterlogAimRegression.position(s.player,new Vec3d(.5,64,.5));
        s.player.setYaw(0); set(c,"ticks",250); set(c,"qualityCautiousUntil",0); call(c,"release",new Class<?>[0]);
        w.blocks.put(new BlockPos(0,63,1),Blocks.OAK_LEAVES.getDefaultState().with(Properties.WATERLOGGED,true));
        s.mc.options.forwardKey.setPressed(true);
        check(!(boolean)call(c,"qualityMovementGuard",new Class<?>[0]),"waterlogged leaves with actual collision remain a safe floor");
        check(!s.mc.options.sneakKey.isPressed(),"safe waterlogged floor keeps full walking speed");
        check((boolean)call(c,"qualityFootprintSafe",new Class<?>[]{double.class,double.class,double.class,boolean.class},.5,64D,1.5,false),"waterlogged floor itself is accepted, not merely approached slowly");
        w.blocks.put(new BlockPos(0,64,1),Blocks.COBBLESTONE.getDefaultState()); s.mc.options.jumpKey.setPressed(true);
        check(!(boolean)call(c,"qualityMovementGuard",new Class<?>[0]),"supported one-block upward step can still jump");
    }
    static void pathfinding() throws Exception {
        var s=new RecoveryRegression.Session(Items.COBBLESTONE,64,100); var c=controller(s);
        // Replanning starts actual goal collection; repeated refusal is bounded per target.
        floor(s,0,0,0,0); WaterlogAimRegression.position(s.player,new Vec3d(.5,64,1.10));
        var target=new Target(new BlockPos(0,64,3),Blocks.COBBLESTONE.getDefaultState());
        var plan=RecoveryRegression.allocate(SchematicPlan.class);
        RecoveryRegression.object(plan,SchematicPlan.class,"blocks",new HashMap<>(Map.of(target.pos(),target)));
        plan.targets=new ArrayList<>(List.of(target)); plan.loaded=true;
        var journal=RecoveryRegression.allocate(ScaffoldJournal.class);
        RecoveryRegression.object(journal,ScaffoldJournal.class,"placed",new HashMap<BlockPos,String>());
        set(c,"target",target); set(c,"plan",plan); set(c,"journal",journal); set(c,"qualityUnsafeSince",100);
        set(c,"stage",BuildController.Stage.MOVE); set(c,"ticks",106);
        velocity(s.player,new Vec3d(0,0,.22)); s.mc.options.forwardKey.setPressed(true);
        call(c,"qualityMovementGuard",new Class<?>[0]);
        check(get(c,"stage")==BuildController.Stage.GOALS && !moving(s),"sustained unsafe motion stops and restarts actual goal collection");
        set(c,"stage",BuildController.Stage.MOVE); set(c,"ticks",130); set(c,"qualityUnsafeSince",120);
        set(c,"qualityReplans",2); s.mc.options.forwardKey.setPressed(true);
        call(c,"qualityMovementGuard",new Class<?>[0]);
        @SuppressWarnings("unchecked") var deferred=(Map<BlockPos,Integer>)get(c,"deferred");
        check(deferred.getOrDefault(target.pos(),0)>130 && !moving(s),"same unsafe route is postponed after bounded replans instead of walking forever");
    }
    static void waterStopsAndPours() throws Exception {
        var s=new RecoveryRegression.Session(Items.WATER_BUCKET,1,100); var c=controller(s); floor(s,-3,3,-3,3);
        var w=(RecoveryRegression.World)s.mc.world; var pos=new BlockPos(0,64,2);
        var dry=Blocks.OAK_LEAVES.getDefaultState(); w.blocks.put(pos,dry);
        set(c,"target",new Target(pos,dry.with(Properties.WATERLOGGED,true)));
        s.player.inv.setStack(0,new ItemStack(Items.WATER_BUCKET));
        call(c,"qualityPrimeWaterlog",new Class<?>[0]);
        Vec3d v=new Vec3d(.022,0,0);
        int clicked=0;
        for(int t=120;t<130 && s.interaction.placements==0;t++) {
            set(c,"ticks",t); velocity(s.player,v);
            s.mc.crosshairTarget=PlacementSolver.ray(s.mc,4.25);
            set(c,"sentYaw",s.player.getYaw()); set(c,"sentPitch",s.player.getPitch());
            call(c,"aim",new Class<?>[0]);
            check(!moving(s),"water AIM cannot turn small residual drift into more movement");
            v=v.multiply(.546,1,.546); clicked=t;
        }
        check(s.interaction.placements==1 && clicked<=124,"video's .022 drift settles and water is poured without ten-second stall");
        check(w.getBlockState(pos).get(Properties.WATERLOGGED),"actual vanilla ray receives water on original leaf after stopping");
    }
    static void mining() throws Exception {
        var s=new RecoveryRegression.Session(Items.NETHERITE_PICKAXE,1,100); var c=controller(s); floor(s,-3,3,-3,3);
        var w=(RecoveryRegression.World)s.mc.world; var pos=new BlockPos(0,64,2);
        w.blocks.put(pos,Blocks.ENDER_CHEST.getDefaultState()); s.player.inv.setStack(0,new ItemStack(Items.NETHERITE_PICKAXE));
        set(c,"stage",BuildController.Stage.STASH); set(c,"stashPos",pos); set(c,"stashStep",7); set(c,"silkWarned",true);
        set(c,"ackPending",true); // Unrelated placement acknowledgments must not block the held attack.
        s.interaction.breakAfter=40;
        for(int t=300;t<350 && !w.getBlockState(pos).isAir();t++) {
            set(c,"ticks",t); call(c,"qualityReleaseForTick",new Class<?>[0]);
            if(s.interaction.miningUpdates>0) check(s.mc.options.attackKey.isPressed(),"routine tick reset preserves continuous held left mouse");
            s.mc.crosshairTarget=PlacementSolver.ray(s.mc,4.25);
            set(c,"sentYaw",s.player.getYaw()); set(c,"sentPitch",s.player.getPitch());
            int before=s.interaction.miningUpdates;
            call(c,"stashTick",new Class<?>[0]);
            check(!(boolean)get(c,"manualBreaking"),"stash never hides or manually injects native mining state");
            if(s.mc.options.attackKey.isPressed()) {
                check(s.interaction.miningUpdates==before,"controller itself does not send an extra digging-progress update");
                call(s.mc,"handleBlockBreaking",new Class<?>[]{boolean.class},true);
                check(s.interaction.miningUpdates==before+1&&s.interaction.mined.equals(pos),"native held-left-click advances correct ender chest once per tick");
            }
        }
        check(s.interaction.miningUpdates==40 && w.getBlockState(pos).isAir(),"continuous vanilla holding completes ender chest instead of a retry loop");
        check(s.interaction.miningCancels==0,"held attack has no cancel/reset between progress ticks");
        set(c,"ticks",351); call(c,"stashTick",new Class<?>[0]);
        check(!s.mc.options.attackKey.isPressed() && (int)get(c,"stashStep")==6,"stash releases mouse and enters server confirmation when block disappears");
        w.blocks.put(pos,Blocks.ENDER_CHEST.getDefaultState()); set(c,"mining",pos); set(c,"ticks",400);
        set(c,"sentYaw",s.player.getYaw()); set(c,"sentPitch",s.player.getPitch());
        call(c,"breakBlock",new Class<?>[]{BlockState.class},w.getBlockState(pos));
        check(s.mc.options.attackKey.isPressed(),"a returned chest can restart the same normal held attack");
        call(c,"release",new Class<?>[0]); check(!s.mc.options.attackKey.isPressed(),"explicit release stops mining immediately");
        RecoveryRegression.field(Mouse.class,"cursorLocked").setBoolean(s.mc.mouse,false);
        call(c,"breakBlock",new Class<?>[]{BlockState.class},w.getBlockState(pos));
        check(!s.mc.options.attackKey.isPressed() && s.interaction.miningUpdates==40,"unfocused game never falls back to injected digging packets");
        set(c,"running",false); s.mc.options.attackKey.setPressed(true);
        call(c,"qualityFinishInputs",new Class<?>[0]);
        check(s.mc.options.attackKey.isPressed(),"paused controller leaves physical mouse usable for manual mining");
        call(s.mc,"handleBlockBreaking",new Class<?>[]{boolean.class},true);
        check(s.interaction.miningUpdates==41,"manual left mouse still reaches vanilla breaking while mod is paused");
    }
    public static int run() throws Exception {
        checks=0; movement(); pathfinding(); waterStopsAndPours(); mining();
        System.out.println("PASS: "+checks+" movement safety, passive stopping and native held-attack assertions."); return checks;
    }
}

