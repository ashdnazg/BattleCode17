package johnny4_20170111_2;

import battlecode.common.*;

import static johnny4_20170111_2.Util.randomDirection;
import static johnny4_20170111_2.Util.tryMove;

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


            //map.sense();
            if (frame % 8 == 0) {

            }

            MapLocation nextEnemy = map.getTarget(myLocation);
            float dist = 10000f;
            if (nextEnemy != null){
                dist = myLocation.distanceTo(nextEnemy);
                if (dist < RobotType.SCOUT.sensorRadius ){
                    tryMove(nextEnemy.directionTo(myLocation));
                }else {
                    tryMove(myLocation.directionTo(nextEnemy));
                }

                if (rc.canFireSingleShot()) {
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                }
            } else  {
                tryMove(randomDirection());
            }
            if (rc.getRoundNum() - frame > 0){
                //System.out.println("Tank took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }


        } catch (Exception e) {
            //System.out.println("Tank Exception");
            //e.printStackTrace();
        }
    }
}
