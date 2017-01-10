package johnny4;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import static johnny4.Util.*;

public class Archon {

    RobotController rc;
    Map map;
    Radio radio;

    public Archon(RobotController rc){
        this.rc = rc;
        this.map = new Map(rc);
        this.radio = new Radio(rc);
    }

    public void run(){
        while(true){
            tick();
            Clock.yield();
        }
    }

    protected void tick(){
        try {
            if (rc.getTeamBullets() >= 10000f){
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            MapLocation myLocation = rc.getLocation();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }

            // Generate a random direction
            Direction dir = randomDirection();

            // Randomly attempt to build a gardener in this direction
            if (rc.canHireGardener(dir) && Math.random() < .01) {
                rc.hireGardener(dir);
            }

            // Move randomly
            tryMove(randomDirection());


        } catch (Exception e) {
            System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
