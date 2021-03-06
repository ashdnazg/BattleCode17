package johnny4_seeding;

import battlecode.common.*;
import johnny4_seeding.*;
import johnny4_seeding.Util;

import static johnny4_seeding.Util.*;

public class Movement {

    static RobotType robotType;
    static RobotController rc;
    static float strideDistance;
    static int lastInit = -1;
    static RobotInfo[] robots;
    static TreeInfo[] trees;
    static BulletInfo[] bullets = new BulletInfo[5];
    static int bulletLen = 0;
    static MapLocation myLocation;
    static Direction fireDir;
    static Team myTeam, enemyTeam;
    static Threat[] threats = new Threat[100];
    static int threatsLen = 0;
    static MapLocation stuckLocation;
    static int stuckSince;
    static float attemptDist[];
    static float MIN_FRIENDLY_LUMBERJACK_DIST;
    static float MIN_ENEMY_LUMBERJACK_DIST; //overrides enemy dist
    static float MIN_ENEMY_SCOUT_DIST;//overrides enemy dist
    static float MIN_ENEMY_DIST;
    static float MIN_MOVE_TO_FIRE_ANGLE;
    static float GO_STRAIGHT_DISTANCE;
    static float MIN_FRIENDLY_GARDENER_DIST;
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
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 3.5f;
                MIN_ENEMY_SCOUT_DIST = 3.5f;
                GO_STRAIGHT_DISTANCE = 4;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                attemptDist[1] = 1.1f;
                break;
            case LUMBERJACK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                MIN_ENEMY_SCOUT_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 100;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                break;
            case SOLDIER:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                MIN_ENEMY_SCOUT_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 4;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                break;
            case GARDENER:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 5f;
                MIN_ENEMY_SCOUT_DIST = 5f;
                GO_STRAIGHT_DISTANCE = 0;
                MIN_FRIENDLY_GARDENER_DIST = 4;
                break;
            case ARCHON:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 0;
                MIN_ENEMY_SCOUT_DIST = 0f;
                evadeBullets = false;
                break;
            default:
            case TANK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                MIN_ENEMY_SCOUT_DIST = 0f;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                evadeBullets = false;
        }
        MIN_MOVE_TO_FIRE_ANGLE = 90.01f - 180f / 3.14159265358979323f * (float) Math.acos(robotType.bodyRadius / (robotType.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET));
        if (johnny4_seeding.Util.DEBUG) System.out.println("min angle for " + robotType + " is " + MIN_MOVE_TO_FIRE_ANGLE);
    }

    // Call this every frame before using
    public static void init(RobotInfo[] robots_, TreeInfo[] trees_, BulletInfo[] bullets_) {
        if (johnny4_seeding.Util.DEBUG) System.out.println("Starting init " + Clock.getBytecodeNum());
        lastInit = rc.getRoundNum();
        robots = robots_ != null ? robots_ : new RobotInfo[0];
        trees = trees_ != null ? trees_ : new TreeInfo[0];
        myLocation = rc.getLocation();
        threatsLen = bulletLen = 0;
        boolean noEnemies = true;
        float dist;
        boolean active, friendly, lj;
        float maxDist = Math.max(MIN_FRIENDLY_LUMBERJACK_DIST, MIN_ENEMY_LUMBERJACK_DIST);
        maxDist = Math.max(maxDist, MIN_ENEMY_DIST);
        Threat currentThreat = threats[threatsLen];
        if (currentThreat == null) {
            threats[threatsLen] = new Threat();
            currentThreat = threats[threatsLen];
        }
        for (int i = 0; i < bullets_.length; i++) {
            if (bulletLen >= bullets.length || bullets_[i].location.distanceTo(myLocation) > strideDistance + bullets_[i].speed + robotType.bodyRadius)
                break;
            bullets[bulletLen++] = bullets_[i];
        }

        for (RobotInfo ri : robots) {
            friendly = ri.team.equals(myTeam);
            if (!friendly) noEnemies = false;
            if ((ri.moveCount + ri.attackCount == 0) && (ri.health < 0.9f * ri.type.maxHealth)) {
                continue;
            }

            if (friendly) {
                if (MIN_FRIENDLY_LUMBERJACK_DIST > 0.01 && ri.type == RobotType.LUMBERJACK) {
                    dist = ri.location.distanceTo(myLocation) - strideDistance;
                    if (dist < MIN_FRIENDLY_LUMBERJACK_DIST) {
                        currentThreat.loc = ri.location;
                        currentThreat.x = ri.location.x;
                        currentThreat.y = ri.location.y;
                        currentThreat.radius = MIN_FRIENDLY_LUMBERJACK_DIST;
                        currentThreat.radiusSquared = MIN_FRIENDLY_LUMBERJACK_DIST * MIN_FRIENDLY_LUMBERJACK_DIST;
                        //currentThreat.description = "friendly lumberjack";
                        currentThreat.severity = 0.5f;
                        currentThreat = threats[++threatsLen];
                        if (currentThreat == null) {
                            threats[threatsLen] = new Threat();
                            currentThreat = threats[threatsLen];
                        }
                    }
                }
                if (MIN_FRIENDLY_GARDENER_DIST > 0.01 && ri.type == RobotType.GARDENER) {
                    dist = ri.location.distanceTo(myLocation) - strideDistance;
                    if (dist < MIN_FRIENDLY_GARDENER_DIST) {
                        currentThreat.loc = ri.location;
                        currentThreat.x = ri.location.x;
                        currentThreat.y = ri.location.y;
                        currentThreat.radius = MIN_FRIENDLY_GARDENER_DIST;
                        currentThreat.radiusSquared = MIN_FRIENDLY_GARDENER_DIST * MIN_FRIENDLY_GARDENER_DIST;
                        //currentThreat.description = "friendly lumberjack";
                        currentThreat.severity = 0.9f;
                        currentThreat = threats[++threatsLen];
                        if (currentThreat == null) {
                            threats[threatsLen] = new Threat();
                            currentThreat = threats[threatsLen];
                        }
                    }
                }
                continue;
            }


            if (MIN_ENEMY_LUMBERJACK_DIST > 0.01 && ri.type == RobotType.LUMBERJACK) {
                dist = ri.location.distanceTo(myLocation) - strideDistance;
                if (dist < MIN_ENEMY_LUMBERJACK_DIST) {
                    currentThreat.loc = ri.location;
                    currentThreat.x = ri.location.x;
                    currentThreat.y = ri.location.y;
                    currentThreat.radius = MIN_ENEMY_LUMBERJACK_DIST;
                    currentThreat.radiusSquared = MIN_ENEMY_LUMBERJACK_DIST * MIN_ENEMY_LUMBERJACK_DIST;
                    currentThreat.severity = 2;
                    //currentThreat.description = "enemy lumberjack";
                    currentThreat = threats[++threatsLen];
                    if (currentThreat == null) {
                        threats[threatsLen] = new Threat();
                        currentThreat = threats[threatsLen];
                    }
                    continue;
                }
            }
            if (MIN_ENEMY_SCOUT_DIST > 0.01 && ri.type == RobotType.SCOUT) {
                dist = ri.location.distanceTo(myLocation) - strideDistance;
                if (dist < MIN_ENEMY_SCOUT_DIST) {
                    currentThreat.loc = ri.location;
                    currentThreat.x = ri.location.x;
                    currentThreat.y = ri.location.y;
                    currentThreat.radius = MIN_ENEMY_SCOUT_DIST;
                    currentThreat.radiusSquared = MIN_ENEMY_SCOUT_DIST * MIN_ENEMY_SCOUT_DIST;
                    currentThreat.severity = 0.8f;
                    //currentThreat.description = "enemy lumberjack";
                    currentThreat = threats[++threatsLen];
                    if (currentThreat == null) {
                        threats[threatsLen] = new Threat();
                        currentThreat = threats[threatsLen];
                    }
                    continue;
                }
            }

            if (MIN_ENEMY_DIST > 0.01 && (ri.type == RobotType.LUMBERJACK || ri.type == RobotType.SOLDIER || ri.type == RobotType.TANK)) {
                dist = ri.location.distanceTo(myLocation) - strideDistance;
                if (dist < MIN_ENEMY_DIST) {
                    currentThreat.loc = ri.location;
                    currentThreat.x = ri.location.x;
                    currentThreat.y = ri.location.y;
                    currentThreat.radius = MIN_ENEMY_DIST;
                    currentThreat.radiusSquared = MIN_ENEMY_DIST * MIN_ENEMY_DIST;
                    currentThreat.severity = 1f;
                    //currentThreat.description = "armed enemy";
                    currentThreat = threats[++threatsLen];
                    if (currentThreat == null) {
                        threats[threatsLen] = new Threat();
                        currentThreat = threats[threatsLen];
                    }
                }
            }
        }
        if (noEnemies && robotType != RobotType.GARDENER) { // only keep distance to lumberjacks in combat
            threatsLen = 0;
            if (johnny4_seeding.Util.DEBUG) System.out.println("No threats, hugging friendly lumberjacks");
        }
        if (myLocation.distanceTo(stuckLocation) > strideDistance * 1.1) {
            stuckLocation = myLocation;
            stuckSince = lastInit;
        }
        if (johnny4_seeding.Util.DEBUG) System.out.println("ending init " + Clock.getBytecodeNum());
    }

    static MapLocation oldTarget = null;
    static int lastLOS = 0;

    public boolean findPath(MapLocation target, Direction fireDir) throws GameActionException {
        if (johnny4_seeding.Util.DEBUG) System.out.println("Starting findPath " + Clock.getBytecodeNum());
        if (target == null) {
            if (johnny4_seeding.Util.DEBUG) System.out.println("Pathfinding to null, that's easy");
            return false;
        }
        if (oldTarget != null && oldTarget.distanceTo(target) > 2) {
            stuckSince = rc.getRoundNum();
        }
        oldTarget = target;
        if (rc.getRoundNum() != lastInit) {
            new RuntimeException("Movement wasn't initialized since: " + lastInit).printStackTrace();
        }
        if (DEBUG) {
            if (johnny4_seeding.Util.DEBUG)
                System.out.println("Pathfinding to " + target + "(dist: " + target.distanceTo(myLocation) + ", dir: " + myLocation.directionTo(target) + ") avoiding bullet in dir " + fireDir);
        }
        this.fireDir = fireDir;

        if (johnny4_seeding.Util.DEBUG) System.out.println("Somewhere in findPath " + Clock.getBytecodeNum());

        boolean gonnaBeHit = false;
        Direction bulletDir = randomDirection();
        if (evadeBullets) {
            for (int i = 0; i < bulletLen; i++) {
                if (willCollideWithMe(myLocation, bullets[i])) {
                    gonnaBeHit = true;
                    bulletDir = bullets[i].dir;
                    break;
                }
            }
        }
        float olddist = myLocation.distanceTo(target);
        if (olddist < 0.0001f) {
            if (valueMove(Direction.getNorth(), 0) < 0.0001 && !gonnaBeHit) {
                if (johnny4_seeding.Util.DEBUG) System.out.println("I'm already at target");
                return false;
            } else {
                if (johnny4_seeding.Util.DEBUG) System.out.println("I'm already at target but it sucks being around here");
                target = target.add(bulletDir.rotateLeftDegrees(90 * (bugdir ? 1.001f : -1.001f)), strideDistance);
            }
        }
        Direction moveDir = myLocation.directionTo(target);
        if (fireDir != null && evadeBullets && Math.abs(moveDir.degreesBetween(fireDir)) < MIN_MOVE_TO_FIRE_ANGLE) {
            moveDir = fireDir.rotateLeftDegrees((bugdir ? 1.001f : -1.001f) * MIN_MOVE_TO_FIRE_ANGLE);

        }
        if (johnny4_seeding.Util.DEBUG) rc.setIndicatorLine(myLocation, target, 0, 0, 255);
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
                    if (DEBUG) {
                        if (johnny4_seeding.Util.DEBUG) System.out.println("Found blocking tree at angle " + bestval);
                    }
                    if (johnny4_seeding.Util.DEBUG) rc.setIndicatorLine(myLocation, best.location, 255, 0, 0);
                    //float sqrt = (float) Math.sqrt(myLocation.distanceSquaredTo(best.location) + best.radius * best.radius);
                    moveDir = toTree.rotateLeftRads((bugdir ? 1 : -1) * correctionAngle);

                    if (johnny4_seeding.Util.DEBUG)
                        rc.setIndicatorLine(myLocation, myLocation.add(moveDir, (float) Math.sqrt(myLocation.distanceSquaredTo(best.location) + (robotType.bodyRadius + best.radius) * (robotType.bodyRadius + best.radius))), 0, 255, 0);
                }
            }
        }

        if (johnny4_seeding.Util.DEBUG) System.out.println("Somewhere else in findPath " + Clock.getBytecodeNum());
        if (olddist > 2 * strideDistance && lastInit - stuckSince > 4) {
            if (DEBUG) {
                if (johnny4_seeding.Util.DEBUG) System.out.println("Switching bugdir because of stuck");
            }
            stuckSince = rc.getRoundNum();
            bugdir = !bugdir;
        }

        //boolean hadLos = checkLineOfFire(myLocation, target, trees, robots, robotType.bodyRadius);
        boolean retval = bugMove(moveDir, Math.min(strideDistance, target.distanceTo(myLocation)));
        if (DEBUG) {
            if (johnny4_seeding.Util.DEBUG) System.out.println(olddist + " -> " + myLocation.distanceTo(target) + " : " + retval);
        }
        if (retval && olddist < myLocation.distanceTo(target) && (olddist < GO_STRAIGHT_DISTANCE || lastLOS >= rc.getRoundNum() - 1 && olddist < GO_STRAIGHT_DISTANCE * 2.5)) {
            if (DEBUG) {
                if (johnny4_seeding.Util.DEBUG) System.out.println("Switching bugdir because of distance");
            }
            bugdir = !bugdir;
        }
        if (johnny4_seeding.Util.DEBUG) System.out.println("end of findPath " + Clock.getBytecodeNum());
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
        if (evadeBullets && fireDir != null && Math.abs(fireDir.degreesBetween(dir)) < MIN_MOVE_TO_FIRE_ANGLE) {
            //if (Util.DEBUG) System.out.println(dir + " would collide with own bullet");
            max = 0.9f;
        }
        nloc = myLocation.add(dir, rc.getType().strideRadius);
        for (int i = 0; i < threatsLen; i++) {

            threat = threats[i];
            if ((threat.x - nloc.x) * (threat.x - nloc.x) + (threat.y - nloc.y) * (threat.y - nloc.y) < threat.radiusSquared) {
                //if (Util.DEBUG) System.out.println(nloc + " would be too close to " + threat.description + " at " + threat.loc);
                max = Math.max(max, (threat.radius - nloc.distanceTo(threat.loc)) * threat.severity + 1);
            }
        }
        if (max > 0.0001) return max;
        br = robotType.bodyRadius * robotType.bodyRadius;

        if (evadeBullets) {
            for (int i = 0; i < bulletLen; i++) {
                b1 = bullets[i].location;
                b2 = bullets[i].location.add(bullets[i].dir, bullets[i].speed / 2);
                b3 = bullets[i].location.add(bullets[i].dir, bullets[i].speed);
                if ((b1.x - nloc.x) * (b1.x - nloc.x) + (b1.y - nloc.y) * (b1.y - nloc.y) < br ||
                        (b2.x - nloc.x) * (b2.x - nloc.x) + (b2.y - nloc.y) * (b2.y - nloc.y) < br ||
                        (b3.x - nloc.x) * (b3.x - nloc.x) + (b3.y - nloc.y) * (b3.y - nloc.y) < br) {
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
        if (johnny4_seeding.Util.DEBUG) System.out.println("Start bugmove: " + Clock.getBytecodeNum());
        float ret = LJ_tryMove(dir, 52, 3, bugdir, dist);
        if (ret >= 180) {
            bugdir = !bugdir;
            if (johnny4_seeding.Util.DEBUG) System.out.println("Switching bugdir to evade enemy");
        }
        if (rand() < 0.02f || ret < 0 && rand() < 0.2f) {
            bugdir = !bugdir;
            if (johnny4_seeding.Util.DEBUG) System.out.println("Switching bugdir");
        }
        if (johnny4_seeding.Util.DEBUG) System.out.println("end bugmove: " + Clock.getBytecodeNum());
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
                        if (left) {
                            d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                        } else {
                            d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                        }
                        int clock2 = Clock.getBytecodeNum();
                        t = valueMove(d, dist);
                        int clock3 = Clock.getBytecodeNum();
                        if (Util.DEBUG)
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
                        if (Clock.getBytecodesLeft() < 800) break; //emergency brake
                    }
                    if (Clock.getBytecodesLeft() < 800) break;
                    if (attempt >= maxAttempt) break;
                    dist = attemptDist[attempt++];
                } while (true);
                left = !left;
                dist = strideDistance;
                if (Clock.getBytecodesLeft() < 800) break;
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

    public static class Threat {
        float x, y;
        float radiusSquared;
        MapLocation loc;
        float radius;
        float severity;
    }

}
