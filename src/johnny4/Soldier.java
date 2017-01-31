package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

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
    int stayWithArchon = 0;


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


        lastRandomLocation = map.enemyArchonPos[(int) (map.enemyArchonPos.length * rand())];


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
    int ignoreBTrees = 0;
    boolean spotterTarget = false;
    boolean lastNoBullets = true;

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
            RobotInfo nextEnemySoldier = null;
            RobotInfo nextSpotter = null;
            nextGardener = null;
            nearbySoldiers = nearbyEnemySoldiers = 0;
            boolean hasSpotter = false;
            trees = senseClosestTrees();
            TreeInfo hideTree = null;
            TreeInfo nextBulletTree = null;
            for (TreeInfo t : trees) {
                if (t.getTeam().equals(rc.getTeam().opponent()) && t.health / t.getMaxHealth() > 0.85f && (nextBulletTree == null || nextBulletTree.location.distanceTo(myLocation) < t.location.distanceTo(myLocation))) {
                    nextBulletTree = t;
                    break;
                }
            }
            for (RobotInfo ri : nearbyRobots) {
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.GARDENER) && (nextGardener == null || nextGardener.location.distanceTo(myLocation) > ri.location.distanceTo(myLocation))) {
                    nextGardener = ri;
                }
            }
            int minAllyID = 100000;
            for (RobotInfo ri : nearbyRobots) {
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.ARCHON)) {
                    SUICIDAL_END = Math.min(SUICIDAL_END, frame + 45);
                }
                if (ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.SOLDIER)) {
                    nearbySoldiers++;
                    minAllyID = Math.min(minAllyID, ri.getID());
                }
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type == RobotType.SOLDIER)) {
                    nearbyEnemySoldiers++;
                }
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type != RobotType.ARCHON || frame - stayWithArchon > 50) &&
                        (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius + (enemyType == RobotType.ARCHON ? 10 : 0) + (enemyType == RobotType.GARDENER && suicidal ? -10 : 0) + (enemyType == RobotType.LUMBERJACK ? 3.7f : 0) >
                                ri.location.distanceTo(myLocation) * ri.type.strideRadius + (ri.type == RobotType.ARCHON ? 10 : 0) + (ri.type == RobotType.GARDENER && suicidal ? -10 : 0) + (ri.type == RobotType.LUMBERJACK ? 3.7f : 0))) {
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
            if (nextEnemyInfo != null && nextEnemyInfo.type == RobotType.ARCHON && nearbySoldiers > 0 && minAllyID < rc.getID()) {
                stayWithArchon = frame; //ignore archons for 50 frames
            }
            if (Radio.countAllies(RobotType.SOLDIER) <= map.enemyArchonPos.length) {
                stayWithArchon = Math.max(stayWithArchon, frame - 45);
            }
            spotterTarget = false;
            Map.generateFarTargets(map.rc, myLocation, 5, 0);
            MapLocation spotted = Map.getTarget(getTargetType, myLocation);
            if (spotted != null && spotted.distanceTo(myLocation) < 20) {
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
            if ((nextEnemy != null && nextEnemyInfo.type != RobotType.GARDENER || nextEnemy == null) && suicidal && nextBulletTree != null && frame - ignoreBTrees > 20) {
                nextEnemy = nextBulletTree.location.add(myLocation.directionTo(nextBulletTree.location), 2);
                nextEnemyInfo = new RobotInfo(4354, rc.getTeam().opponent(), RobotType.GARDENER, nextEnemy, 42, 1, 1);
                enemyType = nextEnemyInfo.type;
                if (Util.DEBUG) System.out.println("Going for fake gardener");
                if (myLocation.distanceTo(nextBulletTree.location) < 3) ignoreBTrees = frame;
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
                }
            }
            int cnt2 = Clock.getBytecodeNum();

            boolean longrange = false;
            if (Util.DEBUG && nextEnemy != null)
                System.out.println("Attacking local " + nextEnemyInfo.type + " at " + nextEnemy);
            if (nextEnemy == null) {
                longrange = true;
                if (nextBulletTree != null && frame - ignoreBTrees > 50) {
                    if (Util.DEBUG) System.out.println("Using bullet tree target");
                    nextEnemy = nextBulletTree.location;
                    if (nextEnemy.distanceTo(myLocation) < 3) {
                        if (Util.DEBUG) System.out.println("Ignoring bullet tree targets");
                        ignoreBTrees = frame;
                    }
                } else {
                    Map.generateFarTargets(map.rc, myLocation, 1000, 0);
                    if (Util.DEBUG) System.out.println("Using long range target");
                    MapLocation archon = Map.getTarget(3, myLocation);
                    if (archon != null && archon.distanceTo(myLocation) < 20 && frame - stayWithArchon > 50) {
                        nextEnemy = archon;
                    } else {
                        nextEnemy = Map.getTarget(suicidal ? 2 : getTargetType, myLocation);
                    }
                    if (nextEnemy == null && suicidal) {
                        nextEnemy = archon;
                    }
                    if (nextEnemy != null && nextEnemy.distanceTo(myLocation) < 0.6 * RobotType.SOLDIER.sensorRadius) {
                        Radio.deleteEnemyReport(nextEnemy);
                    }
                }
                if (DEBUG && nextEnemy != null) System.out.println("Next enemy at " + nextEnemy);
            }
            if (lastNoBullets && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > 14) && bullets.length > 0) {
                Radio.reportEnemies(new RobotInfo[]{new RobotInfo(2342, rc.getTeam().opponent(), RobotType.SOLDIER, bullets[bullets.length - 1].location, 42, 1, 1)});
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
                    movement.MIN_ENEMY_DIST = MIN_EVASION_DIST - 2;
                    Movement.MIN_FRIENDLY_SOLDIER_DIST = 0f;
                    if (!hasMoved && movement.findPath(evadePos, fireDir)) {
                        myLocation = rc.getLocation();
                    }
                    Movement.MIN_FRIENDLY_SOLDIER_DIST = 4f;
                    movement.MIN_ENEMY_DIST = 0;
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
                    if (dist < minfiredist) {
                        if (DEBUG) System.out.println("Firing late " + nextEnemyInfo.location.distanceTo(myLocation));
                        hasFired = tryFire(nextEnemyInfo, nextEnemy, enemyType, dist, enemyType.bodyRadius);
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

            if (nearbyRobots.length < 5) nearbyRobots = map.sense();
            for (RobotInfo ri : nearbyRobots) {
                if (!ri.team.equals(rc.getTeam()) && (ri.type == RobotType.SOLDIER || ri.type == RobotType.TANK)) {
                    nextEnemySoldier = ri;
                }
            }

            if (!hasFired && nextEnemySoldier != null) {
                if (checkLineOfFire(myLocation, nextEnemySoldier.location, trees, nearbyRobots, rc.getType().bodyRadius)) {
                    if (DEBUG) System.out.println("Firing at soldier ");
                    hasFired = tryFire(nextEnemySoldier, nextEnemySoldier.location, RobotType.SCOUT, nextEnemySoldier.location.distanceTo(myLocation), RobotType.SCOUT.bodyRadius);
                } else {
                    if (Util.DEBUG) System.out.println("No LOS on soldier");
                }
            }
            if (!hasFired && nextEnemyScout != null) {
                if (checkLineOfFire(myLocation, nextEnemyScout, trees, nearbyRobots, rc.getType().bodyRadius)) {
                    if (DEBUG) System.out.println("Firing at scout ");
                    hasFired = tryFire(nextEnemyInfo, nextEnemyScout, RobotType.SCOUT, nextEnemyScout.distanceTo(myLocation), RobotType.SCOUT.bodyRadius);
                } else {
                    if (Util.DEBUG) System.out.println("No LOS on scout");
                }
            }
            lastEnemyInfo = nextEnemyInfo;
            lastNoBullets = bullets.length == 0;

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
            EXCEPTION();
        }
    }

    boolean tryFire(RobotInfo nextEnemyInfo, MapLocation nextEnemy, RobotType enemyType, float dist, float radius) throws GameActionException {
        dist += 2f - rc.getType().bodyRadius * 2;
        if (!Util.fireAllowed) return false;
        if (enemyType == RobotType.ARCHON && money < MIN_ARCHON_BULLETS && nextEnemyInfo.health / nextEnemyInfo.type.maxHealth > 0.2f)
            return false;

        if (nextEnemyInfo != null && lastEnemyInfo != null && enemyType != RobotType.SCOUT) {
            nextEnemy = predict(nextEnemyInfo, lastEnemyInfo, spotterTarget ? 1 : 0);
        }
        MapLocation myLocation = rc.getLocation();

        if (nextEnemy.equals(myLocation)) return false;
        if (!checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, rc.getType().bodyRadius)) return false;
        Direction firedir = myLocation.directionTo(nextEnemy).rotateLeftDegrees((2 * rand() - 1f) * Math.min(3, nearbySoldiers + 2) * 1.6f * enemyType.strideRadius);
        if (Util.DEBUG) {
            System.out.println("Random offset of +- " + (Math.min(3, nearbySoldiers + 2) * 2 * enemyType.strideRadius) + " degrees");
        }
        float maxArc = getMaximumArcOfFire(myLocation, firedir, nearbyRobots, trees);
        if (DEBUG) System.out.println("Maximum arc is " + (int) (maxArc * 180 / 3.1415) + " degrees");
        if (enemyType == RobotType.SCOUT) {
            if (dist > 5f && Util.tooManyTrees && money < 110 && rc.getRoundNum() % 2 == 0) return false;
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
        if ((dist - radius < 1.51 + Math.max(0, money / 50f - 2) + Math.max(0, 4 * nearbyEnemySoldiers - 3) || !Util.tooManyTrees && dist < 8 && enemyType == RobotType.SOLDIER) && (maxArc > PENTAD_ARC_PLUSMINUS || dist < 3) && rc.canFirePentadShot()) {
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
