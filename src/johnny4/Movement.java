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
    static float MIN_FRIENDLY_ARCHON_DIST;
    static float MIN_FRIENDLY_SOLDIER_DIST;
    static float MIN_OBSTACLE_DIST;
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
                MIN_FRIENDLY_ARCHON_DIST = 0;
                attemptDist[1] = 1.1f;
                MIN_FRIENDLY_SOLDIER_DIST = 0;
                MIN_OBSTACLE_DIST = 0;
                break;
            case LUMBERJACK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                MIN_ENEMY_SCOUT_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 00;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                MIN_FRIENDLY_ARCHON_DIST = 0;
                MIN_FRIENDLY_SOLDIER_DIST = 0;
                MIN_OBSTACLE_DIST = 0;
                break;
            case SOLDIER:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;//RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                MIN_ENEMY_SCOUT_DIST = 0f;
                GO_STRAIGHT_DISTANCE = 0.1f;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                MIN_FRIENDLY_ARCHON_DIST = 0;
                MIN_FRIENDLY_SOLDIER_DIST = 4; //overriden in soldier
                MIN_OBSTACLE_DIST = 0;
                break;
            case GARDENER:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 10f;
                MIN_ENEMY_SCOUT_DIST = 0;
                GO_STRAIGHT_DISTANCE = 0;
                MIN_FRIENDLY_GARDENER_DIST = 6;
                MIN_FRIENDLY_ARCHON_DIST = 0;
                MIN_FRIENDLY_SOLDIER_DIST = 0;
                MIN_OBSTACLE_DIST = 3;
                break;
            case ARCHON:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_GARDENER_DIST = 10f;
                MIN_ENEMY_DIST = 10f;
                GO_STRAIGHT_DISTANCE = 0;
                MIN_ENEMY_SCOUT_DIST = 0f;
                MIN_FRIENDLY_ARCHON_DIST = 10;
                MIN_FRIENDLY_SOLDIER_DIST = 0;
                MIN_OBSTACLE_DIST = 0;
                //evadeBullets = false;
                break;
            default:
            case TANK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                MIN_ENEMY_SCOUT_DIST = 0f;
                MIN_FRIENDLY_GARDENER_DIST = 0;
                MIN_FRIENDLY_ARCHON_DIST = 0;
                MIN_FRIENDLY_SOLDIER_DIST = 0;
                MIN_OBSTACLE_DIST = 0;
                evadeBullets = false;
        }
        MIN_MOVE_TO_FIRE_ANGLE = 90.01f - 180f / 3.14159265358979323f * (float) Math.acos(robotType.bodyRadius / (robotType.bodyRadius + GameConstants.BULLET_SPAWN_OFFSET));
        if (Util.DEBUG)
            System.out.println("min angle for " + robotType + " is " + MIN_MOVE_TO_FIRE_ANGLE + " mingard: " + MIN_FRIENDLY_GARDENER_DIST);
    }

    // Call this every frame before using
    public static void init(RobotInfo[] robots_, TreeInfo[] trees_, BulletInfo[] bullets_) {
        if (Util.DEBUG) System.out.println("Starting init " + Clock.getBytecodeNum());
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
            if (bullets_[i].damage < 1) continue;
            if (bulletLen >= bullets.length || bullets_[i].location.distanceTo(myLocation) > 10)
                break;
            bullets[bulletLen++] = bullets_[i];
        }
        boolean holdDistance = false;

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
                        currentThreat.severity = 0.1f;
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
                        holdDistance = true;
                        currentThreat.loc = ri.location;
                        currentThreat.x = ri.location.x;
                        currentThreat.y = ri.location.y;
                        currentThreat.radius = MIN_FRIENDLY_GARDENER_DIST;
                        currentThreat.radiusSquared = MIN_FRIENDLY_GARDENER_DIST * MIN_FRIENDLY_GARDENER_DIST;
                        //currentThreat.description = "friendly lumberjack";
                        currentThreat.severity = 0.3f;
                        currentThreat = threats[++threatsLen];
                        if (currentThreat == null) {
                            threats[threatsLen] = new Threat();
                            currentThreat = threats[threatsLen];
                        }
                    }
                }
                if (MIN_FRIENDLY_ARCHON_DIST > 0.01 && ri.type == RobotType.ARCHON) {
                    dist = ri.location.distanceTo(myLocation) - strideDistance;
                    if (dist < MIN_FRIENDLY_ARCHON_DIST) {
                        holdDistance = true;
                        currentThreat.loc = ri.location;
                        currentThreat.x = ri.location.x;
                        currentThreat.y = ri.location.y;
                        currentThreat.radius = MIN_FRIENDLY_ARCHON_DIST;
                        currentThreat.radiusSquared = MIN_FRIENDLY_ARCHON_DIST * MIN_FRIENDLY_ARCHON_DIST;
                        //currentThreat.description = "friendly lumberjack";
                        currentThreat.severity = 0.25f;
                        currentThreat = threats[++threatsLen];
                        if (currentThreat == null) {
                            threats[threatsLen] = new Threat();
                            currentThreat = threats[threatsLen];
                        }
                    }
                }
                if (MIN_FRIENDLY_SOLDIER_DIST > 0.01 && ri.type == RobotType.SOLDIER) {
                    dist = ri.location.distanceTo(myLocation) - strideDistance;
                    if (dist < MIN_FRIENDLY_SOLDIER_DIST) {
                        holdDistance = true;
                        currentThreat.loc = ri.location;
                        currentThreat.x = ri.location.x;
                        currentThreat.y = ri.location.y;
                        currentThreat.radius = MIN_FRIENDLY_SOLDIER_DIST;
                        currentThreat.radiusSquared = MIN_FRIENDLY_SOLDIER_DIST * MIN_FRIENDLY_SOLDIER_DIST;
                        //currentThreat.description = "friendly lumberjack";
                        currentThreat.severity = 0.1f;
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
                    currentThreat.severity = 1.2f;
                    //currentThreat.description = "armed enemy";
                    currentThreat = threats[++threatsLen];
                    if (currentThreat == null) {
                        threats[threatsLen] = new Threat();
                        currentThreat = threats[threatsLen];
                    }
                }
            }
        }
        if (noEnemies && !holdDistance) { // only keep distance to lumberjacks in combat
            threatsLen = 0;
            if (Util.DEBUG) System.out.println("No threats, hugging friendly lumberjacks");
        }
        if (myLocation.distanceTo(stuckLocation) > strideDistance * 2.6) {
            stuckLocation = myLocation;
            stuckSince = lastInit;
        }
        escaping = false;
        if (Util.DEBUG) System.out.println("ending init " + Clock.getBytecodeNum());
    }

    static MapLocation oldTarget = null;
    static int lastLOS = 0;
    static boolean escaping = false;

    public boolean findPath(MapLocation target, Direction fireDir) throws GameActionException {
        if (Util.DEBUG) System.out.println("Starting findPath " + Clock.getBytecodeNum());
        if (target == null) {
            if (Util.DEBUG) System.out.println("Pathfinding to null, that's easy");
            return false;
        }
        if (oldTarget != null && oldTarget.distanceTo(target) > 5) {
            stuckSince = rc.getRoundNum();
        }
        oldTarget = target;
        if (rc.getRoundNum() != lastInit) {
            new RuntimeException("Movement wasn't initialized since: " + lastInit).printStackTrace();
        }
        if (DEBUG) {
            if (Util.DEBUG)
                System.out.println("Pathfinding to " + target + "(dist: " + target.distanceTo(myLocation) + ", dir: " + myLocation.directionTo(target) + ") avoiding bullet in dir " + fireDir);
        }
        this.fireDir = fireDir;

        if (Util.DEBUG) System.out.println("Somewhere in findPath " + Clock.getBytecodeNum());

        boolean gonnaBeHit = false;
        Direction bulletDir = randomDirection();
        if (evadeBullets) {
            for (int i = 0; i < bulletLen; i++) {
                if (willCollideWithMe(myLocation, bullets[i]) > 0) {
                    gonnaBeHit = true;
                    bulletDir = bullets[i].dir;
                    break;
                }
            }
        }
        float olddist = myLocation.distanceTo(target);
        if (olddist < 0.0001f) {
            if (valueMove(Direction.getNorth(), 0) < 0.0001 && !gonnaBeHit) {
                if (Util.DEBUG) System.out.println("I'm already at target");
                return false;
            } else {
                if (Util.DEBUG) System.out.println("I'm already at target but it sucks being around here");
                target = target.add(bulletDir.rotateLeftDegrees(90 * (bugdir ? 1.001f : -1.001f)), strideDistance);
            }
        }
        Direction moveDir = myLocation.directionTo(target);
        if (fireDir != null && evadeBullets && Math.abs(moveDir.degreesBetween(fireDir)) < MIN_MOVE_TO_FIRE_ANGLE) {
            moveDir = fireDir.rotateLeftDegrees((bugdir ? 1.001f : -1.001f) * MIN_MOVE_TO_FIRE_ANGLE);

        }
        if (Util.DEBUG) rc.setIndicatorLine(myLocation, target, 0, 0, 255);
        if (robotType != RobotType.SCOUT && olddist > 0.9 * strideDistance) {
            for (int i = 0; i < 3; i++) {
                TreeInfo best = null;
                float t, bestval = 10000f;
                MapLocation nloc = myLocation.add(moveDir, strideDistance);
                for (TreeInfo tree : trees) {
                    if (MapLocation.doCirclesCollide(nloc, robotType.bodyRadius, tree.location, tree.radius)) {
                        t = Math.abs(myLocation.directionTo(tree.location).degreesBetween(moveDir));
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
                            if (Util.DEBUG) System.out.println("Found blocking tree #" + i + " at angle " + bestval);
                        }
                        if (Util.DEBUG) rc.setIndicatorLine(myLocation, best.location, 255, 0, 0);
                        //float sqrt = (float) Math.sqrt(myLocation.distanceSquaredTo(best.location) + best.radius * best.radius);
                        moveDir = toTree.rotateLeftRads((bugdir ? 1 : -1) * correctionAngle);

                        if (Util.DEBUG)
                            rc.setIndicatorLine(myLocation, myLocation.add(moveDir, (float) Math.sqrt(myLocation.distanceSquaredTo(best.location) + (robotType.bodyRadius + best.radius) * (robotType.bodyRadius + best.radius))), 0, 255, 0);
                    }
                } else {
                    if (Util.DEBUG) System.out.println("No blocking trees");
                    break;
                }
            }
        }
        if (Util.DEBUG) System.out.println("Somewhere else in findPath " + Clock.getBytecodeNum());
        if ((bugdir ? 1 : -1) * (moveDir.degreesBetween(myLocation.directionTo(target))) > 0) {
            if (Util.DEBUG) System.out.println("Switching bugdir because of trees");
            bugdir = !bugdir;
        } else if (olddist > 2 * strideDistance && lastInit - stuckSince > 6) {
            if (Util.DEBUG) System.out.println("Switching bugdir because of stuck");
            stuckSince = rc.getRoundNum() + 10;
            bugdir = !bugdir;
        }

        //boolean hadLos = checkLineOfFire(myLocation, target, trees, robots, robotType.bodyRadius);
        boolean retval = bugMove(moveDir, Math.min(strideDistance, target.distanceTo(myLocation)));
        if (DEBUG) {
            if (Util.DEBUG) System.out.println(olddist + " -> " + myLocation.distanceTo(target) + " : " + retval);
        }
        if (retval && !escaping && olddist < myLocation.distanceTo(target) && (olddist < GO_STRAIGHT_DISTANCE || GO_STRAIGHT_DISTANCE > 0 && lastLOS >= rc.getRoundNum() - 4 && olddist < 12)) {
            if (DEBUG) {
                if (Util.DEBUG)
                    System.out.println("Switching bugdir because of distance and los " + (lastLOS >= rc.getRoundNum() - 4));
            }
            bugdir = !bugdir;
        }
        if (Util.DEBUG) System.out.println("end of findPath " + Clock.getBytecodeNum());
        return retval;

    }


    private float valueMove(Direction dir) throws GameActionException {
        return valueMove(dir, strideDistance);
    }

    static MapLocation nloc;
    static float br, retval;

    static private float valueMove(Direction dir, float dist) throws GameActionException {
        Threat threat;
        if (!rc.canMove(dir, dist) || dist > strideDistance) return 1000;
        float max = 0;
        if (evadeBullets && fireDir != null && Math.abs(fireDir.degreesBetween(dir)) < MIN_MOVE_TO_FIRE_ANGLE) {
            //if (Util.DEBUG) System.out.println(dir + " would collide with own bullet");
            max = 0.9f;
        }
        nloc = myLocation.add(dir, rc.getType().strideRadius);
        if (MIN_OBSTACLE_DIST > 0.0001) {
            TreeInfo[] ntrees = rc.senseNearbyTrees(nloc, MIN_OBSTACLE_DIST + 0.05f, null);
            if (ntrees.length > 0) return 1f - 0.1f * (ntrees[0].location.distanceTo(nloc) - ntrees[0].radius);
            final float N = 11;
            for (int i = 0; i <= N; i++) {
                if (!rc.onTheMap(nloc.add(Direction.getNorth(), (i + 1) / N * MIN_OBSTACLE_DIST))) return 8f - 7f / N * i;
                if (!rc.onTheMap(nloc.add(Direction.getEast(), (i + 1) / N * MIN_OBSTACLE_DIST))) return 8f - 7f / N * i;
                if (!rc.onTheMap(nloc.add(Direction.getSouth(), (i + 1) / N * MIN_OBSTACLE_DIST))) return 8f - 7f / N * i;
                if (!rc.onTheMap(nloc.add(Direction.getWest(), (i + 1) / N * MIN_OBSTACLE_DIST))) return 8f - 7f / N * i;
            }
        }
        for (int i = 0; i < threatsLen; i++) {

            threat = threats[i];
            if ((threat.x - nloc.x) * (threat.x - nloc.x) + (threat.y - nloc.y) * (threat.y - nloc.y) < threat.radiusSquared) {
                //if (Util.DEBUG) System.out.println(nloc + " would be too close to " + threat.description + " at " + threat.loc);
                max +=  (threat.radius - nloc.distanceTo(threat.loc)) * threat.severity + 1;
            }
        }
        //if (max > 0.0001) return max;
        br = robotType.bodyRadius * robotType.bodyRadius;

        float bmax = 0;

        if (evadeBullets) {
            for (int i = 0; i < bulletLen; i++) {
                retval = willCollideWithMe(nloc, bullets[i]);
                if (retval > -0.001f && retval < 15) {
                    bmax = Math.max(bmax, (15f - retval) * 0.3f);
                }
            }
        }

        return max + bmax;
    }


    private boolean bugMove(Direction dir) {
        return bugMove(dir, strideDistance);
    }

    private boolean bugdir = rand() > 0.5f;

    private boolean bugMove(Direction dir, float dist) {
        if (Util.DEBUG) System.out.println("Start bugmove: " + Clock.getBytecodeNum());
        float ret = LJ_tryMove(dir, 52, 3, bugdir, dist);
        if (ret >= 180) {
            bugdir = !bugdir;
            if (Util.DEBUG) System.out.println("Switching bugdir to evade enemy");
        }
        if (Util.DEBUG) System.out.println("end bugmove: " + Clock.getBytecodeNum());
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
                            d = dir.rotateLeftDegrees(degreeOffset * currentCheck + (currentCheck > 0 ? rand() * degreeOffset - degreeOffset / 2f : 0));
                        } else {
                            d = dir.rotateRightDegrees(degreeOffset * currentCheck + (currentCheck > 0 ? rand() * degreeOffset - degreeOffset / 2f : 0));
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
                        if (Clock.getBytecodesLeft() < 5000) break; //emergency brake
                    }
                    if (Clock.getBytecodesLeft() < 5000) break;
                    if (attempt >= maxAttempt) break;
                    dist = attemptDist[attempt++];
                } while (true);
                left = !left;
                dist = strideDistance;
                if (Clock.getBytecodesLeft() < 5000) break;
            }
            if (bestVal > 999) {
                return -1f;
            } else {
                escaping = true;
                rc.move(bestDir, bestDist);
                return bestDeg;
            }
        } catch (Exception ex) {
            ex.printStackTrace();EXCEPTION();
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
