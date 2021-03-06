package johnny4_20170116;

import battlecode.common.*;

import java.awt.*;

import static johnny4_20170116.Util.checkLineOfFire;
import static johnny4_20170116.Util.randomDirection;
import static johnny4_20170116.Util.tryMove;
import static johnny4_20170116.Util.preTick;
import static johnny4_20170116.Util.*;

public class Tank {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;

    public Tank(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                BYTECODE();
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
            movement.init(nearbyRobots, trees, rc.senseNearbyBullets());

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
                map.generateFarTargets(myLocation, 9, 0);
                nextEnemy = map.getTarget(0, myLocation);
                if (nextEnemy != null && nextEnemy.distanceTo(myLocation) < 0.6 * RobotType.TANK.sensorRadius) {
                    Radio.deleteEnemyReport(nextEnemy);
                }
            }
            float dist = 10000f;
            if (nextEnemy != null) {
                movement.findPath(nextEnemy, null);
                if (Util.DEBUG) System.out.println("Aiming at " + nextEnemy);
                if (rc.canFireSingleShot() && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.TANK.bodyRadius) && !myLocation.equals(nextEnemy)) {
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                    if (Util.DEBUG) System.out.println("Fire!");
                }
            } else {
                //if (Util.DEBUG) System.out.println("No target");
                //tryMove(randomDirection());
            }
            if (rc.getRoundNum() - frame > 0) {
                if (Util.DEBUG) System.out.println("Tank took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }


        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Tank Exception");
            e.printStackTrace();
        }
    }
}
