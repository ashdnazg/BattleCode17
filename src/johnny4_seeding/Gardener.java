package johnny4_seeding;

import battlecode.common.*;
import johnny4_seeding.*;
import johnny4_seeding.BuildPlanner;
import johnny4_seeding.Grid;
import johnny4_seeding.Map;
import johnny4_seeding.Movement;
import johnny4_seeding.Radio;
import johnny4_seeding.TreeStorage;
import johnny4_seeding.Util;
import johnny4_seeding.WellSpacedHexagonalClusters;

import static johnny4_seeding.Util.*;

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
    MapLocation lastRandomLocation;
    RobotInfo nearestProtector;
    int lastProtectorContact = -1000;

    public Gardener(RobotController rc) {
        if (Util.DEBUG) System.out.println("Constructing gardener: " + Clock.getBytecodeNum());
        this.rc = rc;
        this.radio = new Radio(rc);
        if (Util.DEBUG) System.out.println("a: " + Clock.getBytecodeNum());
        this.map = new Map(rc, radio);
        if (Util.DEBUG) System.out.println("b: " + Clock.getBytecodeNum());
        this.grid = new WellSpacedHexagonalClusters(rc);
        if (Util.DEBUG) System.out.println("c: " + Clock.getBytecodeNum());
        this.movement = new Movement(rc);
        if (Util.DEBUG) System.out.println("d: " + Clock.getBytecodeNum());
        TreeStorage.rc = rc;
        if (Util.DEBUG) System.out.println("e: " + Clock.getBytecodeNum());
        this.buildDirs = new Direction[12];
        float angle = (float) Math.PI / 6.0f;
        for (int i = 0; i < 12; i++) {
            this.buildDirs[i] = new Direction(angle * i);
        }
        this.myTeam = rc.getTeam();
        this.enemyTeam = rc.getTeam().opponent();
        this.health = rc.getHealth();
        this.roundsSinceAttack = 999999;
        this.MIN_CONSTRUCTION_MONEY = Math.min(GameConstants.BULLET_TREE_COST, RobotType.SCOUT.bulletCost);
        lastRandomLocation = rc.getLocation();

        BuildPlanner.rc = rc;
        if (Util.DEBUG) System.out.println("Finished Constructing gardener: " + Clock.getBytecodeNum());
        BuildPlanner.init();
        mustFindSpaceSince = rc.getRoundNum();
    }

    public void run() {
        while (true) {
            int frame = rc.getRoundNum();
            tick();
            if (frame != rc.getRoundNum()) {
                BYTECODE();
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
        if (DEBUG) System.out.println("Failed to build " + robotType);
        return false;
    }

    int mustFindSpaceSince;

    protected void tick() {
        try {
            preTick();
            if (Util.DEBUG) System.out.println("Starting gardener: " + Clock.getBytecodeNum());

            //Sensing
            roundsSinceAttack++;
            int frame = rc.getRoundNum();
            radio.frame = frame;
            bullets = rc.senseNearbyBullets();
            RobotInfo[] nearbyRobots = map.sense();
            TreeInfo[] trees = senseClosestTrees();
            MapLocation myLocation = rc.getLocation();
            float money = rc.getTeamBullets();
            boolean mustFindSpaceForTree = false;
            if (Util.DEBUG) System.out.println("Own trees: " + TreeStorage.ownTrees);

            BuildPlanner.update(nearbyRobots, trees);
            if (BuildPlanner.myTrees < BuildPlanner.MAX_TREES_PER_GARDENER && frame - mustFindSpaceSince < 10) {
                Radio.reportActiveGardener();
            }

            if (Util.DEBUG) System.out.println("gardener post header: " + Clock.getBytecodeNum());
            MapLocation nextEnemy = null;
            int nearbyProtectors = 0;
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) &&
                        (r.type == RobotType.SCOUT || r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    nextEnemy = r.location;
                }
                if (!r.getTeam().equals(rc.getTeam())&&(r.type != RobotType.SCOUT) ) {
                    Radio.reportContact();
                }
                if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    if (nearestProtector == null || nearestProtector.location.distanceTo(myLocation) < r.location.distanceTo(myLocation)) {
                        nearestProtector = r;
                        lastProtectorContact = frame;
                    }
                    nearbyProtectors++;
                }
            }
            if (frame - lastProtectorContact > 50) {
                nearestProtector = null;
            }
            float newHealth = rc.getHealth();
            if (newHealth < health || nextEnemy != null) {
                roundsSinceAttack = 0;
                radio.setAlarm();
                if (Util.DEBUG) System.out.println("Alarm!");
            }
            if (nearbyProtectors > 0 && (trees.length > 6 || BuildPlanner.myTrees >= 2)) {
                if (Util.DEBUG) System.out.println("Resuming work, I am protected");
                roundsSinceAttack = ATTACK_COOLDOWN_TIME;
                isFleeing = false;
            }
            health = newHealth;

            if (Util.DEBUG) System.out.println("gardener prepath: " + Clock.getBytecodeNum());
            // Trees


            movement.init(nearbyRobots, trees, bullets);
            boolean hasMoved = false;
            if (frame % 9 == 0) {
                TreeStorage.updateTrees(trees);
            } else if (roundsSinceAttack >= ATTACK_COOLDOWN_TIME) {
                //Try building new trees
                if (money > MIN_CONSTRUCTION_MONEY && BuildPlanner.buildTree()) {
                    MapLocation treeloc = grid.getNearestPlantableLocation(myLocation, null);
                    //request to cut annoying trees
                    for (TreeInfo t : trees) {
                        if (t.getTeam().equals(myTeam)) continue;
                        if (t.location.distanceTo(myLocation) - t.radius < 5 || treeloc != null && t.location.distanceTo(treeloc) < 4) {
                            radio.requestTreeCut(t);
                        }
                    }
                    if (Clock.getBytecodesLeft() < 5000) {
                        if (Util.DEBUG) System.out.println("Aborting gardener");
                        return;
                    }
                    if (treeloc != null) {
                        MapLocation walkloc = grid.getNearestWalkableLocation(treeloc);
                        if (walkloc != null) {
                            hasMoved = true;
                            movement.findPath(walkloc, null);
                            myLocation = rc.getLocation();
                            if (Util.DEBUG) rc.setIndicatorDot(walkloc, 0, 0, 255);
                            if (Util.DEBUG) rc.setIndicatorDot(treeloc, 0, 255, 0);
                            if ((myLocation.distanceTo(walkloc) < 0.01) && rc.canPlantTree(myLocation.directionTo(treeloc))) {
                                rc.plantTree(myLocation.directionTo(treeloc));
                                TreeStorage.plantedTree(rc.senseTreeAtLocation(treeloc));
                            }
                        }else{
                            mustFindSpaceForTree = true;
                        }
                    } else {
                        if (Util.DEBUG) System.out.println("No space for tree found");
                        mustFindSpaceForTree = true;
                    }
                }

                //Try watering existing trees
                if (!hasMoved) {
                    MapLocation tree = TreeStorage.waterTree(nearbyRobots);
                    if (tree != null) {
                        if (movement.findPath(tree.add(tree.directionTo(myLocation), 2), null)) {
                            myLocation = rc.getLocation();
                        }
                        hasMoved = true;
                    }
                }
            }
            if (!mustFindSpaceForTree){
                mustFindSpaceSince = frame;
            }
            if (Clock.getBytecodesLeft() < 800) {
                if (Util.DEBUG) System.out.println("Aborting gardener");
                return;
            }
            TreeStorage.tryWater(); //Try to water any trees in range

            if (Clock.getBytecodesLeft() < 800) {
                if (Util.DEBUG) System.out.println("Aborting gardener");
                return;
            }


            // Units

            if (money > RobotType.SCOUT.bulletCost) {
                tryBuild(BuildPlanner.getUnitToBuild());
            }


            //Evasion

            if (nextEnemy == null) {
                if (isFleeing == true) {
                    if (Util.DEBUG) System.out.println("Gardener escaped");
                }
                isFleeing = false;
            } else if (nextEnemy.distanceTo(myLocation) < 5 && nearbyProtectors <= 0) {
                isFleeing = true;
            }

            if (isFleeing) {
                roundsSinceAttack = 0;
            }
            if ((roundsSinceAttack < ATTACK_COOLDOWN_TIME || nextEnemy != null && myLocation.distanceTo(nextEnemy) < 6) && !hasMoved) {
                if (nextEnemy != null) {
                    if (nearestProtector != null) {
                        escapeLocation = nextEnemy.add(nextEnemy.directionTo(nearestProtector.location), 10);
                    } else {
                        escapeLocation = nextEnemy.add(nextEnemy.directionTo(myLocation), 10);
                    }
                }
                if (movement.findPath(escapeLocation, null)) {
                    myLocation = rc.getLocation();
                } else {
                    //Welp
                    isFleeing = false;
                    roundsSinceAttack = ATTACK_COOLDOWN_TIME;
                    if (Util.DEBUG) System.out.println("Gardener stuck, resigning");
                }
                hasMoved = true;
            }
            if (!hasMoved) {
                if (BuildPlanner.myTrees > 0 || !mustFindSpaceForTree) { //evade
                    if (Util.DEBUG) System.out.println("Evading possible bullets");
                    movement.findPath(myLocation, null);
                } else { //runs only when looking for tree space
                    if (Util.DEBUG) System.out.println("Walking randomly");
                    while (lastRandomLocation.distanceTo(myLocation) < 0.6 * RobotType.SCOUT.sensorRadius || !rc.onTheMap(myLocation.add(myLocation.directionTo(lastRandomLocation), 4)) || !movement.findPath(lastRandomLocation, null)) {
                        lastRandomLocation = myLocation.add(randomDirection(), 100);
                    }
                }
            }


            if (rc.getRoundNum() - frame > 0) {
                if (Util.DEBUG)
                    System.out.println("Gardener took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }

        } catch (Exception e) {
            if (Util.DEBUG) System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }
}
