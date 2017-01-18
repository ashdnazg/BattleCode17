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
                BYTECODE();
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

            TreeInfo[] trees = senseClosestTrees();

            if (myLocation.distanceTo(stuckLocation) > 6) {
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 69) {
                if (Util.DEBUG) System.out.println("Stuck archon reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees) {
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 6) {
                        if (Util.DEBUG) System.out.println("Reported tree at " + t.location);
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

                    if (Util.DEBUG) System.out.println("Only " + gardenersDir[alternateBuildDir] + " gardeners in direction " + directions[alternateBuildDir]);
                    rc.hireGardener(directions[alternateBuildDir]);
                    Radio.reportBuild(RobotType.GARDENER);
                    gardenersSpawned++;
                    Radio.reportActiveGardener();
                    if (Util.DEBUG) System.out.println("Gardeners spawned: " + gardenersSpawned);
                }else{
                    if (Util.DEBUG) System.out.println("Cant build Gardener here");
                }

            }
            Direction dir;
            boolean b = false;
            for (TreeInfo t : trees) {
                if (t.containedBullets > 0) {
                    dir = myLocation.directionTo(t.location);
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        b = true;
                    }
                    if (rc.canShake(t.location)) {
                        rc.shake(t.location);
                        b = true;
                    }
                    if (b) {
                        break;
                    }
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
            if (Util.DEBUG) System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
