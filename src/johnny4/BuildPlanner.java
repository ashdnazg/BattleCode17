package johnny4;

import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public class BuildPlanner {

    static RobotController rc;
    static Radio radio;
    static RobotInfo[] nearbyRobots;
    static TreeStorage trees;

    static float money = 0;
    static int nearbyProtectors = 0;

    public BuildPlanner(RobotController rc, Radio radio, TreeStorage trees) {
        this.rc = rc;
        this.radio = radio;
        this.trees = trees;
    }

    public void update(RobotInfo[] nearbyRobots){
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

    public boolean buildTree(){
        return  money > GameConstants.BULLET_TREE_COST;
    }

    public RobotType getUnitToBuild(){

        int ownCounts[] = radio.countAllies();
        int ownScouts = ownCounts[Radio.typeToInt(RobotType.SCOUT)];
        int ownLumberjacks = ownCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        int ownSoldiers = ownCounts[Radio.typeToInt(RobotType.SOLDIER)];
        int enemyCounts[] = radio.countEnemies();
        int enemyScouts = enemyCounts[Radio.typeToInt(RobotType.SCOUT)];
        int enemyLumberjacks = enemyCounts[Radio.typeToInt(RobotType.LUMBERJACK)];
        int enemySoldiers = enemyCounts[Radio.typeToInt(RobotType.SOLDIER)];

        return null;
    }
}
