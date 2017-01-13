package johnny4_prerefactor;

import battlecode.common.*;

import static johnny4_prerefactor.Util.*;

public class Archon {

    RobotController rc;
    Map map;
    Radio radio;
    Direction lastDirection;
    Direction[] directions = new Direction[12];
    Team enemyTeam;
    MapLocation stuckLocation;
    int stuckSince;

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
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
    }

    public void run(){
        while(true){
            tick();
            Clock.yield();
        }
    }

    protected void tick(){
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            map.sense();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }

            boolean inDanger = rc.senseNearbyRobots(RobotType.ARCHON.sensorRadius, enemyTeam).length > 0;
            boolean alarm = radio.getAlarm();
            boolean rich = rc.getTeamBullets() > 200;

            TreeInfo[] trees = rc.senseNearbyTrees(6);

            if (myLocation.distanceTo(stuckLocation) > 6){
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 69){
                System.out.println("Stuck archon reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees){
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 6){
                        System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                }
            }

            Direction oppositeDir = lastDirection.opposite();
            MapLocation potentialSpot = myLocation.add(oppositeDir, 3.0f);
            MapLocation forwardSpot = myLocation.add(lastDirection, 2.0f);
            boolean eligibleSpot = rc.onTheMap(forwardSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(forwardSpot, 2.0f)/* freeDirs > 1*/;
            boolean goodSpot = rc.onTheMap(potentialSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(potentialSpot, 3.0f);
            if (eligibleSpot && rc.canHireGardener(oppositeDir) &&
                (radio.countAllies(RobotType.GARDENER) == 0 || radio.countAllies(RobotType.SCOUT) > 0 && frame > 100) &&
                (!alarm || rich)) {
                rc.hireGardener(oppositeDir);
            } else if (inDanger && rich) {
                boolean[] blockedDir = new boolean[directions.length];
                for (TreeInfo t : trees){
                    int nextDir = 0;
                    float thisAngle = myLocation.directionTo(t.location).getAngleDegrees();
                    for (int i = 0; i < directions.length; i++){
                        if (directions[i].getAngleDegrees() < thisAngle) nextDir = i;
                    }
                    blockedDir[nextDir] = true;
                    blockedDir[(nextDir + 1) % directions.length] = true;
                }
                Direction buildDir = null;
                Direction alternateBuildDir = null;
                int freeDirs = 0;
                for (int i = 0; i < directions.length; i++){
/*
                    if (!blockedDir[i]){
                        buildDir = directions[i];
                        freeDirs ++;
                        //System.out.println("Direction " + directions[i] + " is free");
                    }else */if (rc.canHireGardener(directions[i])){
                        alternateBuildDir = directions[i];
                    }
                }
                if (freeDirs == 1 && alternateBuildDir != null){
                    freeDirs = 2;
                    buildDir = alternateBuildDir;
                }
                if (alternateBuildDir != null) {

                    rc.hireGardener(alternateBuildDir);
                }
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
