package johnny4;

import battlecode.common.*;

import static johnny4.Util.checkLineOfFire;
import static johnny4.Util.randomDirection;
import static johnny4.Util.tryMove;

public class Tank {

    RobotController rc;
    Map map;
    Radio radio;

    public Tank(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }


    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            RobotInfo nearbyRobots[] = null;
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = rc.senseNearbyRobots();
            }
            TreeInfo trees[] = rc.senseNearbyTrees();


            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
                map.sense();

            }

            MapLocation nextEnemy = map.getTarget(myLocation, 0, 9);
            float dist = 10000f;
            if (nextEnemy != null){
                System.out.println("Aiming at " + nextEnemy);
                if (rc.canFireSingleShot() && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.TANK.bodyRadius)) {
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                    System.out.println("Fire!");
                }
            } else  {
                //System.out.println("No target");
                //tryMove(randomDirection());
            }
            if (rc.getRoundNum() - frame > 0){
                System.out.println("Tank took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }


        } catch (Exception e) {
            System.out.println("Tank Exception");
            e.printStackTrace();
        }
    }
}
