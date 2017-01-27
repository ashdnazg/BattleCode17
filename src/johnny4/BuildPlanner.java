package johnny4;

import battlecode.common.*;

public class BuildPlanner {

    static RobotController rc;
    static RobotInfo[] nearbyRobots;
    static TreeInfo[] nearbyTrees;
    static final int MAX_TREES_PER_GARDENER = 3;

    static float money = 0;
    static int nearbyProtectors = 0;
    static int nearbyGardeners = 0;
    static int nearbyBulletTrees = 0;
    static int nearbyEnemies = 0;
    static RobotInfo nextEnemy = null;

    static int ownScouts;
    static int ownLumberjacks;
    static int ownSoldiers;
    static int lastOwnGardeners;
    static int ownGardeners;
    static int enemyScouts;
    static int enemyLumberjacks;
    static int enemySoldiers;
    static int enemyGardeners;
    static int frame;
    static int totalEnemies;
    static boolean alarm;
    static float myTrees;
    static float closestDanger;
    static int graceRounds;
    static float startArchonDist;
    static int lastScoutRound = 0;
    static boolean allOrNothing;
    static boolean closestArchon;
    static MapLocation nextEnemyFar = null;

    public static void init() {
        frame = rc.getRoundNum();
        if (frame > 10) {
            graceRounds = 0;
            return;
        }

        MapLocation myLocation = rc.getLocation();
        startArchonDist = 1e10f;
        float curDist;
        for (MapLocation myArch : rc.getInitialArchonLocations(rc.getTeam())) {
            for (MapLocation archonPos : Map.enemyArchonPos) {
                curDist = archonPos.distanceTo(myArch);
                if (curDist < startArchonDist) {
                    closestArchon = myArch.distanceTo(myLocation) < 2;
                    startArchonDist = curDist;
                }
            }
        }
        graceRounds = (int) (startArchonDist / RobotType.SCOUT.strideRadius) + 11;
        System.out.println("grace rounds:" + graceRounds);
    }

    public static void update(RobotInfo[] nearbyRobots, TreeInfo[] nearbyTrees) throws GameActionException {
        frame = rc.getRoundNum();
        --graceRounds;

        BuildPlanner.nearbyRobots = nearbyRobots;
        BuildPlanner.nearbyTrees = nearbyTrees;
        nearbyProtectors = nearbyGardeners = nearbyBulletTrees = 0;
        money = rc.getTeamBullets();
        nextEnemy = null;
        MapLocation myLocation = rc.getLocation();
        boolean friendly;
        closestDanger = 1e10f;
        float curDist;
        for (RobotInfo r : nearbyRobots) {
            friendly = r.team == rc.getTeam();
            if (friendly) {
                switch (r.type) {
                    case LUMBERJACK:
                    case SOLDIER:
                        nearbyProtectors++;
                        break;
                    case GARDENER:
                        nearbyGardeners++;
                        break;
                }
                continue;
            }
            nearbyEnemies++;
            curDist = r.location.distanceTo(myLocation);
            if (nextEnemy == null || closestDanger > curDist) {
                switch (r.type) {
                    case SCOUT:
                        lastScoutRound = frame;
                    case LUMBERJACK:
                    case SOLDIER:
                    case TANK:
                        nextEnemy = r;
                        closestDanger = curDist;
                        break;
                }

            }
        }
        myTrees = 0;
        Team myTeam = rc.getTeam();
        for (TreeInfo t : nearbyTrees) {
            if (t.team == myTeam && myLocation.distanceTo(t.location) < 2.0f) {
                myTrees++;
            }
        }
        nextEnemyFar = nextEnemy != null ? nextEnemy.location : Map.getTarget(Map.ARCHON, Map.GARDENER, Map.LUMBERJACK, Map.SCOUT, Map.SOLDIER, Map.TANK, 4, myLocation);
        if (frame%5 == 0){
            Map.generateFarTargets(rc, myLocation, 50, 0);
        }
        alarm = Radio.getAlarm();
        int ownCounts[] = Radio.countAllies();
        ownScouts = ownCounts[Radio.typeToInt(RobotType.SCOUT)];
        ownLumberjacks = ownCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        ownSoldiers = ownCounts[Radio.typeToInt(RobotType.SOLDIER)];
        ownGardeners = Math.max(ownCounts[Radio.typeToInt(RobotType.GARDENER)], lastOwnGardeners);
        lastOwnGardeners = ownCounts[Radio.typeToInt(RobotType.GARDENER)];
        int enemyCounts[] = Radio.countEnemies();
        totalEnemies = enemyCounts[0] + enemyCounts[1] + enemyCounts[2] + enemyCounts[3] + enemyCounts[4] + enemyCounts[5];
        enemyScouts = enemyCounts[Radio.typeToInt(RobotType.SCOUT)];
        enemyLumberjacks = enemyCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        enemySoldiers = enemyCounts[Radio.typeToInt(RobotType.SOLDIER)];
        enemyGardeners = enemyCounts[Radio.typeToInt(RobotType.GARDENER)];
        allOrNothing = frame < 200 && startArchonDist < 40;
        if (Util.DEBUG) System.out.println("own scouts: " + ownScouts);
        if (Util.DEBUG) System.out.println("own soldiers: " + ownSoldiers);
        if (Util.DEBUG) System.out.println("own gardeners: " + ownGardeners);
        if (Util.DEBUG) System.out.println("own lumberjacks: " + ownLumberjacks);
        if (Util.DEBUG) System.out.println("enemy scouts: " + enemyScouts);
        if (Util.DEBUG) System.out.println("enemy soldiers: " + enemySoldiers);
        if (Util.DEBUG) System.out.println("enemy gardeners: " + enemyGardeners);
        if (Util.DEBUG) System.out.println("enemy lumberjacks: " + enemyLumberjacks);
        if (Util.DEBUG) System.out.println("nearby trees: " + nearbyTrees.length);
        if (Util.DEBUG) System.out.println("tree cutting requests: " + Radio.countTreeCutRequests());
        if (Util.DEBUG) System.out.println("grace rounds: " + graceRounds);
    }

    public static boolean buildTree() throws GameActionException {
        if (money < GameConstants.BULLET_TREE_COST) {
            return false;
        }

        if (ownSoldiers < 2) {
            return false;
        }

        if (ownSoldiers * 1.2f + ownLumberjacks * 0.2 < (enemySoldiers + 0.3 * enemyLumberjacks)) {
            return false;
        }

        return true;
    }


    public static boolean hireGardener() throws GameActionException {
        money = rc.getTeamBullets();
        boolean haveMoney = money > ((nextEnemy == null) ? RobotType.GARDENER.bulletCost : RobotType.GARDENER.bulletCost + RobotType.SOLDIER.bulletCost);
        if (!haveMoney) {
            return false;
        }

        if (ownGardeners == 0 && ((money > 110 && frame > 4) || closestArchon)) {
            return true;
        }

        if (!allOrNothing && money > 140 && frame > 4) {
            return true;
        }

        if (ownGardeners > 0 && rc.getTreeCount() / ownGardeners > 3) {
            return true;
        }

        return false;
    }


    public static RobotType getUnitToBuild() throws GameActionException {

        boolean canSoldier = money > RobotType.SOLDIER.bulletCost;
        boolean canLumberjack = money > RobotType.LUMBERJACK.bulletCost;
        boolean canScout = money > RobotType.SCOUT.bulletCost;
        boolean rich = money > 160 && frame > 80;
        boolean enemyWasScouted = totalEnemies > 2;

        //if (nearbyProtectors > 4) return null; //dont overcrowd


        boolean needSoldiers = ownSoldiers < 1 + ownGardeners / 2 || (ownSoldiers * 1.2f + ownLumberjacks * 0.2 < (enemySoldiers + 0.3 * enemyLumberjacks));
        if (!Radio.getLandContact() && ownSoldiers >= 1) {
            //needSoldiers = false;
        }
        boolean noScouts = ownScouts == 0;
        boolean needLumberJacks = ((Radio.countTreeCutRequests() > 0 && ownLumberjacks == 0) && (ownLumberjacks < ((ownSoldiers+1) / 3))) || (!needSoldiers && ownLumberjacks < Radio.countTreeCutRequests() && ownLumberjacks < 2);
        boolean needScouts = ownScouts <= ownSoldiers / 3 && ownScouts < 3 || !Radio.getLandContact() && frame >= 42 && ownScouts < Math.min(ownGardeners, 3);
        if (!Radio.getLandContact() && ownLumberjacks >= 2) {
            needLumberJacks = false;
        }

        if (Util.DEBUG) System.out.println("needSoldiers: " + needSoldiers);
        if (Util.DEBUG) System.out.println("noScouts: " + noScouts);
        if (Util.DEBUG) System.out.println("needLumberJacks: " + needLumberJacks);
        if (Util.DEBUG) System.out.println("needScouts: " + needScouts);

        if (noScouts && canScout && !Radio.getLandContact()) {
            return RobotType.SCOUT;
        }

        if (allOrNothing) {
            if (ownSoldiers < 2 && (ownLumberjacks > 0 || !Util.tooManyTrees)) {
                return RobotType.SOLDIER;
            }
            if (ownLumberjacks == 0) {
                return RobotType.LUMBERJACK;
            }
        }


        if (allOrNothing || (nearbyProtectors < 1) && (frame - lastScoutRound) < 100) { //in danger
            if (needLumberJacks && canLumberjack) {
                return RobotType.LUMBERJACK;
            } else if (needSoldiers && canSoldier) {
                return RobotType.SOLDIER;
            } else if (nextEnemy != null && nextEnemy.type == RobotType.SCOUT && canLumberjack) {
                return /*(nearbyTrees.length < 7) ? RobotType.LUMBERJACK : */RobotType.SCOUT;
            } else if (canSoldier) {
                return RobotType.SOLDIER;
            }
        }

        //if (alarm && !rich && nextEnemy == null) return null;


        if (needSoldiers && canSoldier && (ownLumberjacks > 0 || !Util.tooManyTrees)) {
            return RobotType.SOLDIER;
        }

        if (needLumberJacks && canLumberjack) {
            return RobotType.LUMBERJACK;
        }

        if (needScouts && canScout) {
            return RobotType.SCOUT;
        }
        if (Util.DEBUG) System.out.println("nothign to build");

        return null;
    }
}
