package johnny4;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import static johnny4.Util.*;

public class Archon {

    RobotController rc;
    Map map;

    public Archon(RobotController rc){
        this.rc = rc;
        this.map = new Map(rc);
    }

    public void run(){
        while(true){
            tick();
            Clock.yield();
        }
    }

    protected void tick(){
        try {

            // Generate a random direction
            Direction dir = randomDirection();

            // Randomly attempt to build a gardener in this direction
            if (rc.canHireGardener(dir) && Math.random() < .01) {
                rc.hireGardener(dir);
            }

            // Move randomly
            tryMove(randomDirection());

            // Broadcast archon's location for other robots on the team to know
            MapLocation myLocation = rc.getLocation();
            rc.broadcast(0,(int)myLocation.x);
            rc.broadcast(1,(int)myLocation.y);


        } catch (Exception e) {
            System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
