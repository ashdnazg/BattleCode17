package johnny4;

import battlecode.common.*;
import static johnny4.Util.*;

public class Lumberjack {

    RobotController rc;
    Map map;

    public Lumberjack(RobotController rc){
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
            if (rc.getTeamBullets() >= 10000f){
                rc.donate(10000f);
            }
/*
            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+ GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

            if(robots.length > 0 && !rc.hasAttacked()) {
                // Use strike() to hit all nearby robots!
                rc.strike();
            } else {
                // No close robots, so search for robots within sight radius
                robots = rc.senseNearbyRobots(-1,enemy);

                // If there is a robot, move towards it
                if(robots.length > 0) {
                    MapLocation myLocation = rc.getLocation();
                    MapLocation enemyLocation = robots[0].getLocation();
                    Direction toEnemy = myLocation.directionTo(enemyLocation);

                    tryMove(toEnemy);
                } else {
                    // Move Randomly
                    tryMove(randomDirection());
                }
            }*/

        } catch (Exception e) {
            System.out.println("Lumberjack Exception");
            e.printStackTrace();
        }
    }
}
