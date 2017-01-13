package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Movement {

    static RobotType robotType;
    static RobotController rc;
    final float strideDistance;
    static int lastInit = -1;
    static RobotInfo[] robots;
    static TreeInfo[] trees;
    static BulletInfo[] bullets;
    static MapLocation myLocation;
    static MapLocation nextFriendlyLumberjack = null;
    static MapLocation nextEnemyLumberjack = null;
    static MapLocation nextArmedEnemy = null;
    static Direction fireDir;
    static Team myTeam, enemyTeam;
    final float MIN_FRIENDLY_LUMBERJACK_DIST;
    final float MIN_ENEMY_LUMBERJACK_DIST;
    final float MIN_ENEMY_DIST;
    final float MIN_MOVE_TO_FIRE_ANGLE;
    boolean evadeBullets = true;

    public Movement(RobotController rc) {
        this.rc = rc;
        robotType = rc.getType();
        strideDistance = robotType.strideRadius;
        myTeam = rc.getTeam();
        enemyTeam = rc.getTeam().opponent();
        switch (robotType) {
            case SCOUT:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 3.5f;
                break;
            case LUMBERJACK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                break;
            case SOLDIER:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 0f;
                break;
            case GARDENER:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f;
                MIN_ENEMY_DIST = 5f;
                break;
            case ARCHON:
                MIN_ENEMY_LUMBERJACK_DIST = RobotType.LUMBERJACK.bodyRadius + RobotType.SCOUT.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS + 0.01f + RobotType.LUMBERJACK.strideRadius;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                evadeBullets = false;
                break;
            default:
            case TANK:
                MIN_ENEMY_LUMBERJACK_DIST = 0;
                MIN_FRIENDLY_LUMBERJACK_DIST = 0;
                MIN_ENEMY_DIST = 0f;
                evadeBullets = false;
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
        nextEnemyLumberjack = nextFriendlyLumberjack = nextArmedEnemy = null;
        for (RobotInfo ri : robots) {
            if (MIN_FRIENDLY_LUMBERJACK_DIST > 0.01 && ri.getTeam().equals(myTeam) && (nextFriendlyLumberjack == null || nextFriendlyLumberjack.distanceTo(myLocation) > ri.location.distanceTo(myLocation))
                    && ri.type == RobotType.LUMBERJACK && ri.moveCount + ri.attackCount > 0) {
                nextFriendlyLumberjack = ri.location;
            } else if (MIN_ENEMY_LUMBERJACK_DIST > 0.01 && ri.getTeam().equals(enemyTeam) && (nextEnemyLumberjack == null || nextEnemyLumberjack.distanceTo(myLocation) > ri.location.distanceTo(myLocation))
                    && ri.type == RobotType.LUMBERJACK && ri.moveCount + ri.attackCount > 0) {
                nextEnemyLumberjack = ri.location;
            }
            if (ri.getTeam().equals(enemyTeam) && (nextArmedEnemy == null || nextArmedEnemy.distanceTo(myLocation) > ri.location.distanceTo(myLocation)) && ri.moveCount + ri.attackCount > 0 &&
                    (ri.type == RobotType.LUMBERJACK || ri.type == RobotType.SOLDIER || ri.type == RobotType.TANK || robotType == RobotType.GARDENER && ri.type == RobotType.SCOUT)) {
                nextArmedEnemy = ri.location;
            }
        }
        if (nextArmedEnemy == null) { // only keep distance to lumberjacks in combat
            nextFriendlyLumberjack = null;
        }
    }

    public boolean findPath(MapLocation target, Direction fireDir) {
        if (rc.getRoundNum() != lastInit) {
            new RuntimeException("Movement wasn't initialized since: " + lastInit).printStackTrace();
        }
        System.out.println("Pathfinding to " + target + "(dist: " + target.distanceTo(myLocation) + ", dir: " + myLocation.directionTo(target) + ") avoiding bullet in dir " + fireDir);
        this.fireDir = fireDir;

        //We're in an invalid position, go somewhere safe if possible
        /*if (nextArmedEnemy != null && myLocation.distanceTo(nextArmedEnemy) < MIN_ENEMY_DIST && bugMove(nextArmedEnemy.directionTo(myLocation))) {
            System.out.println("Evading armed enemy at " + nextArmedEnemy);
            return true;
        }
        if (nextFriendlyLumberjack != null && myLocation.distanceTo(nextFriendlyLumberjack) < MIN_FRIENDLY_LUMBERJACK_DIST && bugMove(nextFriendlyLumberjack.directionTo(myLocation))) {
            System.out.println("Evading friendly lumberjack at " + nextFriendlyLumberjack);
            return true;
        }
        if (nextEnemyLumberjack != null && myLocation.distanceTo(nextEnemyLumberjack) < MIN_ENEMY_LUMBERJACK_DIST && bugMove(nextEnemyLumberjack.directionTo(myLocation))) {
            System.out.println("Evading enemy lumberjack at " + nextEnemyLumberjack);
            return true;
        }*/

        boolean hadLos = checkLineOfFire(myLocation, target, trees, robots, robotType.bodyRadius);
        float olddist = myLocation.distanceTo(target);
        boolean retval = bugMove(myLocation.directionTo(target), Math.min(strideDistance, target.distanceTo(myLocation)));
        System.out.println(olddist + " -> " + myLocation.distanceTo(target) +" : " + retval);
        if (retval && olddist < myLocation.distanceTo(target) && (olddist < 4 || hadLos && olddist < 9)) {
            System.out.println("Switching bugdir because of distance");
            bugdir = !bugdir;
        }
        return retval;

    }


    private boolean canMove(Direction dir, boolean force) {
        return canMove(dir, strideDistance, force);
    }

    private boolean canMove(Direction dir, float dist, boolean force) {
        try {
            if (!rc.canMove(dir, dist) || dist > strideDistance) return false;
            if (fireDir != null && Math.abs(fireDir.degreesBetween(dir)) < MIN_MOVE_TO_FIRE_ANGLE && !force) {
                System.out.println(dir + " would collide with own bullet");
                return false;
            }
            MapLocation nloc = myLocation.add(dir, rc.getType().strideRadius);
            MapLocation nloc2 = myLocation.add(dir, rc.getType().strideRadius / 2);
            if (nextArmedEnemy != null && nloc.distanceTo(nextArmedEnemy) < MIN_ENEMY_DIST && (!force || myLocation.distanceTo(nextArmedEnemy) > MIN_ENEMY_DIST)) {
                System.out.println(nloc + " would be too close to armed enemy at " + nextArmedEnemy);
                return false;
            }
            if (nextFriendlyLumberjack != null && nloc.distanceTo(nextFriendlyLumberjack) < MIN_FRIENDLY_LUMBERJACK_DIST && (!force || myLocation.distanceTo(nextFriendlyLumberjack) > MIN_FRIENDLY_LUMBERJACK_DIST)) {
                System.out.println(nloc + " would be too close to friendly lumberjack at " + nextFriendlyLumberjack);
                return false;
            }
            if (nextEnemyLumberjack != null && nloc.distanceTo(nextEnemyLumberjack) < MIN_ENEMY_LUMBERJACK_DIST && (!force || myLocation.distanceTo(nextEnemyLumberjack) > MIN_ENEMY_LUMBERJACK_DIST)) {
                System.out.println(nloc + " would be too close to enemy lumberjack at " + nextEnemyLumberjack);
                return false;
            }
            MapLocation b1, b2, b3;
            float br = rc.getType().bodyRadius;
            if (evadeBullets) {
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
            }
            return true;
        } catch (Exception ex) {

            System.out.println("canMove exception with args " + dir + ": " + dist);
            ex.printStackTrace();
            return false;
        }
    }


    private boolean bugMove(Direction dir) {
        return bugMove(dir, strideDistance);
    }

    private boolean bugdir = rand() > 0.5f;

    private boolean bugMove(Direction dir, float dist) {
        boolean ret = LJ_tryMove(dir, 30, 5, bugdir, dist, false) ;
        if (!ret){
            if (LJ_tryMove(dir, 30, 6, !bugdir, dist, false) ){
                bugdir = !bugdir;
                System.out.println("Switching bugdir to evade enemy");
                return true;
            }
            ret = LJ_tryMove(dir, 40, 9, bugdir, dist, true);
        }
        if (rand() < 0.02f || !ret && rand() < 0.2f) {
            bugdir = !bugdir;
            System.out.println("Switching bugdir");
        }
        return ret;
    }

    private boolean LJ_tryMove(Direction dir, boolean force) {
        return LJ_tryMove(dir, 17 + rand() * 17, 6, true, strideDistance, force) || LJ_tryMove(dir, 17 + rand() * 17, 6, false, strideDistance, force);

    }

    private boolean LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide, boolean left, float dist, boolean force) {

        try {
            do {
                if (canMove(dir, dist, force)) {
                    rc.move(dir, dist);
                    myLocation = rc.getLocation();
                    return true;
                }
                boolean moved = false;
                int currentCheck = 1;

                while (currentCheck <= checksPerSide) {
                    try {
                        System.out.println("Checking angle" + degreeOffset * currentCheck + " in left dir " + left);
                        if (left) {
                            Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                            if (canMove(d, dist, force)) {
                                rc.move(d, dist);
                                myLocation = rc.getLocation();
                                return true;
                            }
                        } else {
                            Direction d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                            if (canMove(d, dist, force)) {
                                rc.move(d, dist);
                                myLocation = rc.getLocation();
                                return true;
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    currentCheck++;
                }
                dist /= 2.9f;
            } while (dist > 0.5f);
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

}
