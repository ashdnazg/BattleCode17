package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Gardener {

    RobotController rc;
    Map map;

    public Gardener(RobotController rc){
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
            // Listen for home archon's location
            int xPos = rc.readBroadcast(0);
            int yPos = rc.readBroadcast(1);
            MapLocation archonLoc = new MapLocation(xPos,yPos);

            // Generate a random direction
            Direction dir = randomDirection();

            // Randomly attempt to build a soldier or lumberjack in this direction
            if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .02) {
                rc.buildRobot(RobotType.SOLDIER, dir);
            }

            // Move randomly
            tryMove(randomDirection());

        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }
}
