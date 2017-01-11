package johnny4_20170110;

import battlecode.common.*;

import java.util.Optional;

import static johnny4_20170110.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;

    public Soldier(RobotController rc) {
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
            MapLocation myLocation = rc.getLocation();


            MapLocation nextEnemy = null;
            for (RobotInfo r : rc.senseNearbyRobots()) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                    nextEnemy = r.location;
                }
            }
            boolean longrange = false;
            if (nextEnemy == null) {
                longrange = true;
                nextEnemy = map.getTarget(myLocation);
            }
            float dist = 10000f;
            if (nextEnemy != null){
                dist = myLocation.distanceTo(nextEnemy);
                if (dist < 0.5 * RobotType.SCOUT.sensorRadius ){
                    if (!longrange && rc.getTeamBullets() > 400 && rc.canFirePentadShot()) {
                        rc.firePentadShot(myLocation.directionTo(nextEnemy));
                    }
                    if(!longrange && rc.getTeamBullets() > 100 && rc.canFireTriadShot()){
                        rc.fireTriadShot(myLocation.directionTo(nextEnemy));
                    }
                    tryMove(nextEnemy.directionTo(myLocation));
                }else {
                    tryMove(myLocation.directionTo(nextEnemy));
                }

                if (!longrange && rc.canFireSingleShot()) {
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                }
            } else  {
                tryMove(randomDirection());
            }
            if (rc.getRoundNum() - frame > 0 && !longrange){
                System.out.println("Soldier took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
            }


        } catch (Exception e) {
            System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }
}
