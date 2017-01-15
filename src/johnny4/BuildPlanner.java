package johnny4;

import battlecode.common.*;

public class BuildPlanner {

    static RobotController rc;
    static RobotInfo[] nearbyRobots;
    static TreeStorage trees;
    static final int MAX_TREES_PER_GARDENER = 6;

    static float money = 0;
    static int nearbyProtectors = 0;

    public static void update(RobotInfo[] nearbyRobots){
        int frame = rc.getRoundNum();

        BuildPlanner.nearbyRobots = nearbyRobots;
        nearbyProtectors = 0;
        money = rc.getTeamBullets();
        for (RobotInfo r : nearbyRobots) {
            if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                nearbyProtectors++;
            }
        }
    }

    public static boolean buildTree(){
        return money > GameConstants.BULLET_TREE_COST && (!Radio.getAlarm() || money > 200) && trees.ownTrees < MAX_TREES_PER_GARDENER;
    }


    public static boolean hireGardener() throws GameActionException {
        money = rc.getTeamBullets();
        boolean haveMoney = money > RobotType.GARDENER.bulletCost;
        if (!haveMoney) {
            return false;
        }

        int numGardeners =  Radio.countAllies(RobotType.GARDENER);
        System.out.println("numGardeners: " + numGardeners);
        if (numGardeners == 0) {
            return true;
        }

        int activeGardeners = Radio.countActiveGardeners();
        System.out.println("activeGardeners: " + activeGardeners);
        if (activeGardeners == 0) {
            return false;
        }

        boolean rich = money > 2 * RobotType.GARDENER.bulletCost;
        System.out.println("rich: " + rich);
        if (rich && numGardeners == 1) {
            return true;
        }

        return false;
    }


    public static RobotType getUnitToBuild(){

        int ownCounts[] = Radio.countAllies();
        int ownScouts = ownCounts[Radio.typeToInt(RobotType.SCOUT)];
        int ownLumberjacks = ownCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        int ownSoldiers = ownCounts[Radio.typeToInt(RobotType.SOLDIER)];
        int enemyCounts[] = Radio.countEnemies();
        int enemyScouts = enemyCounts[Radio.typeToInt(RobotType.SCOUT)];
        int enemyLumberjacks = enemyCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        int enemySoldiers = enemyCounts[Radio.typeToInt(RobotType.SOLDIER)];

        boolean canSoldier = money > RobotType.SOLDIER.bulletCost;
        boolean canLumberjack = money > RobotType.LUMBERJACK.bulletCost;
        boolean canScout = money > RobotType.SCOUT.bulletCost;



        boolean alarm = Radio.getAlarm() && nearbyProtectors < 2;
        boolean needSoldiers = (ownSoldiers < enemySoldiers) || (ownSoldiers < (enemyScouts / 2));
        boolean noScouts = ownScouts == 0;

        boolean needLumberJacks = (ownLumberjacks < 1) || (ownLumberjacks < (ownSoldiers / 3));
        boolean needScouts = (ownScouts < 3) || (ownScouts < (ownSoldiers / 3));


        if (alarm) {
            if (needLumberJacks && canLumberjack) {
                return RobotType.LUMBERJACK;
            } else if (needSoldiers && canSoldier) {
                return RobotType.SOLDIER;
            }
        }

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
