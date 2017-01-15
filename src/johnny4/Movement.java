package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Movement {

    static RobotType robotType;
    static RobotController rc;
    static float strideDistance;
    static int lastInit = -1;
    static RobotInfo[] robots;
    static TreeInfo[] trees;
    static BulletInfo[] bullets;
    static MapLocation myLocation;
    static Direction fireDir;
    static Team myTeam, enemyTeam;
    static Threat[] threats;
    static int threatsLen = 0;
    static MapLocation stuckLocation;
    static int stuckSince;
    static float attemptDist[];
    static float MIN_FRIENDLY_LUMBERJACK_DIST;
    static float MIN_ENEMY_LUMBERJACK_DIST; //overrides enemy dist
    static float MIN_ENEMY_DIST;
    static float MIN_MOVE_TO_FIRE_ANGLE;
    static float GO_STRAIGHT_DISTANCE;
    static boolean evadeBullets = true;

    public Movement(RobotController rc) {
        this.rc = rc;
        robotType = rc.getType();
        strideDistance = robotType.strideRadius;
        myTeam = rc.getTeam();
        enemyTeam = rc.getTeam().opponent();
        stuckLocation = rc.getLocation();
        stuckSince = rc.getRoundNum();
        attemptDist = new float[2];
        attemptDist[0] = strideDistance;
        attemptDist[1] = 0.5f;
        switch (robotType) {
            case SCOUT:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.81f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 3.5f;
                GO_STRAIGHT_DISTANCE = 4;
                attemptDist[1] = 1.1f;
                break;
            case LUMBERJACK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 1;
                break;
            case SOLDIER:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 2;
                break;
            case GARDENER:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 5f;
                GO_STRAIGHT_DISTANCE = 0;
                break;
            case ARCHON:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 0;
                evadeBullets = false;
                break;
            default:
            case TANK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                evadeBullets = false;
        }
        threats = new Threat[100];
        for (int i = 0; i < 100; i++) {
            threats[i] = new Threat();
        }

        MIN_MOVE_TO_FIRE_ANGLE = 90.01f - 180f / 3.14159265358979323f * (float) Math.acos(robotType.bodyRadius / (robotType.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET));
        System.out.println("min angle for " + robotType + " is " + MIN_MOVE_TO_FIRE_ANGLE);
    }

    // Call this every frame before using
    public void init(RobotInfo[] robots, TreeInfo[] trees, BulletInfo[] bullets) {
        lastInit = rc.getRoundNum();
        this.robots = robots != null ? robots : new RobotInfo[0];
        this.trees = trees != null ? trees : new TreeInfo[0];
        this.bullets = bullets != null ? bullets : new BulletInfo[0];
        this.myLocation = rc.getLocation();
        this.threatsLen = 0;
        boolean nothreats = true;
        for (RobotInfo ri : robots) {
            if (MIN_FRIENDLY_LUMBERJACK_DIST > 0.01 && ri.getTeam().equals(myTeam) && (ri.location.distanceTo(myLocation) < MIN_FRIENDLY_LUMBERJACK_DIST + strideDistance)
                    && ri.type == RobotType.LUMBERJACK && (ri.moveCount + ri.attackCount > 0 || ri.health > 0.9f * ri.type.maxHealth)) {
                threats[threatsLen].loc = ri.location;
                threats[threatsLen].radius = MIN_FRIENDLY_LUMBERJACK_DIST;
                threats[threatsLen++].description = "friendly lumberjack";
            } else if (MIN_ENEMY_LUMBERJACK_DIST > 0.01 && ri.getTeam().equals(enemyTeam) && (ri.location.distanceTo(myLocation) < MIN_ENEMY_LUMBERJACK_DIST + strideDistance)
                    && ri.type == RobotType.LUMBERJACK && (ri.moveCount + ri.attackCount > 0 || ri.health > 0.9f * ri.type.maxHealth)) {
                threats[threatsLen].loc = ri.location;
                threats[threatsLen].radius = MIN_ENEMY_LUMBERJACK_DIST;
                threats[threatsLen++].description = "enemy lumberjack";
                nothreats = false;
            } else if (ri.getTeam().equals(enemyTeam) && (ri.location.distanceTo(myLocation)) < MIN_ENEMY_DIST + strideDistance && (ri.moveCount + ri.attackCount > 0 || ri.health > 0.9f * ri.type.maxHealth) &&
                    (ri.type == RobotType.LUMBERJACK || ri.type == RobotType.SOLDIER || ri.type == RobotType.TANK || robotType == RobotType.GARDENER && ri.type == RobotType.SCOUT)) {
                threats[threatsLen].loc = ri.location;
                threats[threatsLen].radius = MIN_ENEMY_DIST;
                threats[threatsLen++].description = "armed enemy";
                nothreats = false;
            }
        }
        for (int i = 0; i < threatsLen; i++) {
            threats[i].x = threats[i].loc.x;
            threats[i].y = threats[i].loc.y;
            threats[i].radiusSquared = threats[i].radius * threats[i].radius;
        }
        if (nothreats) { // only keep distance to lumberjacks in combat
            threatsLen = 0;
        }
        if (myLocation.distanceTo(stuckLocation) > strideDistance * 1.1) {
            stuckLocation = myLocation;
            stuckSince = lastInit;
        }
    }

    static MapLocation oldTarget = null;

    public boolean findPath(MapLocation target, Direction fireDir) throws GameActionException {
        if (target == null) {
            System.out.println("Pathfinding to null, that's easy");
            return false;
        }
        if (oldTarget != null && oldTarget.distanceTo(target) > 2) {
            stuckSince = rc.getRoundNum();
        }
        oldTarget = target;
        if (rc.getRoundNum() != lastInit) {
            new RuntimeException("Movement wasn't initialized since: " + lastInit).printStackTrace();
        }
        System.out.println("Pathfinding to " + target + "(dist: " + target.distanceTo(myLocation) + ", dir: " + myLocation.directionTo(target) + ") avoiding bullet in dir " + fireDir);
        this.fireDir = fireDir;

        Direction moveDir = myLocation.directionTo(target);
        float olddist = myLocation.distanceTo(target);
        if (robotType != RobotType.SCOUT && olddist > 0.9 * strideDistance) {
            float t, bestval = 10000f;
            TreeInfo best = null;
            for (TreeInfo tree : trees) {
                if (myLocation.distanceTo(tree.location) - tree.radius - robotType.bodyRadius < strideDistance) {
                    t = Math.abs(myLocation.directionTo(tree.location).degreesBetween(myLocation.directionTo(target)));
                    if (t < bestval) {
                        bestval = t;
                        best = tree;
                    }
                }
            }
            if (best != null) {
                Direction toTree = myLocation.directionTo(best.location);
                float correctionAngle = (float) (Math.asin((robotType.bodyRadius + best.radius) / myLocation.distanceTo(best.location)));
                if (Math.abs(moveDir.radiansBetween(toTree)) < correctionAngle) {
                    System.out.println("Found blocking tree at angle " + bestval);
                    rc.setIndicatorLine(myLocation, target, 0, 0, 255);
                    rc.setIndicatorLine(myLocation, best.location, 255, 0, 0);
                    //float sqrt = (float) Math.sqrt(myLocation.distanceSquaredTo(best.location) + best.radius * best.radius);
                    moveDir = toTree.rotateLeftRads((bugdir ? 1 : -1) * correctionAngle);

                    rc.setIndicatorLine(myLocation, myLocation.add(moveDir, (float) Math.sqrt(myLocation.distanceSquaredTo(best.location) + (robotType.bodyRadius + best.radius) * (robotType.bodyRadius + best.radius))), 0, 255, 0);
                }
            }
        }
        if (olddist > 2 * strideDistance && lastInit - stuckSince > 4) {
            System.out.println("Switching bugdir because of stuck");
            stuckSince = rc.getRoundNum();
            bugdir = !bugdir;
        }

        boolean hadLos = checkLineOfFire(myLocation, target, trees, robots, robotType.bodyRadius);
        if (olddist < 0.0001f) return false;
        boolean retval = bugMove(moveDir, Math.min(strideDistance, target.distanceTo(myLocation)));
        System.out.println(olddist + " -> " + myLocation.distanceTo(target) + " : " + retval);
        if (retval && olddist < myLocation.distanceTo(target) && (olddist < GO_STRAIGHT_DISTANCE || hadLos && olddist < GO_STRAIGHT_DISTANCE * 2.5)) {
            System.out.println("Switching bugdir because of distance");
            bugdir = !bugdir;
        }
        return retval;

    }


    private float valueMove(Direction dir) {
        return valueMove(dir, strideDistance);
    }

    static MapLocation nloc, nloc2, b1, b2, b3;
    static float br;

    static private float valueMove(Direction dir, float dist) {
        Threat threat;
        if (!rc.canMove(dir, dist) || dist > strideDistance) return 10;
        float max = 0;
        if (fireDir != null && Math.abs(fireDir.degreesBetween(dir)) < MIN_MOVE_TO_FIRE_ANGLE) {
            //System.out.println(dir + " would collide with own bullet");
            max = 0.9f;
        }
        nloc = myLocation.add(dir, rc.getType().strideRadius);
        nloc2 = myLocation.add(dir, rc.getType().strideRadius / 2);
        for (int i = 0; i < threatsLen; i++) {

            threat = threats[i];
            if ((threat.x - nloc.x) * (threat.x - nloc.x) + (threat.y - nloc.y) * (threat.y - nloc.y) < threats[i].radiusSquared) {
                //System.out.println(nloc + " would be too close to " + threat.description + " at " + threat.loc);
                max = Math.max(max, threat.radius - nloc.distanceTo(threat.loc) + 1);
            }
        }
        if (max > 0.0001) return max;
        br = robotType.bodyRadius * robotType.bodyRadius;

        if (evadeBullets) {
            for (int i = 0; i < bullets.length; i++) {
                b1 = bullets[i].location;
                b2 = bullets[i].location.add(bullets[i].dir, bullets[i].speed / 2);
                b3 = bullets[i].location.add(bullets[i].dir, bullets[i].speed);
                if ((b1.x - nloc.x) * (b1.x - nloc.x) + (b1.y - nloc.y) * (b1.y - nloc.y) < br ||
                        (b2.x - nloc.x) * (b2.x - nloc.x) + (b2.y - nloc.y) * (b2.y - nloc.y) < br ||
                        (b3.x - nloc.x) * (b3.x - nloc.x) + (b3.y - nloc.y) * (b3.y - nloc.y) < br) {
                    return 1;
                }
                if ((b1.x - nloc2.x) * (b1.x - nloc2.x) + (b1.y - nloc2.y) * (b1.y - nloc2.y) < br ||
                        (b2.x - nloc2.x) * (b2.x - nloc2.x) + (b2.y - nloc2.y) * (b2.y - nloc2.y) < br ||
                        (b3.x - nloc2.x) * (b3.x - nloc2.x) + (b3.y - nloc2.y) * (b3.y - nloc2.y) < br) {
                    return 1;
                }
            }
        }

        return 0;
    }


    private boolean bugMove(Direction dir) {
        return bugMove(dir, strideDistance);
    }

    private boolean bugdir = rand() > 0.5f;

    private boolean bugMove(Direction dir, float dist) {
        float ret = LJ_tryMove(dir, 52, 3, bugdir, dist);
        if (ret >= 180) {
            bugdir = !bugdir;
            System.out.println("Switching bugdir to evade enemy");
        }
        if (rand() < 0.02f || ret < 0 && rand() < 0.2f) {
            bugdir = !bugdir;
            System.out.println("Switching bugdir");
        }
        return ret > -0.01f;
    }

    private boolean LJ_tryMove(Direction dir) {
        return LJ_tryMove(dir, 17 + rand() * 17, 6, true, strideDistance) > -0.001f || LJ_tryMove(dir, 17 + rand() * 17, 6, false, strideDistance) > -0.001f;

    }

    private static float LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide, boolean left, float dist) {

        try {
            Direction bestDir = null;
            float bestDist = 0;
            float bestVal = 10000;
            float t = 0;
            float bestDeg = 0;
            float tOff = degreeOffset;
            int tCheck = checksPerSide;
            for (int lob = 0; lob < 2; lob++) {
                int attempt = 0;
                int maxAttempt = 1;
                if (dist >= 0.8 * strideDistance) {
                    attempt = 1;
                    maxAttempt = 2;
                }

                do {
                    boolean moved = false;
                    int currentCheck = lob;
                    checksPerSide = tCheck / ((dist < strideDistance / 2f) ? 3 : 1);
                    degreeOffset = tOff * ((dist < strideDistance / 2f) ? 3 : 1);
                    Direction d;

                    while (currentCheck <= checksPerSide) {
                        System.out.println(Clock.getBytecodeNum() + ": Checking angle" + degreeOffset * currentCheck + " in left dir " + left);
                        if (left) {
                            d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                        } else {
                            d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                        }
                        int clock2 = Clock.getBytecodeNum();
                        t = valueMove(d, dist);
                        int clock3 = Clock.getBytecodeNum();
                        rc.setIndicatorDot(myLocation.add(d, dist), 255 - (int) (t * 25), 255 - (int) (t * 25), 255 - (int) (t * 25));
                        if (t < 0.01) {
                            rc.move(d, dist);
                            myLocation = rc.getLocation();
                            return degreeOffset * currentCheck - 2 * lob * degreeOffset * currentCheck + lob * 360;
                        } else if (t < bestVal) {
                            bestDist = dist;
                            bestDir = d;
                            bestVal = t;
                            bestDeg = degreeOffset * currentCheck - 2 * lob * degreeOffset * currentCheck + lob * 360;
                        }
                        currentCheck++;
                        if (Clock.getBytecodesLeft() < 500) break; //emergency brake
                    }
                    if (Clock.getBytecodesLeft() < 500) break;
                    if (attempt >= maxAttempt) break;
                    dist = attemptDist[attempt++];
                } while (true);
                left = !left;
                dist = strideDistance;
                if (Clock.getBytecodesLeft() < 500) break;
            }
            if (bestVal > 9.9) {
                return -1f;
            } else {
                rc.move(bestDir, bestDist);
                return bestDeg;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return -1f;
        }
    }

    public class Threat {
        float x, y;
        float radiusSquared;
        MapLocation loc;
        float radius;
        String description;
    }

}
