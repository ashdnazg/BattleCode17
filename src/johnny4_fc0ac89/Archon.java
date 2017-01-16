package johnny4_fc0ac89;

import battlecode.common.*;
import johnny4_fc0ac89.Map;
import johnny4_fc0ac89.Radio;

import static johnny4_fc0ac89.Util.*;
import static johnny4_fc0ac89.Radio.*;

public class Archon {

    RobotController rc;
    johnny4_fc0ac89.Map map;
    Radio radio;
    Direction lastDirection;
    Direction[] directions = new Direction[12];
    int[] gardenersDir = new int[12];
    Team enemyTeam;
    MapLocation stuckLocation;
    int stuckSince;
    static int gardenersSpawned = 0;

    public Archon(RobotController rc) {
        BuildPlanner.rc = rc;
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.lastDirection = randomDirection();
        // if (radio.getEnemyCounter() == 0){
        // for (MapLocation m : rc.getInitialArchonLocations(rc.getTeam().opponent())){
        // radio.reportEnemy(m, RobotType.ARCHON, 0);
        // }
        // }
        float angle = (float) Math.PI * 2 / directions.length;
        for (int i = 0; i < directions.length; i++) {
            this.directions[i] = new Direction(angle * i);
        }
        this.enemyTeam = rc.getTeam().opponent();
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                //System.out.println("BYTECODE OVERFLOW");
            }
            Clock.yield();
        }
    }


    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            RobotInfo[] nearbyRobots = map.sense();

            boolean inDanger = rc.senseNearbyRobots(RobotType.ARCHON.sensorRadius, enemyTeam).length > 0;
            boolean alarm = radio.getAlarm();
            boolean rich = rc.getTeamBullets() > 200;

            TreeInfo[] trees = senseClosestTrees();

            if (myLocation.distanceTo(stuckLocation) > 6) {
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 69) {
                //System.out.println("Stuck archon reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees) {
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 6) {
                        //System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                }
            }
            boolean hireGardener = false;
            if (rc.getTeamBullets() > RobotType.GARDENER.bulletCost) {
                BuildPlanner.update(nearbyRobots, trees);
                hireGardener = BuildPlanner.hireGardener();
            }

            gardenersSpawned = Math.min(gardenersSpawned, Radio.countAllies(RobotType.ARCHON));

            if (hireGardener) {
                int alternateBuildDir = -1;
                for (int i = 0; i < directions.length; i++) {
                    gardenersDir[i] = 0;
                }
                RobotInfo robot;
                int ii;
                Direction tog;
                for (int i = 0; i < nearbyRobots.length; i++) {
                    robot = nearbyRobots[i];
                    if (robot.getTeam().equals(myTeam) && robot.getType() == RobotType.GARDENER) {
                        tog = myLocation.directionTo(robot.location);
                        for (ii = 0; ii < directions.length; ii++) {
                            if (Math.abs(directions[ii].degreesBetween(tog)) < 90) {
                                gardenersDir[ii]++;
                            }
                        }
                    }
                }
                for (int i = 0; i < directions.length; i++) {
                    if (rc.canHireGardener(directions[i]) && rc.onTheMap(myLocation.add(directions[i], 6)) && (alternateBuildDir < 0 || gardenersDir[alternateBuildDir] > gardenersDir[i])) {
                        alternateBuildDir = i;
                    }
                }
                if (alternateBuildDir >= 0) {

                    //System.out.println("Only " + gardenersDir[alternateBuildDir] + " gardeners in direction " + directions[alternateBuildDir]);
                    rc.hireGardener(directions[alternateBuildDir]);
                    Radio.reportBuild(RobotType.GARDENER);
                    gardenersSpawned++;
                    Radio.reportActiveGardener();
                    //System.out.println("Gardeners spawned: " + gardenersSpawned);
                }else{
                    //System.out.println("Cant build Gardener here");
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
            //System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
