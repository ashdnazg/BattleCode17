package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Archon {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    Direction lastDirection;
    Direction[] directions = new Direction[24];
    Team enemyTeam;

    MapLocation lastRandomLocation;
    MapLocation stuckLocation;
    int stuckSince;
    int lastGardener = -1000;
    MapLocation lastGardenerPos = null;
    Team myTeam;
    static int gardenersHired = 0;

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
        int touchingFriendArchons = 0;
        int touchingEnemyArchons = 0;
        for (MapLocation archonPos : map.enemyArchonPos) {
            curDist = archonPos.distanceTo(stuckLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
            if (curDist < 2 * RobotType.ARCHON.bodyRadius + 0.02) {
                touchingEnemyArchons++;
            }
        }
        for (MapLocation archonPos : map.ourArchonPos) {
            curDist = archonPos.distanceTo(stuckLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
            if (curDist < 2 * RobotType.ARCHON.bodyRadius + 0.02 && curDist > 0) {
                touchingFriendArchons++;
            }
        }
        if (touchingFriendArchons > 0 && touchingEnemyArchons == 0 && rc.senseNearbyTrees(RobotType.ARCHON.bodyRadius + 0.01f).length > 0) {
            rc.disintegrate();
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

    int timesTouched = 0;


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
            int touchingFriendArchons = 0;
            int touchingEnemyArchons = 0;
            for (RobotInfo r : nearbyRobots) {
                if (r.getTeam().equals(myTeam) && r.type == RobotType.GARDENER) {
                    lastGardener = frame;
                    lastRandomLocation = myLocation.add(r.location.directionTo(myLocation), 10);
                    lastGardenerPos = r.location;
                    break;
                }
                if (r.type == RobotType.ARCHON && r.location.distanceTo(myLocation) < 2 * RobotType.ARCHON.bodyRadius + 0.02) {
                    if (r.getTeam().equals(myTeam)) {
                        touchingFriendArchons++;
                    } else {
                        touchingEnemyArchons++;
                    }
                }
            }
            if (touchingFriendArchons > 0 && touchingEnemyArchons == 0 && timesTouched++ > 5) {
                rc.disintegrate();
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
            /*if (eligibleSpot && rc.canHireGardener(oppositeDir) && hireGardener) {
                rc.hireGardener(oppositeDir);
                Radio.reportBuild(RobotType.GARDENER);
                gardenersHired++;
            } else */if (hireGardener) {
                boolean[] blockedDir = new boolean[directions.length];
                for (TreeInfo t : trees) {
                    int nextDir = 0;
                    float thisAngle = myLocation.directionTo(t.location).getAngleDegrees();
                    for (int i = 0; i < directions.length; i++) {
                        if (directions[i].getAngleDegrees() < thisAngle) nextDir = i;
                    }
                    blockedDir[nextDir] = true;
                    blockedDir[(nextDir + 1) % directions.length] = true;
                }
                Direction buildDir = null;
                Direction alternateBuildDir = null;
                int freeDirs = 0;
                for (int i = 0; i < directions.length; i++) {
                    boolean valid = true;
                    for (MapLocation archon : Map.enemyArchonPos) {
                        if (Math.abs(directions[i].degreesBetween(myLocation.directionTo(archon))) < 120 - Map.enemyArchonPos.length * 30) {
                            valid = false;
                        }
                    }
                    if (valid && rc.canHireGardener(directions[i]) && alternateBuildDir == null) {
                        alternateBuildDir = directions[i];
                        //System.out.println(alternateBuildDir + " found");
                    }
                }
                for (int i = 0; i < directions.length; i++) {
                    if (rc.canHireGardener(directions[i]) && alternateBuildDir == null) {
                        alternateBuildDir = directions[i];
                    }
                }
                if (freeDirs == 1 && alternateBuildDir != null) {
                    freeDirs = 2;
                    buildDir = alternateBuildDir;
                }
                if (alternateBuildDir != null) {
                    rc.hireGardener(alternateBuildDir);
                    gardenersHired++;
                    Radio.reportBuild(RobotType.GARDENER);
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
            if (!tryingToShake && frame - lastGardener < 30) {
                if (!movement.findPath(myLocation.add(lastGardenerPos.directionTo(myLocation), 10), null)) {
                    int iterations = 0;
                    while ((lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.ARCHON.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) && iterations++ < 1) {
                        lastRandomLocation = myLocation.add(randomDirection(), 20);
                    }
                }

            }
        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Archon Exception");
            e.printStackTrace();
            EXCEPTION();
        }
    }
}
