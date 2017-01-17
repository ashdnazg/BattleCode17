package johnny4_20170116;

import battlecode.common.*;

import static johnny4_20170116.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;
    Movement movement;
    final boolean isRoamer;
    MapLocation lastRandomLocation;
    RobotInfo lastEnemyInfo;

    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.movement = new Movement(rc);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        this.isRoamer = rc.getID() % 2 == 0;
        lastRandomLocation = map.archonPos[(int) (map.archonPos.length * rand())];
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
    BulletInfo bullets[];
    MapLocation nextLumberjack;
    boolean evasionMode = true;
    final float MIN_EVASION_DIST = 5f;

    protected void tick() {
        try {
            preTick();
            int frame = rc.getRoundNum();
            radio.frame = frame;
            MapLocation myLocation = rc.getLocation();
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
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) &&
                        (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius + (enemyType == RobotType.ARCHON ? 10 : 0) > r.location.distanceTo(myLocation) * r.type.strideRadius + (r.type == RobotType.ARCHON ? 10 : 0))) {
                    nextEnemy = r.location;
                    nextEnemyInfo = r;
                    enemyType = r.type;
                }
            }
            if (nextEnemyInfo != null && lastEnemyInfo != null) {
                nextEnemy = predict(nextEnemyInfo, lastEnemyInfo);
            }
            lastEnemyInfo = nextEnemyInfo;

            if (myLocation.distanceTo(stuckLocation) > 7) {
                stuckSince = frame;
                stuckLocation = myLocation;
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
            if (nextEnemy == null) {
                longrange = true;
                if (Util.DEBUG) System.out.println("Using long range target");
                nextEnemy = map.getTarget(0, myLocation);
                if (nextEnemy != null && nextEnemy.distanceTo(myLocation) < 0.6 * RobotType.SOLDIER.sensorRadius) {
                    Radio.deleteEnemyReport(nextEnemy);
                }
                if (frame % 9 == 0) {
                    map.generateFarTargets(myLocation, 1000, 0);
                }

            }
            Movement.init(nearbyRobots, trees, bullets);
            boolean hasMoved = false;
            myLocation = rc.getLocation();
            float dist = 10000f;
            int cnt3 = Clock.getBytecodeNum();
            int cnt4, cnt5, cnt6;
            cnt4 = cnt5 = cnt6 = 0;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);

                boolean hasFired = longrange;
                Direction fireDir = null;
                if (!hasFired && evasionMode) {
                    if (!checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < 5f / enemyType.strideRadius + 2 + 3 * (int) (rc.getTeamBullets() / 150)) {
                        if (Util.DEBUG) System.out.println("No LOS");
                    } else {
                        hasFired = tryFire(nextEnemy, dist, enemyType.bodyRadius);
                        fireDir = myLocation.directionTo(nextEnemy);
                        if (hasFired) {
                            bullets = rc.senseNearbyBullets();
                        }
                    }
                }
                if (rc.getTeamBullets() > 50 && evasionMode) {
                    if (Util.DEBUG) System.out.println("Soldier entering aggression mode");
                    movement.evadeBullets = false;
                    evasionMode = false;
                }
                if (rc.getTeamBullets() < 10 && !evasionMode) {
                    if (Util.DEBUG) System.out.println("Soldier entering evasion mode");
                    evasionMode = true;
                    movement.evadeBullets = true;
                }
                cnt4 = Clock.getBytecodeNum();
                if (evasionMode && !longrange && (dist < MIN_EVASION_DIST)) {
                    if (!hasMoved && movement.findPath(nextEnemy.add(nextEnemy.directionTo(myLocation), MIN_EVASION_DIST + 1), fireDir)) {
                        myLocation = rc.getLocation();
                    }
                    hasMoved = true;
                } else {
                    if (!hasMoved) {
                        cnt5 = Clock.getBytecodeNum();
                        if (movement.findPath(nextEnemy, fireDir)) {
                            myLocation = rc.getLocation();
                        }
                        hasMoved = true;
                        cnt6 = Clock.getBytecodeNum();
                    }
                }

                if (!hasFired) {
                    if (!checkLineOfFire(myLocation, nextEnemyInfo.location, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < 5f / enemyType.strideRadius + 2) {
                        if (Util.DEBUG) System.out.println("No LOS");
                    } else {
                        hasFired = tryFire(nextEnemy, dist, enemyType.bodyRadius);
                        fireDir = myLocation.directionTo(nextEnemy);
                        if (hasFired) {
                            bullets = rc.senseNearbyBullets();
                        }
                        Movement.lastLOS = frame;
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

    boolean tryFire(MapLocation nextEnemy, float dist, float radius) throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        if (nextEnemy.equals(myLocation)) return false;
        float bullets = rc.getTeamBullets();
        if (dist - radius < 1.51 + Math.max(0, bullets / 50f - 2) && rc.canFirePentadShot()) {
            if (Util.DEBUG) System.out.println("Firing pentad");
            rc.firePentadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (dist - radius < 2.11 + Math.max(0, bullets / 50f - 2) && rc.canFireTriadShot()) {
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
