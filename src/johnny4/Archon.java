package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Archon {

    RobotController rc;
    Map map;
    Radio radio;
    Direction lastDirection;
    Direction[] directions = new Direction[12];
    Team enemyTeam;

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
        float angle = (float) Math.PI * 2/ directions.length ;
        for (int i = 0; i < directions.length; i++) {
            this.directions[i] = new Direction(angle * i);
        }
        this.enemyTeam = rc.getTeam().opponent();
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
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            map.sense();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }

            boolean alarm = radio.getAlarm() || rc.senseNearbyRobots(RobotType.ARCHON.sensorRadius, enemyTeam).length > 0;
            boolean rich = rc.getTeamBullets() > 400;

            TreeInfo[] trees = rc.senseNearbyTrees(6);


            boolean[] blockedDir = new boolean[directions.length];
            for (TreeInfo t : trees){
                int nextDir = 0;
                float thisAngle = myLocation.directionTo(t.location).getAngleDegrees();
                for (int i = 0; i < directions.length; i++){
                    if (directions[i].getAngleDegrees() < thisAngle) nextDir = i;
                }
                blockedDir[nextDir] = true;
                blockedDir[(nextDir + 1) % directions.length] = true;
            }/*
            Direction buildDir = null;
            Direction alternateBuildDir = null;
            int freeDirs = 0;
            for (int i = 0; i < directions.length; i++){

                if (!blockedDir[i]){
                    buildDir = directions[i];
                    freeDirs ++;
                    System.out.println("Direction " + directions[i] + " is free");
                }else if (rc.canHireGardener(directions[i])){
                    alternateBuildDir = directions[i];
                }
            }
            if (freeDirs == 1 && alternateBuildDir != null){
                freeDirs = 2;
                buildDir = alternateBuildDir;
            }*/

            Direction oppositeDir = lastDirection.opposite();
            MapLocation potentialSpot = myLocation.add(oppositeDir, 3.0f);
            MapLocation forwardSpot = myLocation.add(lastDirection, 2.0f);
            boolean eligibleSpot = rc.onTheMap(forwardSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(forwardSpot, 2.0f)/* freeDirs > 1*/;
            boolean goodSpot = rc.onTheMap(potentialSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(potentialSpot, 3.0f);
            if (eligibleSpot && rc.canHireGardener(oppositeDir) && (radio.countAllies(RobotType.GARDENER) == 0 || radio.countAllies(RobotType.SCOUT) > 0) && (!alarm || rich)) {
                rc.hireGardener(oppositeDir);
            }

            // try to stay in good spots
            if (goodSpot && eligibleSpot) {
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
