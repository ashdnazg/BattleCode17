package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Archon {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    Direction lastDirection;
    Direction[] directions = new Direction[23];
    Team enemyTeam;

    MapLocation lastRandomLocation;
    MapLocation stuckLocation;
    int stuckSince;
    int lastGardener = -1000;
    Team myTeam;

    public Archon(RobotController rc) {
        BuildPlanner.rc = rc;
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        this.lastDirection = randomDirection();
        float angle = (float) Math.PI * 2 / directions.length;
        for (int i = 0; i < directions.length; i++) {
            this.directions[i] = new Direction(angle * i);
        }
        this.myTeam = rc.getTeam();
        this.enemyTeam = rc.getTeam().opponent();
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        BuildPlanner.init();
        lastRandomLocation = rc.getLocation();
        float dist = 1e10f;
        float curDist;
        for (MapLocation archonPos : map.enemyArchonPos) {
            curDist = archonPos.distanceTo(stuckLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
        }
        lastRandomLocation = rc.getLocation();
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
            BulletInfo bullets[] = rc.senseNearbyBullets();

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
                    if (t.location.distanceTo(myLocation) - t.radius < 6) {
                        if (Util.DEBUG) System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                }
            }
            for (RobotInfo r: nearbyRobots){
                if (r.getTeam().equals(myTeam) && r.type == RobotType.GARDENER) lastGardener = frame;
            }

            boolean hireGardener = false;
            BuildPlanner.update(nearbyRobots, trees);
            if (rc.getTeamBullets() > RobotType.GARDENER.bulletCost) {
                hireGardener = BuildPlanner.hireGardener();
            }

            Direction oppositeDir = lastDirection.opposite();
            MapLocation potentialSpot = myLocation.add(oppositeDir, 3.0f);
            MapLocation forwardSpot = myLocation.add(lastDirection, 2.0f);
            boolean eligibleSpot = rc.onTheMap(forwardSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(forwardSpot, 2.0f)/* freeDirs > 1*/;
            boolean goodSpot = rc.onTheMap(potentialSpot, 3.0f) && !rc.isCircleOccupiedExceptByThisRobot(potentialSpot, 3.0f);
            if (eligibleSpot && rc.canHireGardener(oppositeDir) && hireGardener) {
                rc.hireGardener(oppositeDir);
            } else if (hireGardener) {
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
            Movement.init(nearbyRobots, trees, bullets);

            // shake trees before frame 100
            boolean tryingToShake = false;
            if (frame < 100) {
                Direction dir;
                for (TreeInfo t : trees) {
                    if (t.containedBullets > 0) {
                        dir = myLocation.directionTo(t.location);
                        if (movement.findPath(t.location.add(dir, -t.radius - RobotType.ARCHON.bodyRadius), null)) {
                            tryingToShake = true;
                        }
                        if (rc.canShake(t.location)) {
                            rc.shake(t.location);
                            tryingToShake = true;
                        }
                        if (tryingToShake) {
                            break;
                        }
                    }
                }
            }

            // try to stay in good spots
            /*if (goodSpot && eligibleSpot) {
                return;
            }*/

            // Move randomly
            if (!tryingToShake && frame - lastGardener < 10) {
                if (!movement.findPath(myLocation, null)){
                    while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.ARCHON.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                        lastRandomLocation = myLocation.add(randomDirection(), 10);
                    }
                }

            }
        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Archon Exception");
            e.printStackTrace();
        }
    }
}
