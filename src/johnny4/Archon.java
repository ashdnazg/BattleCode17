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
    Direction lastDirection;

    public Archon(RobotController rc){
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.lastDirection = randomDirection();
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
            map.sense();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }

            // Generate a random direction

            // Randomly attempt to build a gardener in this direction
            Direction r = lastDirection.rotateLeftDegrees(90.0f);
            Direction l = lastDirection.rotateLeftDegrees(90.0f);

            if (rc.canHireGardener(r)) {
                rc.hireGardener(r);
            } else if (rc.canHireGardener(l)) {
                rc.hireGardener(l);
            }

            // Move randomly
            while (!tryMove(lastDirection) && Math.random() > 0.02) {
                lastDirection = lastDirection.rotateRightDegrees((float)Math.random() * 60);
            }
        } catch (Exception e) {
            System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
