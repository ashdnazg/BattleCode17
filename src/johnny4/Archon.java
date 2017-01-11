package johnny4;

import battlecode.common.*;

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
        if (radio.getEnemyCounter() == 0){
            for (MapLocation m : rc.getInitialArchonLocations(rc.getTeam().opponent())){
                radio.reportEnemy(m, RobotType.ARCHON, 0);
            }
        }
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

            Direction oppositeDir = lastDirection.opposite();
            MapLocation potentialSpot = myLocation.add(oppositeDir, 3.0f);
            MapLocation forwardSpot = myLocation.add(lastDirection, 2.0f);
            boolean eligibleSpot = rc.onTheMap(forwardSpot, 2.0f) && !rc.isCircleOccupiedExceptByThisRobot(forwardSpot, 2.0f);
            boolean goodSpot = rc.onTheMap(potentialSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(potentialSpot, 3.0f);
            if (eligibleSpot && rc.canHireGardener(oppositeDir)) {
                rc.hireGardener(oppositeDir);
            }

            // try to stay in good spots
            if (goodSpot) {
                return;
            }


            // Move randomly
            while (!rc.canMove(lastDirection) && Math.random() > 0.01) {
                lastDirection = lastDirection.rotateRightDegrees((float)Math.random() * 60);
            }
            if (rc.canMove(lastDirection)) {
                rc.move(lastDirection);
            }
        } catch (Exception e) {
            System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
