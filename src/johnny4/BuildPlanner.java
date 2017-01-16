package johnny4;

import battlecode.common.*;

public class BuildPlanner {

    static RobotController rc;
    static RobotInfo[] nearbyRobots;
    static final int MAX_TREES_PER_GARDENER = 6;

    static float money = 0;
    static int nearbyProtectors = 0;
    static RobotInfo nextEnemy = null;

    static int ownScouts;
    static int ownLumberjacks;
    static int ownSoldiers;
    static int ownGardeners;
    static int enemyScouts;
    static int enemyLumberjacks;
    static int enemySoldiers;
    static int enemyGardeners;
    static int frame;


    public static void update(RobotInfo[] nearbyRobots) {
        frame = rc.getRoundNum();

        BuildPlanner.nearbyRobots = nearbyRobots;
        nearbyProtectors = 0;
        money = rc.getTeamBullets();
        nextEnemy = null;
        MapLocation myLocation = rc.getLocation();
        for (RobotInfo r : nearbyRobots) {
            if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                nearbyProtectors++;
            }
            if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.location.distanceTo(myLocation) > r.location.distanceTo(myLocation)) &&
                    (r.type == RobotType.SCOUT || r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                nextEnemy = r;
            }
        }
        int ownCounts[] = Radio.countAllies();
        ownScouts = ownCounts[Radio.typeToInt(RobotType.SCOUT)];
        ownLumberjacks = ownCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        ownSoldiers = ownCounts[Radio.typeToInt(RobotType.SOLDIER)];
        ownGardeners = ownCounts[Radio.typeToInt(RobotType.GARDENER)];
        int enemyCounts[] = Radio.countEnemies();
        enemyScouts = enemyCounts[Radio.typeToInt(RobotType.SCOUT)];
        enemyLumberjacks = enemyCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        enemySoldiers = enemyCounts[Radio.typeToInt(RobotType.SOLDIER)];
        enemyGardeners = enemyCounts[Radio.typeToInt(RobotType.GARDENER)];
    }

    public static boolean buildTree() throws GameActionException {
        return money > GameConstants.BULLET_TREE_COST && (!Radio.getAlarm() || money > 200) && TreeStorage.ownTrees < MAX_TREES_PER_GARDENER && TreeStorage.ownTrees * ownGardeners < frame / 100 + 1;
    }


    public static boolean hireGardener() throws GameActionException {
        money = rc.getTeamBullets();
        boolean haveMoney = money > RobotType.GARDENER.bulletCost;
        if (!haveMoney) {
            return false;
        }

        int numGardeners = Radio.countAllies(RobotType.GARDENER);
        System.out.println("numGardeners: " + numGardeners);
        if (numGardeners == 0) {
            return true;
        }

        int activeGardeners = Radio.countActiveGardeners();
        System.out.println("activeGardeners: " + activeGardeners);
        if (activeGardeners == 0) {
            return true;
        }

        boolean rich = money > 2 * RobotType.GARDENER.bulletCost;
        System.out.println("rich: " + rich);
        if (rich && numGardeners == 1) {
            return true;
        }

        return false;
    }


    public static RobotType getUnitToBuild() throws GameActionException {

        boolean canSoldier = money > RobotType.SOLDIER.bulletCost;
        boolean canLumberjack = money > RobotType.LUMBERJACK.bulletCost;
        boolean canScout = money > RobotType.SCOUT.bulletCost;
        boolean rich = money > 200;

        if (nearbyProtectors > 4) return null; //dont overcrowd


        boolean alarm = Radio.getAlarm() ;
        boolean needSoldiers = (ownSoldiers < (enemySoldiers + enemyLumberjacks)) || (ownSoldiers < (enemyScouts / 2)) || rich;
        boolean noScouts = ownScouts == 0;

        boolean needLumberJacks = (ownLumberjacks < 1) || (ownLumberjacks < (ownSoldiers / 3));
        boolean needScouts = (ownScouts < 3) || (ownScouts < (ownSoldiers / 3));


        if (nextEnemy != null) { //in danger
            if (needLumberJacks && canLumberjack) {
                return RobotType.LUMBERJACK;
            } else if (needSoldiers && canSoldier) {
                return RobotType.SOLDIER;
            } else if (nextEnemy.type == RobotType.SCOUT && canLumberjack) {
                return RobotType.LUMBERJACK;
            } else if (canSoldier) {
                return RobotType.SOLDIER;
            }
        }

        if (alarm && !rich) return null;

        if (needSoldiers && canSoldier) {
            return RobotType.SOLDIER;
        }

        if (noScouts && canScout) {
            return RobotType.SCOUT;
        }

        if (needLumberJacks && canLumberjack) {
            return RobotType.LUMBERJACK;
        }

        if (needScouts && canScout) {
            return RobotType.SCOUT;
        }

        return null;
    }
}
