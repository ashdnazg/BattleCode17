package johnny4_20170130;

import battlecode.common.*;

import static johnny4_20170130.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    boolean ignoreScouts;
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
    int lastRandomLocTime = 10000;
    static int nearbySoldiers = 0;


    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        this.ignoreScouts = true;
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
    int SUICIDAL_END = 250;
    RobotInfo nextGardener;
    int nearbyEnemySoldiers = 0;

    float money;

    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            ignoreScouts = frame > 200 || !Radio.getAlarm();
            MapLocation myLocation = rc.getLocation();
            money = rc.getTeamBullets();
            bullets = rc.senseNearbyBullets();
            nearbyRobots = null;
            RobotType enemyType = RobotType.SOLDIER;
            int cnt1 = Clock.getBytecodeNum();
            if (frame > SUICIDAL_END) {
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
            MapLocation nextEnemyScout = null;
            RobotInfo nextEnemyInfo = null;
            RobotInfo nextSpotter = null;
            nextGardener = null;
            nearbySoldiers = nearbyEnemySoldiers = 0;
            boolean hasSpotter = false;
            trees = senseClosestTrees();
            TreeInfo hideTree = null;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.GARDENER) && (nextGardener == null || nextGardener.location.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextGardener = ri;
                }
            }
            for (RobotInfo ri : nearbyRobots) {
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.ARCHON)) {
                    SUICIDAL_END = Math.min(SUICIDAL_END, frame + 15);
                }
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.SOLDIER)) {
                    nearbySoldiers++;
                }
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.SOLDIER)) {
                    nearbyEnemySoldiers++;
                }
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type != RobotType.ARCHON || money > MIN_ARCHON_BULLETS || ri.health / ri.type.maxHealth < 0.2f) &&
                        (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius + (enemyType == RobotType.ARCHON ? 10 : 0) + (enemyType == RobotType.LUMBERJACK ? 2.5f : 0) >
                                ri.location.distanceTo(myLocation) * ri.type.strideRadius + (ri.type == RobotType.ARCHON ? 10 : 0) + (enemyType == RobotType.LUMBERJACK ? 2.5f : 0))) {
                    if ((ri.type != RobotType.SCOUT || !ignoreScouts || nextGardener != null)) {
                        nextEnemy = ri.location;
                        nextEnemyInfo = ri;
                        enemyType = ri.type;
                        if (enemyType == RobotType.SCOUT) {
                            hideTree = rc.canSenseLocation(ri.location) ? rc.senseTreeAtLocation(ri.location) : null;
                        }
                    } else {
                        nextEnemyScout = ri.location;
                    }
                }
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.SCOUT) && (nextSpotter == null || nextSpotter.location.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextSpotter = ri;
                    hasSpotter = true;
                }
            }
            boolean spotterTarget = false;
            Map.generateFarTargets(map.rc, myLocation, 5, 0);
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
            if (nextGardener != null && nextEnemy != null && nextEnemy.distanceTo(nextGardener.location) < myLocation.distanceTo(nextEnemy) + 1) {
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
            if (nextEnemy == null) {
                Movement.MIN_FRIENDLY_SOLDIER_DIST = 10f;
            } else {
                Movement.MIN_FRIENDLY_SOLDIER_DIST = 4f;
            }
            Movement.init(nearbyRobots, trees, bullets);
            boolean hasMoved = false;
            myLocation = rc.getLocation();
            float dist = 10000f;
            int cnt3 = Clock.getBytecodeNum();
            int cnt4, cnt5, cnt6;
            cnt4 = cnt5 = cnt6 = 0;
            boolean hasFired = false;
            if (nextEnemy != null) {
                dist = longrange ? myLocation.distanceTo(nextEnemy) : myLocation.distanceTo(nextEnemyInfo.location);

                Direction fireDir = null;
                float minfiredist = 3f / enemyType.strideRadius + 6 + rc.getType().bodyRadius * 4;
                if (DEBUG) System.out.println("Engagement dist is " + dist + " / " + minfiredist);
                boolean hasLosOnEnemy = !longrange && checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, rc.getType().bodyRadius);

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
                if (evasionMode && !longrange && (dist < MIN_EVASION_DIST) && (hasLosOnEnemy || Radio.countAllies(RobotType.SOLDIER) <= Radio.countEnemies(RobotType.SOLDIER))) { //retreat when low on units
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

                if (!hasFired && !longrange) {
                    if (checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, rc.getType().bodyRadius) && dist < minfiredist) {
                        if (DEBUG) System.out.println("Firing late " + nextEnemyInfo.location.distanceTo(myLocation));
                        hasFired = tryFire(nextEnemy, enemyType, dist, enemyType.bodyRadius);
                        if (hasFired) {
                            Movement.lastLOS = frame;
                        }
                    } else {
                        if (Util.DEBUG) System.out.println("No LOS");
                    }
                }
            } else if (!hasMoved) {
                while (lastRandomLocation.distanceTo(myLocation) < 0.8 * RobotType.SOLDIER.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || frame - lastRandomLocTime > 69 || !movement.findPath(lastRandomLocation, null)) {
                    lastRandomLocation = myLocation.add(randomDirection(), 100);
                    lastRandomLocTime = frame;
                }
                myLocation = rc.getLocation();
            }

            if (!hasFired && nextEnemyScout != null) {
                if (checkLineOfFire(myLocation, nextEnemyScout, trees, nearbyRobots, rc.getType().bodyRadius)) {
                    if (DEBUG) System.out.println("Firing at scout ");
                    hasFired = tryFire(nextEnemyScout, RobotType.SCOUT, nextEnemyScout.distanceTo(myLocation), RobotType.SCOUT.bodyRadius);
                } else {
                    if (Util.DEBUG) System.out.println("No LOS on scout");
                }
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
        /*if (money < 120 && rc.getTreeCount() < 6 && Radio.countActiveGardeners() > 0 && nextGardener == null && rc.getRoundNum() % (1 + Radio.countAllies(RobotType.SOLDIER)) > 0 && dist >= 7 ){
            return false;
        }*/
        MapLocation myLocation = rc.getLocation();
        if (nextEnemy.equals(myLocation)) return false;
        Direction firedir = myLocation.directionTo(nextEnemy); //.rotateLeftDegrees((2 * rand() - 1f) * Math.min(3, nearbySoldiers + 2) * 1.6f * enemyType.strideRadius);
        // if (Util.DEBUG)
            // System.out.println("Random offset of +- " + (Math.min(3, nearbySoldiers + 2) * 2 * enemyType.strideRadius) + " degrees");
        float maxArc = getMaximumArcOfFire(myLocation, firedir, nearbyRobots, trees);
        if (DEBUG) System.out.println("Maximum arc is " + (int) (maxArc * 180 / 3.1415) + " degrees");
        if (enemyType == RobotType.SCOUT) {
            if (dist > 5f && Util.tooManyTrees && money < 110) return false;
            if (rc.canFirePentadShot() && maxArc > PENTAD_ARC_PLUSMINUS && (money > 20 || dist < 7f)) {
                rc.firePentadShot(firedir);
                return true;
            }
            if (rc.canFireTriadShot() && maxArc > TRIAD_ARC_PLUSMINUS) {
                rc.fireTriadShot(firedir);
                return true;
            }
            if (rc.canFireSingleShot() && maxArc > 0.001) {
                rc.fireSingleShot(firedir);
                return true;
            }

            return false;
        }
        Radio.reportContact();
        if (dist - radius < 1.51 + Math.max(0, money / 50f - 2) + Math.max(0, 4 * nearbyEnemySoldiers - 3) && (maxArc > PENTAD_ARC_PLUSMINUS || dist < 3) && rc.canFirePentadShot()) {
            if (Util.DEBUG) System.out.println("Firing pentad");
            rc.firePentadShot(firedir);
            return true;
        } else if (/*dist - radius < 2.21 + Math.max(0, money / 20f - 2) && */ dist <= 8 * enemyType.strideRadius - 3 + radius * 3 + money / 50f && (maxArc > TRIAD_ARC_PLUSMINUS || dist < 4) && rc.canFireTriadShot()) {
            if (Util.DEBUG) System.out.println("Firing triad");
            rc.fireTriadShot(firedir);
            return true;
        } else if (rc.canFireSingleShot()/* && (dist < 11 - 6 * enemyType.strideRadius || maxArc <= TRIAD_ARC_PLUSMINUS)*/ && (maxArc > 0.01f || dist < 5)) {
            if (Util.DEBUG) System.out.println("Firing single bullet");
            rc.fireSingleShot(firedir);
            return true;
        }
        return false;
    }

}
