package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Gardener {

    final int ATTACK_COOLDOWN_TIME = 10; //frames until resuming normal work

    RobotController rc;
    Radio radio;

    Map map;
    Direction[] buildDirs;
    Team myTeam;
    Team enemyTeam;
    float health;
    int roundsSinceAttack;
    BulletInfo bullets[];
    boolean isFleeing;
    Grid grid;
    Movement movement;
    MapLocation escapeLocation;
    final float MIN_CONSTRUCTION_MONEY;


    public Gardener(RobotController rc) {
        System.out.println("Constructing gardener: " + Clock.getBytecodeNum());
        this.rc = rc;
        this.radio = new Radio(rc);
        System.out.println("a: " + Clock.getBytecodeNum());
        this.map = new Map(rc, radio);
        System.out.println("b: " + Clock.getBytecodeNum());
        this.grid = new WellSpacedHexagonalClusters(rc);
        System.out.println("c: " + Clock.getBytecodeNum());
        this.movement = new Movement(rc);
        System.out.println("d: " + Clock.getBytecodeNum());
        TreeStorage.rc = rc;
        System.out.println("e: " + Clock.getBytecodeNum());
        this.buildDirs = new Direction[6];
        float angle = (float) Math.PI / 3.0f;
        for (int i = 0; i < 6; i++) {
            this.buildDirs[i] = new Direction(angle * i);
        }
        this.myTeam = rc.getTeam();
        this.enemyTeam = rc.getTeam().opponent();
        this.health = rc.getHealth();
        this.roundsSinceAttack = 999999;
        this.MIN_CONSTRUCTION_MONEY = Math.min(GameConstants.BULLET_TREE_COST, RobotType.SCOUT.bulletCost);

        BuildPlanner.rc = rc;
        System.out.println("Finished Constructing gardener: " + Clock.getBytecodeNum());
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

    public boolean tryBuild(RobotType robotType) throws GameActionException {
        if (robotType == null) return false;
        for (int i = 0; i < buildDirs.length; i++) {
            if (rc.canBuildRobot(robotType, buildDirs[i])) {
                rc.buildRobot(robotType, buildDirs[i]);
                Radio.reportBuild(robotType);
                return true;
            }
        }
        return false;
    }

    protected void tick() {
        try {
            preTick();
            System.out.println("Starting gardener: " + Clock.getBytecodeNum());

            //Sensing
            roundsSinceAttack++;
            int frame = rc.getRoundNum();
            radio.frame = frame;
            bullets = rc.senseNearbyBullets();
            MapLocation myLocation = rc.getLocation();
            float money = rc.getTeamBullets();
            System.out.println("Own trees: " + TreeStorage.ownTrees);

            if (TreeStorage.ownTrees < BuildPlanner.MAX_TREES_PER_GARDENER){
                Radio.reportActiveGardener();
            }

            RobotInfo[] nearbyRobots = map.sense();
            TreeInfo[] trees = senseBiggestTrees();
            System.out.println("gardener post header: " + Clock.getBytecodeNum());
            MapLocation nextEnemy = null;
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) &&
                        (r.type == RobotType.SCOUT || r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    nextEnemy = r.location;
                }
            }
            float newHealth = rc.getHealth();
            if (newHealth < health || nextEnemy != null) {
                roundsSinceAttack = 0;
                radio.setAlarm();
                System.out.println("Alarm!");
            }
            health = newHealth;

            System.out.println("gardener prepath: " + Clock.getBytecodeNum());
            // Trees

            movement.init(nearbyRobots, trees, bullets);
            if (money > MIN_CONSTRUCTION_MONEY) BuildPlanner.update(nearbyRobots);
            boolean hasMoved = false;
            if (frame % 9 == 0) {
                TreeStorage.updateTrees(trees);
            } else if (roundsSinceAttack >= ATTACK_COOLDOWN_TIME) {
                //Try building new trees
                if (money > MIN_CONSTRUCTION_MONEY && BuildPlanner.buildTree()) {
                    MapLocation treeloc = grid.getNearestPlantableLocation(myLocation, null);
                    if (treeloc != null) {
                        MapLocation walkloc = grid.getNearestWalkableLocation(treeloc);
                        hasMoved = movement.findPath(walkloc, null);
                        myLocation = rc.getLocation();
                        rc.setIndicatorDot(walkloc, 0, 0, 255);
                        rc.setIndicatorDot(treeloc, 0, 255, 0);
                        if ((myLocation.distanceTo(walkloc) < 0.01) && rc.canPlantTree(myLocation.directionTo(treeloc))) {
                            rc.plantTree(myLocation.directionTo(treeloc));
                            TreeStorage.plantedTree(rc.senseTreeAtLocation(treeloc));
                        }
                    }else{
                        System.out.println("No space for tree found");
                    }
                }

                //Try watering existing trees
                if (!hasMoved) {
                    MapLocation tree = TreeStorage.waterTree(nearbyRobots);
                    if (tree != null && movement.findPath(tree.add(tree.directionTo(myLocation), 2), null)) {
                        hasMoved = true;
                        myLocation = rc.getLocation();
                    }
                }
            }
            TreeStorage.tryWater(); //Try to water any trees in range


            // Units

            if (money > RobotType.SCOUT.bulletCost) {
                tryBuild(BuildPlanner.getUnitToBuild());
            }


            //Evasion

            if (nextEnemy == null) {
                if (isFleeing == true) {
                    System.out.println("Gardener escaped");
                }
                isFleeing = false;
            } else if (nextEnemy.distanceTo(myLocation) < 5) {
                isFleeing = true;
            }

            if (isFleeing) {
                roundsSinceAttack = 0;
            }
            if (roundsSinceAttack < ATTACK_COOLDOWN_TIME && !hasMoved) {
                if (nextEnemy != null) {
                    escapeLocation = nextEnemy.add(nextEnemy.directionTo(myLocation), 10);
                }
                if (movement.findPath(escapeLocation, null)) {
                    hasMoved = true;
                    myLocation = rc.getLocation();
                } else {
                    //Welp
                    isFleeing = false;
                    roundsSinceAttack = ATTACK_COOLDOWN_TIME;
                    System.out.println("Gardener stuck, resigning");
                }
            }


            if (rc.getRoundNum() - frame > 0) {
                System.out.println("Gardener took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }

        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }
}
