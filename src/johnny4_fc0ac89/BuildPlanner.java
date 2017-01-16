package johnny4_fc0ac89;

import battlecode.common.*;
import johnny4_fc0ac89.*;
import johnny4_fc0ac89.Archon;
import johnny4_fc0ac89.Radio;
import johnny4_fc0ac89.TreeStorage;

public class BuildPlanner {

    static RobotController rc;
    static RobotInfo[] nearbyRobots;
    static TreeInfo[] nearbyTrees;
    static final int MAX_TREES_PER_GARDENER = 3;

    static float money = 0;
    static int nearbyProtectors = 0;
    static int nearbyGardeners = 0;
    static int nearbyBulletTrees = 0;
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
    static float myTrees;


    public static void update(RobotInfo[] nearbyRobots, TreeInfo[] nearbyTrees) {
        frame = rc.getRoundNum();

        johnny4_fc0ac89.BuildPlanner.nearbyRobots = nearbyRobots;
        johnny4_fc0ac89.BuildPlanner.nearbyTrees = nearbyTrees;
        nearbyProtectors = nearbyGardeners = nearbyBulletTrees = 0;
        money = rc.getTeamBullets();
        nextEnemy = null;
        MapLocation myLocation = rc.getLocation();
        for (RobotInfo r : nearbyRobots) {
            if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                nearbyProtectors++;
            }
            if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.GARDENER)) {
                nearbyGardeners++;
            }
            if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.location.distanceTo(myLocation) > r.location.distanceTo(myLocation)) &&
                    (r.type == RobotType.SCOUT || r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                nextEnemy = r;
            }
        }
        Team myTeam = rc.getTeam();
        for (TreeInfo t : nearbyTrees) {
            if (t.getTeam().equals(myTeam)) {
                nearbyBulletTrees++;
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
        myTrees = johnny4_fc0ac89.TreeStorage.ownTrees + TreeStorage.otherTrees * (nearbyGardeners == 0 ? 1 : 0.5f );
    }

    public static boolean buildTree() throws GameActionException {
        return money > GameConstants.BULLET_TREE_COST && (/*!Radio.getAlarm() &&*/ myTrees * ownGardeners < frame / 100 + 1 || money > 100) && myTrees < MAX_TREES_PER_GARDENER;
    }


    public static boolean hireGardener() throws GameActionException {
        money = rc.getTeamBullets();
        boolean haveMoney = money > RobotType.GARDENER.bulletCost;
        if (!haveMoney) {
            return false;
        }

        //System.out.println("numGardeners: " + ownGardeners);
        //System.out.println("nearby Gardeners: " + nearbyGardeners);
        //System.out.println("nearby Trees: " + nearbyBulletTrees);
        if (ownGardeners == 0 || nearbyBulletTrees > 1 && nearbyGardeners == 0) {
            return true;
        }

        if (frame % ((Radio.countAllies(RobotType.ARCHON) - 1) * Math.log(nearbyGardeners * 2 + 1) * 5 + 1) > 0) {
            //System.out.println("Waiting for other Archon, Chance: " + (Math.log(Archon.gardenersSpawned * 2 + 1) * 10 + 1));
            return false;
        }

        int activeGardeners = Radio.countActiveGardeners();
        //System.out.println("activeGardeners: " + activeGardeners);
        if (activeGardeners == 0 && ownSoldiers + ownLumberjacks >= ownGardeners) {
            return true;
        }

        boolean rich = money > 120;
        //System.out.println("rich: " + rich);
        if (rich && ownGardeners <= (ownSoldiers + ownLumberjacks) / 2 + rc.getRoundNum() / 150) {
            return true;
        }

        return false;
    }


    public static RobotType getUnitToBuild() throws GameActionException {

        boolean canSoldier = money > RobotType.SOLDIER.bulletCost;
        boolean canLumberjack = money > RobotType.LUMBERJACK.bulletCost;
        boolean canScout = money > RobotType.SCOUT.bulletCost;
        boolean rich = money > 160 && rc.getRoundNum() > 80;

        if (nearbyProtectors > 4) return null; //dont overcrowd

        //System.out.println("own scouts: " + ownScouts);
        //System.out.println("own soldiers: " + ownSoldiers);
        //System.out.println("own gardeners: " + ownGardeners);
        //System.out.println("own lumberjacks: " + ownLumberjacks);
        //System.out.println("enemy scouts: " + enemyScouts);
        //System.out.println("enemy soldiers: " + enemySoldiers);
        //System.out.println("enemy gardeners: " + enemyGardeners);
        //System.out.println("enemy lumberjacks: " + enemyLumberjacks);
        //System.out.println("nearby trees: " + nearbyTrees.length);


        boolean alarm = Radio.getAlarm();
        boolean needSoldiers = (ownSoldiers * 1.5f + ownLumberjacks < (enemySoldiers + 0.5 * enemyLumberjacks)) || rich;
        boolean noScouts = ownScouts == 0;

        boolean needLumberJacks = (ownLumberjacks < (ownSoldiers / 3)) || (ownLumberjacks < Math.min(Radio.countTreeCutRequests(), ownSoldiers + 1 + (rich ? 10 : 0)));
        boolean needScouts = (ownScouts < 2 + ((ownLumberjacks + ownSoldiers) / 2)) || ownScouts < enemyScouts;


        if (nextEnemy != null && nearbyProtectors < 1) { //in danger
            if (needLumberJacks && canLumberjack) {
                return RobotType.LUMBERJACK;
            } else if (needSoldiers && canSoldier) {
                return RobotType.SOLDIER;
            } else if (nextEnemy.type == RobotType.SCOUT && canLumberjack) {
                return (nearbyTrees.length < 7) ? RobotType.LUMBERJACK : RobotType.SCOUT;
            } else if (canSoldier) {
                return RobotType.SOLDIER;
            }
        }

        if (alarm && !rich && nextEnemy == null) return null;


        if (noScouts && canScout) {
            return RobotType.SCOUT;
        }
        if (needScouts && canScout) {
            return RobotType.SCOUT;
        }

        if (needSoldiers && canSoldier) {
            return RobotType.SOLDIER;
        }
        if (needLumberJacks && canLumberjack) {
            return RobotType.LUMBERJACK;
        }


        return null;
    }
}
