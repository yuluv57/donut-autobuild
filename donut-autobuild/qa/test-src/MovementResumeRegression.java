package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.build.SchematicPlan.Target;
import com.donutautobuild.logic.PathSearch;
import com.donutautobuild.logic.PathSearch.Cell;
import net.minecraft.block.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.border.WorldBorder;
import java.util.*;

/** Live Navigation geometry and multi-tick MOVE/input simulation; no stand/clear cache injection. */
public final class MovementResumeRegression {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static Object call(Object o, String method, Class<?>[] types, Object... args) throws Exception {
        return RecoveryRegression.call(o, method, types, args);
    }
    static void set(BuildController c,String field,Object value) throws Exception { MovementMiningRegression.set(c,field,value); }
    static Object get(BuildController c,String field) throws Exception { return MovementMiningRegression.get(c,field); }

    /** Only the backing storage is mocked; support/clearance comes from Minecraft block VoxelShapes. */
    public static final class GeometryWorld extends ClientWorld {
        Map<BlockPos,BlockState> blocks; WorldBorder border;
        private GeometryWorld() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos,Blocks.AIR.getDefaultState()); }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public boolean isChunkLoaded(int x,int z) { return true; }
        @Override public WorldBorder getWorldBorder() { return border; }
        @Override public int getBottomY() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public long getTime() { return 0; }
        @Override public boolean isSpaceEmpty(Entity entity,Box box) {
            for(var entry:blocks.entrySet()) for(Box local:entry.getValue().getCollisionShape(this,entry.getKey()).getBoundingBoxes())
                if(local.offset(entry.getKey()).intersects(box)) return false;
            return true;
        }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity,Box box) { return List.of(); }
    }
    static final class Fixture {
        final RecoveryRegression.Session s; final BuildController c; final GeometryWorld w; final BuildConfig cfg;
        Fixture(Vec3d pos) throws Exception {
            s=new RecoveryRegression.Session(Items.COBBLESTONE,64,100); c=MovementMiningRegression.controller(s);
            w=RecoveryRegression.allocate(GeometryWorld.class); w.blocks=new HashMap<>(); w.border=new WorldBorder();
            s.mc.world=w; RecoveryRegression.object(s.player,Entity.class,"world",w);
            WaterlogAimRegression.position(s.player,pos);
            cfg=c.config; cfg.pathRange=32; cfg.bridge=false; cfg.scaffold=false; cfg.syncLitematicaLayer=false;
            set(c,"stage",BuildController.Stage.MOVE); set(c,"approachOnly",true); set(c,"nearbyCheckAt",100000);
            set(c,"qualityCautiousUntil",0); set(c,"qualityUnsafeSince",-1);
        }
        void floor(int x,int y,int z) { w.blocks.put(new BlockPos(x,y,z),Blocks.COBBLESTONE.getDefaultState()); }
        Navigation navigation() { var n=new Navigation(s.mc,cfg,null,false,null); n.walkOnly=true; return n; }
        void path(List<Cell> route) throws Exception {
            set(c,"terrain",navigation()); set(c,"path",route); set(c,"pathIndex",1);
            set(c,"lastPosition",s.player.getPos()); set(c,"walkPitch",30F); set(c,"stageTicks",1);
        }
        boolean supported(Vec3d at) {
            Box feet=new Box(at.x-.3,at.y-.06,at.z-.3,at.x+.3,at.y+.001,at.z+.3);
            return !w.isSpaceEmpty(s.player,feet);
        }
        void physicsStep() throws Exception {
            var o=s.mc.options; double f=(o.forwardKey.isPressed()?1:0)-(o.backKey.isPressed()?1:0);
            double a=(o.leftKey.isPressed()?1:0)-(o.rightKey.isPressed()?1:0);
            double yaw=Math.toRadians(s.player.getYaw()),norm=Math.max(1,Math.hypot(f,a));
            double accel=.098*(o.sneakKey.isPressed()?.3:o.sprintKey.isPressed()?1.3:1);
            Vec3d old=s.player.getVelocity();
            Vec3d move=new Vec3d(old.x+(-Math.sin(yaw)*f+Math.cos(yaw)*a)/norm*accel,0,
                old.z+(Math.cos(yaw)*f+Math.sin(yaw)*a)/norm*accel);
            Vec3d next=s.player.getPos().add(move);
            check(supported(next),"executed MOVE stays supported at "+next);
            WaterlogAimRegression.position(s.player,next);
            MovementMiningRegression.velocity(s.player,move.multiply(.546,1,.546));
            WaterlogAimRegression.pose(s.player,o.sneakKey.isPressed());
        }
        int walkTo(Vec3d goal,int maxTicks) throws Exception {
            int ticks=0;
            while(s.player.getPos().squaredDistanceTo(goal)>.14 && ticks<maxTicks) {
                set(c,"ticks",300+ticks); set(c,"stageTicks",ticks+1);
                call(c,"release",new Class<?>[0]); call(c,"move",new Class<?>[0]);
                call(c,"qualityFinishInputs",new Class<?>[0]);
                check(get(c,"stage")==BuildController.Stage.MOVE,"legitimate MOVE does not abandon/replan its target");
                physicsStep(); ticks++;
            }
            check(ticks<maxTicks,"MOVE reaches goal instead of remaining still: "+s.player.getPos()+" -> "+goal);
            check(ticks>=3,"arrival uses multiple evolving physics ticks");
            return ticks;
        }
    }

    static void navigationAndMotion() throws Exception {
        var f=new Fixture(new Vec3d(.9,64,.5));
        for(int z=0;z<=6;z++) f.floor(0,63,z);
        var nav=f.navigation(); var start=new Cell(0,64,0); var goal=new Cell(0,64,6);
        check(nav.standable(start),"real full-cube floor is standable without cached answers");
        check(nav.neighbors(start).stream().anyMatch(e->e.to().equals(start.add(0,0,1))),"live planner retains narrow supported corridor");
        var search=new PathSearch(start,List.of(goal),nav,1000); search.advance(1000);
        check(search.done()&&search.result()!=null,"actual PathSearch finds one-wide corridor");
        f.path(search.result()); int ticks=f.walkTo(Navigation.feet(goal),160);
        check(f.s.player.getZ()>6,"offset player advances more than five blocks through repeated MOVE/input guards");
        check(ticks<130,"normal supported walk is not a permanent safety pause");

        var inward=new Fixture(new Vec3d(.99,64,.5)); inward.floor(0,63,0);
        set(inward.c,"stage",BuildController.Stage.AIM); inward.s.player.setYaw(90F);
        double initial=inward.s.player.getX();
        for(int t=0;t<4;t++) {
            set(inward.c,"ticks",500+t); call(inward.c,"release",new Class<?>[0]);
            inward.s.mc.options.forwardKey.setPressed(true); call(inward.c,"qualityFinishInputs",new Class<?>[0]); inward.physicsStep();
        }
        check(inward.s.player.getX()<initial-.2,"partly supported player can walk inward from block edge");

        var corner=new Fixture(new Vec3d(.9,64,.5));
        for(int z=0;z<=2;z++) corner.floor(0,63,z);
        for(int x=0;x<=3;x++) corner.floor(x,63,2);
        List<Cell> route=List.of(new Cell(0,64,0),new Cell(0,64,1),new Cell(0,64,2),new Cell(1,64,2),new Cell(2,64,2),new Cell(3,64,2));
        corner.path(route); corner.walkTo(new Vec3d(3.5,64,2.5),180);
        check(corner.s.player.getX()>3,"actual MOVE turns corner and continues along supported floor");

        var diagonal=new Fixture(new Vec3d(.5,64,.5)); diagonal.floor(0,63,0); diagonal.floor(1,63,1);
        nav=diagonal.navigation(); var end=new Cell(1,64,1);
        check(nav.neighbors(start).stream().anyMatch(e->e.to().equals(end)),"corner-contact full cubes retain physically supported diagonal route");
        check(nav.edgeStillValid(start,end),"planning and execution agree on diagonal geometry");
        diagonal.path(List.of(start,end)); diagonal.walkTo(Navigation.feet(end),100);

        var row=new Fixture(new Vec3d(.5,64,.5));
        for(int i=0;i<=8;i++) row.floor(i,63,i);
        var leaf=new BlockPos(8,63,8); row.w.blocks.put(leaf,Blocks.OAK_LEAVES.getDefaultState());
        set(row.c,"currentLayer",63);
        @SuppressWarnings("unchecked") List<Cell> rowPath=(List<Cell>)call(row.c,"planePath",new Class<?>[]{Target.class,int.class},
            new Target(leaf,Blocks.OAK_LEAVES.getDefaultState().with(Properties.WATERLOGGED,true)),2000);
        check(rowPath!=null&&rowPath.size()>3,"actual row-mode BFS finds remote target over corner-contact floor");
        var rowNav=(Navigation)get(row.c,"terrain");
        for(int i=1;i<rowPath.size();i++) check(rowNav.edgeStillValid(rowPath.get(i-1),rowPath.get(i)),
            "row-mode planned edge passes same live execution validation");

        var water=new Fixture(new Vec3d(.9,64,.5));
        for(int z=0;z<=4;z++) water.w.blocks.put(new BlockPos(0,63,z),Blocks.OAK_LEAVES.getDefaultState().with(Properties.WATERLOGGED,true));
        nav=water.navigation(); goal=new Cell(0,64,4); search=new PathSearch(start,List.of(goal),nav,500);search.advance(500);
        check(search.result()!=null,"actual wet-leaf collision supplies a walking route");
        water.path(search.result()); water.walkTo(Navigation.feet(goal),130);
    }

    static void unsafeAndSpecialEdges() throws Exception {
        var bad=new Fixture(new Vec3d(.5,64,1.10)); bad.floor(0,63,0); bad.s.player.setYaw(0F);
        set(bad.c,"stage",BuildController.Stage.AIM);
        MovementMiningRegression.velocity(bad.s.player,new Vec3d(0,0,.23));
        bad.s.mc.options.forwardKey.setPressed(true); call(bad.c,"qualityFinishInputs",new Class<?>[0]);
        check(!MovementMiningRegression.moving(bad.s),"unsupported next step is still blocked");
        bad.w.blocks.put(new BlockPos(0,63,1),Blocks.LAVA.getDefaultState()); bad.floor(0,62,1);
        bad.s.mc.options.forwardKey.setPressed(true); call(bad.c,"qualityFinishInputs",new Class<?>[0]);
        check(!MovementMiningRegression.moving(bad.s),"lava above a lower solid floor cannot masquerade as safe support");

        var jump=new Fixture(new Vec3d(.5,64,.5));
        jump.floor(0,63,0); jump.floor(0,63,-1); jump.floor(0,63,2); jump.floor(0,61,1);
        var nav=jump.navigation(); var start=new Cell(0,64,0); var landing=new Cell(0,64,2);
        check(nav.edgeStillValid(start,landing),"actual planner validates one-block parkour over shallow dry gap");
        jump.path(List.of(start,landing)); jump.s.player.setYaw(0F);
        set(jump.c,"qualityCautiousUntil",1000); set(jump.c,"ticks",600);
        jump.s.mc.options.forwardKey.setPressed(true);
        call(jump.c,"qualityFinishInputs",new Class<?>[0]);
        check(jump.s.mc.options.forwardKey.isPressed(),"validated parkour run-up is not canceled by stale cautious mode");
        jump.s.mc.options.jumpKey.setPressed(true); MovementMiningRegression.velocity(jump.s.player,new Vec3d(0,0,.24));
        WaterlogAimRegression.position(jump.s.player,new Vec3d(.5,64,.9));
        MovementMiningRegression.velocity(jump.s.player,new Vec3d(0,0,.24));
        call(jump.c,"qualityFinishInputs",new Class<?>[0]);
        check(jump.s.mc.options.forwardKey.isPressed()&&jump.s.mc.options.jumpKey.isPressed(),"validated airborne launch can cross planned gap");
        WaterlogAimRegression.position(jump.s.player,new Vec3d(.99,64,.9));
        MovementMiningRegression.velocity(jump.s.player,new Vec3d(.35,0,.24));
        jump.s.mc.options.forwardKey.setPressed(true); jump.s.mc.options.jumpKey.setPressed(true);
        call(jump.c,"qualityFinishInputs",new Class<?>[0]);
        check(!jump.s.mc.options.jumpKey.isPressed(),"valid gap cannot authorize a sideways launch outside its safe corridor");

        var lowerJump=new Fixture(new Vec3d(.5,64,.9));
        lowerJump.floor(0,63,0); lowerJump.floor(0,63,-1); lowerJump.floor(0,62,2); lowerJump.floor(0,61,1);
        var lowerLanding=new Cell(0,63,2); nav=lowerJump.navigation();
        check(nav.edgeStillValid(start,lowerLanding),"live planner finds parkour landing one block lower");
        lowerJump.path(List.of(start,lowerLanding)); lowerJump.s.player.setYaw(0F);
        lowerJump.s.mc.options.forwardKey.setPressed(true); lowerJump.s.mc.options.jumpKey.setPressed(true);
        MovementMiningRegression.velocity(lowerJump.s.player,new Vec3d(0,0,.24));
        call(lowerJump.c,"qualityFinishInputs",new Class<?>[0]);
        check(lowerJump.s.mc.options.jumpKey.isPressed(),"lower parkour landing is not mistaken for a ground-only descent");

        var down=new Fixture(new Vec3d(.5,64,.85)); down.floor(0,63,0); down.floor(0,61,1);
        nav=down.navigation(); var lower=new Cell(0,62,1);
        check(nav.edgeStillValid(start,lower),"actual Navigation retains safe two-block descent");
        down.path(List.of(start,lower)); down.s.player.setYaw(0F); set(down.c,"ticks",650); set(down.c,"qualityCautiousUntil",1000);
        down.s.mc.options.forwardKey.setPressed(true); MovementMiningRegression.velocity(down.s.player,new Vec3d(0,0,.15));
        call(down.c,"qualityFinishInputs",new Class<?>[0]);
        check(down.s.mc.options.forwardKey.isPressed(),"validated two-block descent remains executable");
        check(!down.s.mc.options.sneakKey.isPressed(),"stale edge crouch cannot stop a validated descent");

        var covered=new Fixture(new Vec3d(.5,64,.85)); covered.floor(0,63,0); covered.floor(0,60,1);
        covered.w.blocks.put(new BlockPos(0,62,0),Blocks.LAVA.getDefaultState());
        var bottom=new Cell(0,61,1); nav=covered.navigation();
        check(nav.edgeStillValid(start,bottom),"source floor over lava still permits a separate clear three-block descent");
        covered.path(List.of(start,bottom)); covered.s.player.setYaw(0F); covered.s.mc.options.forwardKey.setPressed(true);
        MovementMiningRegression.velocity(covered.s.player,new Vec3d(0,0,.1));
        call(covered.c,"qualityFinishInputs",new Class<?>[0]);
        check(covered.s.mc.options.forwardKey.isPressed(),"lava covered beneath existing source floor does not trap the player");

        var sideways=new Fixture(new Vec3d(.99,64,.5)); sideways.floor(0,63,0); sideways.floor(0,60,1); sideways.floor(1,60,0);
        nav=sideways.navigation();
        check(nav.edgeStillValid(start,bottom),"forward descent remains valid with a different dry hole beside it");
        sideways.path(List.of(start,bottom)); sideways.s.player.setYaw(0F); sideways.s.mc.options.forwardKey.setPressed(true);
        MovementMiningRegression.velocity(sideways.s.player,new Vec3d(.45,0,0));
        call(sideways.c,"qualityFinishInputs",new Class<?>[0]);
        check(!MovementMiningRegression.moving(sideways.s),"planned descent cannot authorize sideways drift into an unrelated hole");
        sideways.floor(1,63,0); sideways.s.mc.options.forwardKey.setPressed(true);
        MovementMiningRegression.velocity(sideways.s.player,new Vec3d(.45,0,0));
        call(sideways.c,"qualityFinishInputs",new Class<?>[0]);
        check(sideways.s.mc.options.forwardKey.isPressed(),"descent corridor does not block lateral travel where ordinary floor supports the step");
    }
    public static int run() throws Exception {
        checks=0; Navigation.GHOSTS.clear(); Navigation.AVOID.clear();
        navigationAndMotion(); unsafeAndSpecialEdges();
        System.out.println("PASS: "+checks+" live geometry and multi-tick movement resume assertions.");
        return checks;
    }
}
