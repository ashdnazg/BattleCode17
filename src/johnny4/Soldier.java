package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    final boolean ignoreScouts;
    MapLocation lastRandomLocation;
    final MapLocation spawnLocation;
    RobotInfo lastEnemyInfo;
    boolean suicidal;
    // RobotInfo guardener = null;
    // int guardenerID = -1;
    //int lastContactWithGuardener = -1000;
    //final static float MIN_GUARDENER_DIST = RobotType.SOLDIER.sensorRadius + 10;
    final static float MIN_SCOUT_SHOOT_RANGE = 6.5f;
    static float MIN_ARCHON_BULLETS = 80f;
    final int getTargetType;


    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        this.ignoreScouts = rand() < 0.7f;
        this.getTargetType = ignoreScouts ? 7 : 6;
        spawnLocation = stuckLocation;
        suicidal = rc.getRoundNum() < 100;

        // boolean hasGuardener = false;
        // for (RobotInfo ri : rc.senseNearbyRobots()) {
        // if (ri.getTeam().equals(rc.getTeam())) {
        // if (ri.type == RobotType.GARDENER) {
        // guardener = ri;
        // guardenerID = ri.ID;
        // lastContactWithGuardener = rc.getRoundNum();
        // }
        // if (ri.type == RobotType.SOLDIER) {
        // hasGuardener = true;
        // }
        // }
        // }


        float dist = 1e10f;
        float curDist;
        for (MapLocation archonPos : map.enemyArchonPos) {
            curDist = archonPos.distanceTo(stuckLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
        }


        // if (hasGuardener || dist < 45f) {
        // guardener = null;
        // guardenerID = -1;
        // }
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

    float circleDir = 0f;
    MapLocation stuckLocation;
    int stuckSince;
    MapLocation enemyStuckLocation = new MapLocation(0, 0);
    int enemyStuckSince;
    BulletInfo bullets[];
    MapLocation nextLumberjack;
    RobotInfo[] nearbyRobots;
    TreeInfo trees[];
    boolean evasionMode = true;
    float MIN_EVASION_DIST = 8f;

    float money;

    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            money = rc.getTeamBullets();
            bullets = rc.senseNearbyBullets();
            nearbyRobots = null;
            RobotType enemyType = RobotType.SOLDIER;
            int cnt1 = Clock.getBytecodeNum();
            if (frame > 250) {
                suicidal = false;
            }
            if (frame % 8 == 0) {
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = map.sense();//rc.senseNearbyRobots();
            }
            MIN_ARCHON_BULLETS = 125f - Radio.countAllies(RobotType.GARDENER) * 20;

            MapLocation nextEnemy = null;
            RobotInfo nextEnemyInfo = null;
            RobotInfo nextSpotter = null;
            RobotInfo nextGardener = null;
            boolean hasSpotter = false;
            trees = senseClosestTrees();
            TreeInfo hideTree = null;
            for (RobotInfo ri : nearbyRobots) {
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type != RobotType.ARCHON || money > MIN_ARCHON_BULLETS) &&
                        (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius + (enemyType == RobotType.ARCHON ? 10 : 0) + (enemyType == RobotType.LUMBERJACK ? 2.5f : 0) >
                                ri.location.distanceTo(myLocation) * ri.type.strideRadius + (ri.type == RobotType.ARCHON ? 10 : 0) + (enemyType == RobotType.LUMBERJACK ? 2.5f : 0))) {
                    nextEnemy = ri.location;
                    nextEnemyInfo = ri;
                    enemyType = ri.type;
                    if (enemyType == RobotType.SCOUT) {
                        hideTree = rc.canSenseLocation(ri.location) ? rc.senseTreeAtLocation(ri.location) : null;
                    }
                }
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.SCOUT) && (nextSpotter == null || nextSpotter.location.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextSpotter = ri;
                    hasSpotter = true;
                }
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.GARDENER) && (nextGardener == null || nextGardener.location.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextGardener = ri;
                }
            }
            boolean spotterTarget = false;
            if (nextEnemy == null || true) {
                Map.generateFarTargets(map.rc, myLocation, 2, 0);
                MapLocation spotted = Map.getTarget(getTargetType, myLocation);
                if (spotted != null) {
                    if (Util.DEBUG) System.out.println("Found spotter target");
                    hasSpotter = true;
                    if (nextEnemy == null && spotted.distanceTo(myLocation) > RobotType.SOLDIER.sensorRadius && (!suicidal || spotted.distanceTo(myLocation) < 12)) {
                        if (Util.DEBUG) System.out.println("Using spotter target");
                        spotterTarget = true;
                        nextEnemy = spotted;
                        nextEnemyInfo = new RobotInfo(Map.targetType, rc.getTeam().opponent(), Radio.intToType(Map.targetType), nextEnemy, 42, 1, 1);
                        enemyType = nextEnemyInfo.type;
                    }
                }
            }
            if (nextGardener != null && nextEnemy != null/* && nextEnemy.distanceTo(nextGardener.location) < myLocation.distanceTo(nextEnemy) + 2.5*/) {
                MIN_EVASION_DIST = 0f;
                if (Util.DEBUG) System.out.println("Protect gardener");
            } else {
                MIN_EVASION_DIST = 7f + rc.getType().bodyRadius * 3;
                if (enemyType == RobotType.LUMBERJACK) {
                    MIN_EVASION_DIST = 5f;
                }
                if (enemyType == RobotType.GARDENER || enemyType == RobotType.ARCHON) {
                    MIN_EVASION_DIST = 0f;
                }
            }

            if (DEBUG && nextEnemy != null) System.out.println("Next enemy at " + nextEnemy + " of type " + enemyType);
            if (nextEnemyInfo != null && lastEnemyInfo != null && enemyType != RobotType.SCOUT) {
                nextEnemy = predict(nextEnemyInfo, lastEnemyInfo, spotterTarget ? 1 : 0);
            }
            lastEnemyInfo = nextEnemyInfo;

            if (myLocation.distanceTo(stuckLocation) > 7) {
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (nextEnemyInfo == null || nextEnemyInfo.location.distanceTo(enemyStuckLocation) > 4.2) {
                enemyStuckSince = frame;
                enemyStuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 69) {
                if (Util.DEBUG) System.out.println("Stuck soldier reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees) {
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 5.5) {
                        if (Util.DEBUG) System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                    if (t.containedBullets > 0 && rc.canShake(t.location)) {
                        rc.shake(t.location);
                    }
                }
            }
            int cnt2 = Clock.getBytecodeNum();

            boolean longrange = false;
            if (Util.DEBUG && nextEnemy != null)
                System.out.println("Attacking local " + nextEnemyInfo.type + " at " + nextEnemy);
            if (nextEnemy == null) {
                Map.generateFarTargets(map.rc, myLocation, 1000, 0);
                longrange = true;
                if (Util.DEBUG) System.out.println("Using long range target");
                nextEnemy = Map.getTarget(suicidal ? 2 : getTargetType, myLocation);
                if (nextEnemy != null && nextEnemy.distanceTo(myLocation) < 0.6 * RobotType.SOLDIER.sensorRadius) {
                    Radio.deleteEnemyReport(nextEnemy);
                }
                if (DEBUG && nextEnemy != null) System.out.println("Next enemy at " + nextEnemy);
            }
            Movement.init(nearbyRobots, trees, bullets);
            boolean hasMoved = false;
            myLocation = rc.getLocation();
            float dist = 10000f;
            int cnt3 = Clock.getBytecodeNum();
            int cnt4, cnt5, cnt6;
            cnt4 = cnt5 = cnt6 = 0;
            if (nextEnemy != null) {
                dist = longrange ? myLocation.distanceTo(nextEnemy) : myLocation.distanceTo(nextEnemyInfo.location);

                boolean hasFired = longrange;
                Direction fireDir = null;
                float minfiredist = 3f / enemyType.strideRadius + 6 + rc.getType().bodyRadius * 3;
                if (DEBUG) System.out.println("Engagement dist is " + dist + " / " + minfiredist);
                boolean hasLosOnEnemy = !longrange && checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, rc.getType().bodyRadius);
                if (!hasFired && evasionMode && false) {
                    if (hasLosOnEnemy && dist < minfiredist) {
                        if (DEBUG) System.out.println("Firing early " + nextEnemyInfo.location.distanceTo(myLocation));
                        hasFired = tryFire(nextEnemy, enemyType, dist, enemyType.bodyRadius);
                        fireDir = myLocation.directionTo(nextEnemy);
                        if (hasFired) {
                            bullets = rc.senseNearbyBullets();
                        }
                        Movement.lastLOS = frame;
                    } else {
                        if (Util.DEBUG) System.out.println("No LOS / " + (dist < minfiredist));
                    }
                }

                if (enemyType == RobotType.SCOUT) {
                    if (Util.DEBUG) System.out.println("Soldier entering aggression mode");
                    movement.evadeBullets = false;
                    evasionMode = false;
                } else
                /*if (enemyType != RobotType.SCOUT && money < 10 && !evasionMode) {*/ {
                    if (Util.DEBUG) System.out.println("Soldier entering evasion mode");
                    evasionMode = true;
                    movement.evadeBullets = true;
                }/*
                if (!evasionMode && !longrange){
                    movement.evadeBullets = nextEnemyInfo.location.distanceTo(myLocation) < 3.5f;
                }*/
                cnt4 = Clock.getBytecodeNum();
                //movement.MIN_ENEMY_DIST = MIN_EVASION_DIST;
                if (evasionMode && !longrange && (dist < MIN_EVASION_DIST) && hasLosOnEnemy) {
                    MapLocation evadePos;
                    if (myLocation.distanceTo(spawnLocation) - 2 < nextEnemyInfo.location.distanceTo(spawnLocation) && dist > MIN_EVASION_DIST - 3) {
                        if (Util.DEBUG) System.out.println("Evading to spawn");
                        evadePos = nextEnemy.add(nextEnemy.directionTo(spawnLocation), MIN_EVASION_DIST + 5);
                    } else {
                        if (Util.DEBUG) System.out.println("Evading perpendicularly");
                        evadePos = nextEnemy.add(nextEnemy.directionTo(myLocation), MIN_EVASION_DIST + 1);
                    }
                    if (!hasMoved && movement.findPath(evadePos, fireDir)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                } else {
                    if (!hasMoved) {
                        cnt5 = Clock.getBytecodeNum();
                        MapLocation gotopos = longrange ? nextEnemy : nextEnemyInfo.location.add(nextEnemyInfo.location.directionTo(myLocation), nextEnemyInfo.getRadius() + 1.001f);
                        if (hideTree != null && nextEnemyInfo != null && frame - enemyStuckSince > 7 && hideTree.location.distanceTo(myLocation) - hideTree.radius < nextEnemyInfo.location.distanceTo(myLocation) - nextEnemyInfo.type.bodyRadius) {
                            gotopos = hideTree.location.add(hideTree.location.directionTo(nextEnemyInfo.location), hideTree.radius + 1.001f);
                            if (DEBUG) System.out.println("Moving to hide tree");
                        } else {

                            if (DEBUG) System.out.println("Moving close up");
                        }
                        if (movement.findPath(gotopos, fireDir)) {
                            myLocation = rc.getLocation();
                        }
                        hasMoved = true;
                        cnt6 = Clock.getBytecodeNum();
                    }
                }

                if (!hasFired) {
                    if (checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, rc.getType().bodyRadius) && dist < minfiredist) {
                        if (DEBUG) System.out.println("Firing late " + nextEnemyInfo.location.distanceTo(myLocation));
                        hasFired = tryFire(nextEnemy, enemyType, dist, enemyType.bodyRadius);
                        fireDir = myLocation.directionTo(nextEnemy);
                        if (hasFired) {
                            bullets = rc.senseNearbyBullets();
                        }
                        Movement.lastLOS = frame;
                    } else {
                        if (Util.DEBUG) System.out.println("No LOS");
                    }
                }
            } else if (!hasMoved) {
                while (lastRandomLocation.distanceTo(myLocation) < 0.8 * RobotType.SOLDIER.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                    lastRandomLocation = myLocation.add(randomDirection(), 100);
                }
                myLocation = rc.getLocation();
            }

            int cnt7 = Clock.getBytecodeNum();
            if (rc.getRoundNum() - frame > 0) {
                if (Util.DEBUG)
                    System.out.println("Soldier took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
                if (Util.DEBUG)
                    System.out.println("Timings " + cnt1 + " " + cnt2 + " " + cnt3 + " " + cnt4 + " " + cnt5 + " " + cnt6 + " " + cnt7);
            }


        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }

    boolean tryFire(MapLocation nextEnemy, RobotType enemyType, float dist, float radius) throws GameActionException {
        if (!Util.fireAllowed) return false;
        MapLocation myLocation = rc.getLocation();
        if (nextEnemy.equals(myLocation)) return false;
        float maxArc = getMaximumArcOfFire(myLocation, myLocation.directionTo(nextEnemy), nearbyRobots, trees);
        if (DEBUG) System.out.println("Maximum arc is " + (int) (maxArc * 180 / 3.1415) + " degrees");
        if (enemyType == RobotType.SCOUT) {

            if (rc.canFirePentadShot() && maxArc > PENTAD_ARC_PLUSMINUS && (money > 50 || dist < 7f)) {
                rc.firePentadShot(myLocation.directionTo(nextEnemy));
                return true;
            }
            if (rc.canFireTriadShot() && maxArc > TRIAD_ARC_PLUSMINUS) {
                rc.fireTriadShot(myLocation.directionTo(nextEnemy));
                return true;
            }
            if (rc.canFireSingleShot() && dist < 3.0f) {
                rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                return true;
            }

            return false;
        }
        Radio.reportContact();
        if (enemyType == RobotType.ARCHON && money < MIN_ARCHON_BULLETS) {
            return false;
        }
        if (dist - radius < 1.51 + Math.max(0, money / 50f - 2) && maxArc > PENTAD_ARC_PLUSMINUS && rc.canFirePentadShot()) {
            if (Util.DEBUG) System.out.println("Firing pentad");
            rc.firePentadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (/*dist - radius < 2.21 + Math.max(0, money / 20f - 2) && */ dist <= 5 + radius * 2 && maxArc > TRIAD_ARC_PLUSMINUS && rc.canFireTriadShot()) {
            if (Util.DEBUG) System.out.println("Firing triad");
            rc.fireTriadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (rc.canFireSingleShot() && (maxArc > 0.01f || dist < 3)) {
            if (Util.DEBUG) System.out.println("Firing single bullet");
            rc.fireSingleShot(myLocation.directionTo(nextEnemy));
            return true;
        }
        return false;
    }

}
