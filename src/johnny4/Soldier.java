package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Soldier {

    RobotController rc;
    Map map;
    Radio radio;
    final boolean isRoamer;

    public Soldier(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        this.isRoamer = rc.getID() % 2 == 0;
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                System.out.println("BYTECODE OVERFLOW");
            }
            Clock.yield();
        }
    }

    private boolean canMove(Direction dir) {
        if (Clock.getBytecodesLeft() < 1500) {
            return rc.canMove(dir);
        }
        MapLocation nloc = rc.getLocation().add(dir, rc.getType().strideRadius);
        MapLocation nloc2 = rc.getLocation().add(dir, rc.getType().strideRadius / 2);
        if (nextLumberjack != null && nloc.distanceTo(nextLumberjack) < MIN_LUMBERJACK_DIST) return false;
        float br = rc.getType().bodyRadius;

        MapLocation b1, b2, b3;
        for (BulletInfo bi : bullets) {
            b1 = bi.location;
            b2 = bi.location.add(bi.dir, bi.speed / 2);
            b3 = bi.location.add(bi.dir, bi.speed);
            if (b1.distanceTo(nloc) < br || b2.distanceTo(nloc) < br || b3.distanceTo(nloc) < br) {
                return false;
            }
            if (b1.distanceTo(nloc2) < br || b2.distanceTo(nloc2) < br || b3.distanceTo(nloc2) < br) {
                return false;
            }
        }
        return rc.canMove(dir);
    }

    private boolean canMove(Direction dir, float dist) {
        try {
            if (Clock.getBytecodesLeft() < 1500) {
                return rc.canMove(dir, dist);
            }
            MapLocation nloc = rc.getLocation().add(dir, dist);
            MapLocation nloc2 = rc.getLocation().add(dir, dist / 2);
            MapLocation b1, b2, b3;
            if (nextLumberjack != null && nloc.distanceTo(nextLumberjack) < MIN_LUMBERJACK_DIST) return false;
            float br = rc.getType().bodyRadius;
            for (BulletInfo bi : bullets) {
                b1 = bi.location;
                b2 = bi.location.add(bi.dir, bi.speed / 2);
                b3 = bi.location.add(bi.dir, bi.speed);
                if (b1.distanceTo(nloc) < br || b2.distanceTo(nloc) < br || b3.distanceTo(nloc) < br) {
                    return false;
                }
                if (b1.distanceTo(nloc2) < br || b2.distanceTo(nloc2) < br || b3.distanceTo(nloc2) < br) {
                    return false;
                }
            }
            return rc.canMove(dir, dist);
        } catch (Exception ex) {

            System.out.println("canMove exception with args " + dir + ": " + dist);
            ex.printStackTrace();
            return false;
        }
    }

    float circleDir = 0f;
    MapLocation stuckLocation;
    int stuckSince;
    BulletInfo bullets[];
    MapLocation nextLumberjack;
    boolean evasionMode = true;
    final float MIN_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
    final float MIN_HOSTILE_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
    final float MIN_EVASION_DIST = 6f;

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
                nearbyRobots = rc.senseNearbyRobots();
            }

            MapLocation nextEnemy = null;
            nextLumberjack = null;
            TreeInfo trees[] = rc.senseNearbyTrees();
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (r.type != RobotType.SCOUT || rc.getID() % 7 == 0) &&
                        (nextEnemy == null || nextEnemy.distanceTo(myLocation) * enemyType.strideRadius + (enemyType == RobotType.ARCHON ? 10 : 0) > r.location.distanceTo(myLocation) * r.type.strideRadius + (r.type == RobotType.ARCHON ? 10 : 0))) {
                    nextEnemy = r.location;
                    enemyType = r.type;
                }
                if (r.getTeam().equals(rc.getTeam()) && r.type == RobotType.LUMBERJACK && (nextLumberjack == null || nextLumberjack.distanceTo(myLocation) > r.location.distanceTo(myLocation))) {
                    nextLumberjack = r.location;
                }
            }

            if (myLocation.distanceTo(stuckLocation) > 5) {
                stuckSince = frame;
                stuckLocation = myLocation;
            }
            if (rc.getRoundNum() - stuckSince > 69) {
                System.out.println("Stuck soldier reporting trees");
                stuckSince = 100000;
                for (TreeInfo t : trees) {
                    if (t.getTeam().equals(rc.getTeam())) continue;
                    if (t.location.distanceTo(myLocation) < 5) {
                        System.out.println("Reported tree at " + t.location);
                        radio.requestTreeCut(t);
                    }
                }
            }
            int cnt2 = Clock.getBytecodeNum();

            boolean longrange = false;
            if (nextEnemy == null) {
                longrange = true;
                nextEnemy = map.getTarget(myLocation, 4, 10);
                if (nextEnemy == null) {


                    nextEnemy = map.getTarget(myLocation, 4, 30 + rc.getID() % 60, RobotType.SOLDIER.sensorRadius * 4);
                    if (!isRoamer && nextEnemy == null) {
                        nextEnemy = map.getTarget(myLocation, 3, 300);
                    }
                    //System.out.println("Soldier at " + myLocation + " attacking " + nextEnemy);
                }
            }
            boolean hasMoved = false;
            myLocation = rc.getLocation();
            float dist = 10000f;
            int cnt3 = Clock.getBytecodeNum();
            int cnt4, cnt5, cnt6;
            cnt4 = cnt5 = cnt6 = 0;
            if (nextEnemy != null) {
                dist = myLocation.distanceTo(nextEnemy);

                boolean hasFired = longrange;
                if (!hasFired && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < 6f / enemyType.strideRadius + rc.getTeamBullets() / 100) {
                    hasFired = tryFire(nextEnemy, dist, enemyType.bodyRadius);
                    if (hasFired) {
                        bullets = rc.senseNearbyBullets();
                    }
                }
                if (dist < 5 && nextLumberjack != null && nextLumberjack.distanceTo(myLocation) < MIN_LUMBERJACK_DIST) {
                    if (LJ_tryMove(nextLumberjack.directionTo(myLocation))) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                }
                if (rc.getTeamBullets() > 50) evasionMode = false;
                if (rc.getTeamBullets() < 10) evasionMode = true;
                cnt4 = Clock.getBytecodeNum();
                if (evasionMode && (dist < MIN_EVASION_DIST || enemyType == RobotType.LUMBERJACK && dist < MIN_HOSTILE_LUMBERJACK_DIST)) {
                    if (!hasMoved && LJ_tryMove(nextEnemy.directionTo(myLocation))) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                } else {
                    if (!hasMoved) {
                        if ((longrange || dist > 6f / enemyType.strideRadius) && !evasionMode) {
                            cnt5 = Clock.getBytecodeNum();
                            if (LJ_tryMove(myLocation.directionTo(nextEnemy), 42, 2)) {
                                hasMoved = true;
                                myLocation = rc.getLocation();
                                rc.setIndicatorDot(myLocation, 1, 0, 0);
                            }
                            cnt6 = Clock.getBytecodeNum();
                        } else {
                            //cnt6 = Clock.getBytecodeNum();
                            Direction dir;
                            int tries = 0;
                            while (!hasMoved && tries++ < 8) {
                                if (circleDir > 0.5) {
                                    dir = myLocation.directionTo(nextEnemy).rotateRightDegrees(9 * tries + 30);
                                } else {
                                    dir = myLocation.directionTo(nextEnemy).rotateLeftDegrees(9 * tries + 30);
                                }
                                if (!hasMoved && canMove(dir, 2f)) {
                                    rc.move(dir, 2f);
                                    hasMoved = true;
                                    myLocation = rc.getLocation();
                                    rc.setIndicatorDot(myLocation, 0, 1, 0);
                                } else {
                                    circleDir = rand();
                                }
                            }
                        }

                    }
                }

                if (!hasFired && checkLineOfFire(myLocation, nextEnemy, trees, nearbyRobots, RobotType.SOLDIER.bodyRadius) && dist < 6f / enemyType.strideRadius + rc.getTeamBullets() / 100) {
                    hasFired = tryFire(nextEnemy, dist, enemyType.bodyRadius);
                }
            } else if (!hasMoved) {
                LJ_tryMove(randomDirection());
                myLocation = rc.getLocation();
            }

            int cnt7 = Clock.getBytecodeNum();
            if (rc.getRoundNum() - frame > 0) {
                System.out.println("Soldier took " + (rc.getRoundNum() - frame) + " frames at " + frame + " using longrange " + longrange);
                System.out.println("Timings " + cnt1 + " " + cnt2 + " " + cnt3 + " " + cnt4 + " " + cnt5 + " " + cnt6 + " " + cnt7);
            }


        } catch (Exception e) {
            System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }

    boolean tryFire(MapLocation nextEnemy, float dist, float radius) throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        float bullets = rc.getTeamBullets();
        if (dist - radius < 1.51 +  bullets / 100f && rc.canFirePentadShot()) {
            System.out.println("Firing pentad");
            rc.firePentadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (dist - radius < 2.11 +  bullets / 100f && rc.canFireTriadShot()) {
            System.out.println("Firing triad");
            rc.fireTriadShot(myLocation.directionTo(nextEnemy));
            return true;
        } else if (rc.canFireSingleShot()) {
            rc.fireSingleShot(myLocation.directionTo(nextEnemy));
            return true;
        }
        return false;
    }

    boolean LJ_tryMove(Direction dir) {
        try {
            return LJ_tryMove(dir, 32, 3);
        } catch (Exception ex) {
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        if (canMove(dir)) {
            rc.move(dir);
            return true;
        }
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            try {
                Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
                d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            currentCheck++;
        }
        return false;
    }
}
