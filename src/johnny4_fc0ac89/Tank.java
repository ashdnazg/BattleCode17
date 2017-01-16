package johnny4_fc0ac89;

import battlecode.common.*;

import static johnny4_fc0ac89.Util.checkLineOfFire;
import static johnny4_fc0ac89.Util.tryMove;
import static johnny4_fc0ac89.Util.preTick;

public class Tank {

    RobotController rc;
    johnny4_fc0ac89.Map map;
    johnny4_fc0ac89.Radio radio;

    public Tank(RobotController rc) {
        this.rc = rc;
        this.radio = new johnny4_fc0ac89.Radio(rc);
        this.map = new johnny4_fc0ac89.Map(rc, radio);
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                //System.out.println("BYTECODE OVERFLOW");
            }
            Clock.yield();
        }
    }

    RobotInfo lastTarget = null;


    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            RobotInfo nearbyRobots[] = null;
            if (frame % 8 == 0) {
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = rc.senseNearbyRobots();
            }
            TreeInfo trees[] = rc.senseNearbyTrees();

            MapLocation nextEnemy = null;
            RobotInfo best = null;

            for (RobotInfo e : nearbyRobots) {
                if (e.getTeam().equals(rc.getTeam().opponent())) {
                    if (nextEnemy == null || nextEnemy.distanceTo(myLocation) > e.location.distanceTo(myLocation)) {
                        nextEnemy = e.location;
                        best = e;
                    }
                }
            }
            if (nextEnemy != null) {
                if (lastTarget != null && lastTarget.getID() == best.getID()){
                    float dx = best.location.x - lastTarget.location.x;
                    float dy = best.location.y - lastTarget.location.y;
                    float time = (myLocation.distanceTo(best.location) - best.type.bodyRadius - RobotType.TANK.bodyRadius) / RobotType.TANK.bulletSpeed;
                    nextEnemy = new MapLocation(nextEnemy.x + dx * time, nextEnemy.y + dy * time);
                }
                lastTarget = best;
            } else {
                lastTarget = null;
                map.generateFarTargets(myLocation, 3, 0);
                nextEnemy = map.getTarget(0, myLocation);
            }
            float dist = 10000f;
            if (nextEnemy != null) {
                //System.out.println("Aiming at " + nextEnemy);
                if (rc.canFireSingleShot() && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.TANK.bodyRadius)) {
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                    //System.out.println("Fire!");
                }
            } else {
                ////System.out.println("No target");
                //tryMove(randomDirection());
            }
            if (rc.getRoundNum() - frame > 0) {
                //System.out.println("Tank took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }


        } catch (Exception e) {
            //System.out.println("Tank Exception");
            e.printStackTrace();
        }
    }
}
