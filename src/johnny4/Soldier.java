package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    final boolean isRoamer;
    MapLocation lastRandomLocation;
    RobotInfo lastEnemyInfo;
    RobotInfo guardener = null;
    int guardenerID = -1;
    int lastContactWithGuardener = -1000;
    final static float MIN_GUARDENER_DIST = RobotType.SOLDIER.sensorRadius + 10;
    final static float MIN_SCOUT_SHOOT_RANGE = 6.5f;
    final static float MIN_ARCHON_BULLETS = 80f;


    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        this.isRoamer = rc.getID() % 2 == 0;

        boolean hasGuardener = false;
        for (RobotInfo ri : rc.senseNearbyRobots()) {
            if (ri.getTeam().equals(rc.getTeam())) {
                if (ri.type == RobotType.GARDENER) {
                    guardener = ri;
                    guardenerID = ri.ID;
                    lastContactWithGuardener = rc.getRoundNum();
                }
                if (ri.type == RobotType.SOLDIER) {
                    hasGuardener = true;
                }
            }
        }



        float dist = 1e10f;
        float curDist;
        for (MapLocation archonPos: map.enemyArchonPos) {
            curDist = archonPos.distanceTo(stuckLocation);
            if (curDist < dist) {
                lastRandomLocation = archonPos;
                dist = curDist;
            }
        }


        if (hasGuardener || dist < 45f) {
            guardener = null;
            guardenerID = -1;
        }
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
    MapLocation enemyStuckLocation = new MapLocation(0,0);
    int enemyStuckSince;
    BulletInfo bullets[];
    MapLocation nextLumberjack;
    boolean evasionMode = true;
    final float MIN_EVASION_DIST = 8f;

    float money;

    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
            money = rc.getTeamBullets();
            bullets = rc.senseNearbyBullets();
            RobotInfo nearbyRobots[] = null;
            RobotType enemyType = RobotType.SOLDIER;
            int cnt1 = Clock.getBytecodeNum();
            if (frame % 8 == 0) {
                nearbyRobots = map.sense();
            }
            if (nearbyRobots == null) {
                nearbyRobots = map.sense();//rc.senseNearbyRobots();
            }

            MapLocation nextEnemy = null;
            RobotInfo nextEnemyInfo = null;
            TreeInfo trees[] = senseClosestTrees();
            boolean hasGuardener = false;
            TreeInfo hideTree = null;
            for (RobotInfo ri : nearbyRobots) {
                if (ri.getTeam().equals(rc.getTeam())) {
                    if (guardenerID == ri.ID) {
                        guardener = ri;
                        lastContactWithGuardener = rc.getRoundNum();
                    } else if (ri.type == RobotType.GARDENER) {
                        guardener = ri;
                        guardenerID = ri.ID;
                        lastContactWithGuardener = rc.getRoundNum();
                    }
                    if (ri.type == RobotType.SOLDIER) {
                        hasGuardener = true;
                    }
                }
                if (!ri.getTeam().equals(rc.getTeam()) && (ri.type != RobotType.ARCHON || money > MIN_ARCHON_BULLETS) &&
                        (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius + (enemyType == RobotType.ARCHON ? 10 : 0) > ri.location.distanceTo(myLocation) * ri.type.strideRadius + (ri.type == RobotType.ARCHON ? 10 : 0))) {
                    nextEnemy = ri.location;
                    nextEnemyInfo = ri;
                    enemyType = ri.type;
                    if (enemyType == RobotType.SCOUT){
                        hideTree = rc.canSenseLocation(ri.location) ? rc.senseTreeAtLocation(ri.location) : null;
                    }
                }
            }
            if ((frame - lastContactWithGuardener) > 40 || (frame == lastContactWithGuardener && hasGuardener)) {
                guardenerID = -1;
                guardener = null;
            }
            if (nextEnemyInfo != null && lastEnemyInfo != null && enemyType != RobotType.SCOUT) {
                nextEnemy = predict(nextEnemyInfo, lastEnemyInfo);
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
            if (nextEnemy == null) {
                longrange = true;
                if (Util.DEBUG) System.out.println("Using long range target");
                nextEnemy = map.getTarget(1, guardener == null ? myLocation : guardener.location);
                if (nextEnemy == null){
                    if (Util.DEBUG) System.out.println("Using long range target fallback");
                    nextEnemy = map.getTarget(0, guardener == null ? myLocation : guardener.location);
                }
                if (nextEnemy != null && nextEnemy.distanceTo(myLocation) < 0.6 * RobotType.SOLDIER.sensorRadius) {
                    Radio.deleteEnemyReport(nextEnemy);
                }
                if (frame % 9 == 0) {
                    map.generateFarTargets(guardener == null ? myLocation : guardener.location, 1000, 0);
                }
            }else{

                if (Util.DEBUG) System.out.println("Attacking local " + nextEnemyInfo.type + " at " + nextEnemy);
            }
            Movement.init(nearbyRobots, trees, bullets);
            boolean hasMoved = false;
            myLocation = rc.getLocation();
            float dist = 10000f;
            int cnt3 = Clock.getBytecodeNum();
            int cnt4, cnt5, cnt6;
            cnt4 = cnt5 = cnt6 = 0;
            if (guardener != null) {
                if (myLocation.distanceTo(guardener.location) > MIN_GUARDENER_DIST) {
                    movement.findPath(guardener.location, null); //protect your padawan
                    hasMoved = true;
                    if (Util.DEBUG) System.out.println("Returning to guardener");
                }
                if (nextEnemy != null && nextEnemy.distanceTo(guardener.location) > MIN_GUARDENER_DIST && (longrange ? nextEnemy.distanceTo(myLocation) : nextEnemyInfo.location.distanceTo(myLocation)) > MIN_SCOUT_SHOOT_RANGE) {
                    nextEnemy = null;
                }
            }
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);

                boolean hasFired = longrange;
                Direction fireDir = null;
                float minfiredist = 100f / enemyType.strideRadius + 2 + 3 * (int) (money / 150);
                if (!hasFired && evasionMode) {
                    if (checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < minfiredist) {
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
                if (enemyType != RobotType.SOLDIER || (money > 50 && evasionMode)) {
                    if (Util.DEBUG) System.out.println("Soldier entering aggression mode");
                    movement.evadeBullets = false;
                    evasionMode = false;
                }
                if (enemyType != RobotType.SCOUT && money < 10 && !evasionMode) {
                    if (Util.DEBUG) System.out.println("Soldier entering evasion mode");
                    evasionMode = true;
                    movement.evadeBullets = true;
                }
                cnt4 = Clock.getBytecodeNum();
                if (evasionMode && !longrange && (dist < MIN_EVASION_DIST)) {
                    if (!hasMoved && movement.findPath(nextEnemy.add(nextEnemy.directionTo(guardener == null ? myLocation : guardener.location), MIN_EVASION_DIST + 1), fireDir)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                } else {
                    if (!hasMoved) {
                        cnt5 = Clock.getBytecodeNum();
                        MapLocation gotopos = longrange ? nextEnemy : nextEnemyInfo.location.add(nextEnemyInfo.location.directionTo(myLocation), nextEnemyInfo.getRadius() + 1.001f);
                        if (hideTree != null && nextEnemyInfo != null && frame - enemyStuckSince > 7 && hideTree.location.distanceTo(myLocation) - hideTree.radius < nextEnemyInfo.location.distanceTo(myLocation) - nextEnemyInfo.type.bodyRadius){
                            gotopos = hideTree.location.add(hideTree.location.directionTo(nextEnemyInfo.location), hideTree.radius + 1.001f);
                        }
                        if (movement.findPath(gotopos, fireDir)) {
                            myLocation = rc.getLocation();
                        }
                        hasMoved = true;
                        cnt6 = Clock.getBytecodeNum();
                    }
                }

                if (!hasFired) {
                    if (checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < minfiredist) {
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
                while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !movement.findPath(lastRandomLocation, null)) {
                    lastRandomLocation = myLocation.add(randomDirection(), 20);
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
        MapLocation myLocation = rc.getLocation();
        if (nextEnemy.equals(myLocation)) return false;
        if (enemyType == RobotType.SCOUT) {
            if (money < 110 && dist > 5.0f) {
                return false;
            } else {
                if (rc.canFirePentadShot()) {
                    rc.firePentadShot(myLocation.directionTo(nextEnemy));
                    return true;
                }
                if (rc.canFireTriadShot() && dist < 4.0f) {
                    rc.fireTriadShot(myLocation.directionTo(nextEnemy));
                    return true;
                }
                if (rc.canFireSingleShot() && dist < 3.0f) {
                    rc.fireSingleShot(myLocation.directionTo(nextEnemy));
                    return true;
                }
            }
            return false;
        }
        Radio.reportContact();
        if (enemyType == RobotType.ARCHON && money < MIN_ARCHON_BULLETS) {
            return false;
        }
        if (dist - radius < 1.51 + Math.max(0, money / 50f - 2) && rc.canFirePentadShot()) {
            if (Util.DEBUG) System.out.println("Firing pentad");
            rc.firePentadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (dist - radius < 2.41 + Math.max(0, money / 50f - 2) && rc.canFireTriadShot()) {
            if (Util.DEBUG) System.out.println("Firing triad");
            rc.fireTriadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (rc.canFireSingleShot()) {
            if (Util.DEBUG) System.out.println("Firing single bullet");
            rc.fireSingleShot(myLocation.directionTo(nextEnemy));
            return true;
        }
        return false;
    }

}
