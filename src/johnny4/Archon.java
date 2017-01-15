package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;
import static johnny4.Radio.*;

public class Archon {

    RobotController rc;
    Map map;
    Radio radio;
    Direction lastDirection;
    Direction[] directions = new Direction[12];
    boolean[] blockedDir = new boolean[12];
    Team enemyTeam;
    MapLocation stuckLocation;
    int stuckSince;

    public Archon(RobotController rc){
        BuildPlanner.rc = rc;
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.lastDirection = randomDirection();
        if (radio.getEnemyCounter() == 0){
            for (MapLocation m : rc.getInitialArchonLocations(rc.getTeam().opponent())){
                //radio.reportEnemy(m, RobotType.ARCHON, 0);
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
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                System.out.println("BYTECODE OVERFLOW");
            }
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

            boolean hireGardener = BuildPlanner.hireGardener();

            if (hireGardener) {
                blockedDir[0] = false;
                blockedDir[1] = false;
                blockedDir[2] = false;
                blockedDir[3] = false;
                blockedDir[4] = false;
                blockedDir[5] = false;
                blockedDir[6] = false;
                blockedDir[7] = false;
                blockedDir[8] = false;
                blockedDir[9] = false;
                blockedDir[10] = false;
                blockedDir[11] = false;

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
                    Radio.reportBuild(RobotType.GARDENER);
                }
            }


            // Move randomly
            // while (!rc.canMove(lastDirection) && rand() > 0.01) {
                // lastDirection = lastDirection.rotateRightDegrees(rand() * 60);
            // }
            // if (rc.canMove(lastDirection)) {
                // rc.move(lastDirection);
            // }
        } catch (Exception e) {
            System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
