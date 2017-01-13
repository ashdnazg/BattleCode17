package johnny4_soldierRush;

import battlecode.common.*;
import johnny4_soldierRush.*;
import johnny4_soldierRush.Map;
import johnny4_soldierRush.Radio;

import static johnny4_soldierRush.Util.*;

public class Gardener {

    RobotController rc;
    johnny4_soldierRush.Radio radio;

    Map map;
    Direction[] treeDirs;
    Team myTeam;
    Team enemyTeam;
    int lastWatered;
    RobotType lastBuilt;
    RobotType lastThreat = RobotType.SCOUT;
    float health;
    int roundsSinceAttack;
    BulletInfo bullets[];
    boolean isFleeing;

    public Gardener(RobotController rc) {
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.treeDirs = new Direction[6];
        float angle = (float) Math.PI / 3.0f;
        for (int i = 0; i < 6; i++) {
            this.treeDirs[i] = new Direction(angle * i);
        }
        this.lastWatered = 0;
        this.myTeam = rc.getTeam();
        this.enemyTeam = rc.getTeam().opponent();
        this.lastBuilt = RobotType.LUMBERJACK;
        this.health = rc.getHealth();
        this.roundsSinceAttack = 999999;
    }

    public void run() {
        while (true) {
            tick();
            Clock.yield();
        }
    }

    protected void tick() {
        try {
            if (rc.getTeamBullets() >= 10000f) {
                rc.donate(10000f);
            }
            roundsSinceAttack++;
            int frame = rc.getRoundNum();
            radio.frame = frame;
            bullets = rc.senseNearbyBullets();
            MapLocation myLocation = rc.getLocation();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }

            RobotInfo[] nearbyRobots = map.sense();
            MapLocation nextEnemy = null;
            int nearbyProtectors = 0;
            for (RobotInfo r : nearbyRobots) {
                if (!r.getTeam().equals(rc.getTeam()) && (nextEnemy == null || nextEnemy.distanceTo(myLocation) > r.location.distanceTo(myLocation)) &&
                        (r.type == RobotType.SCOUT || r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    nextEnemy = r.location;
                    lastThreat = r.type;
                }
                if (r.getTeam().equals(rc.getTeam()) && (r.type == RobotType.LUMBERJACK || r.type == RobotType.SOLDIER || r.type == RobotType.TANK) && (r.attackCount + r.moveCount > 0 || r.health >= 0.95 * r.type.maxHealth)) {
                    nearbyProtectors++;
                }
            }

            float newHealth = rc.getHealth();
            if (newHealth < health || nextEnemy != null) {
                roundsSinceAttack = 0;
                radio.setAlarm();
            }
            health = newHealth;

            boolean alarm = radio.getAlarm();
            boolean inDanger = roundsSinceAttack < 10 || isFleeing;
            boolean rich = rc.getTeamBullets() > 1.2f * RobotType.SOLDIER.bulletCost;


            if (!isFleeing) {

                TreeInfo[] tis = rc.senseNearbyTrees(2.0f);
                if (!inDanger && (!alarm || rich) && tis.length < 5) {
                    boolean freePos = false;
                    for (int i = 0; i < 6; i++) {
                        if (rc.canPlantTree(treeDirs[i]) && radio.getUnitCounter() >= 4) {
                            if (!freePos) {
                                freePos = true;
                                continue;
                            }
                            rc.plantTree(treeDirs[i]);
                            break;
                        }
                        if (frame % 6 == i) {
                            MapLocation treeLocation = myLocation.add(treeDirs[i], 3.0f);
                            for (TreeInfo ti : rc.senseNearbyTrees(treeLocation, 1.0f, Team.NEUTRAL)) {
                                radio.requestTreeCut(ti);
                            }
                        }
                    }
                }

                // Try watering trees in some order
                if (tis.length > 0) {
                    lastWatered = (lastWatered + 1) % tis.length;
                    TreeInfo ti = tis[lastWatered];
                    if (ti.team == myTeam && rc.canWater(ti.ID)) {
                        rc.water(ti.ID);
                    }
                }
            }

            for (int i = 0; i < 6; i++) {
                // Check for soldier on purpose to allow the Archon to build gardeners
                if (rc.canBuildRobot(RobotType.SOLDIER, treeDirs[i]) && rc.isBuildReady() && nearbyProtectors < 3) {

                    rc.buildRobot(RobotType.SOLDIER, treeDirs[i]);
                    lastBuilt = RobotType.SOLDIER;

                    break;
                }
            }


            boolean hasMoved = tryEvade(bullets);
            if (hasMoved) {
                isFleeing = true;
                myLocation = rc.getLocation();
                roundsSinceAttack = 0;
            }


            if (nextEnemy == null) {
                if (isFleeing == true) {
                    System.out.println("Gardener escaped");
                }
                isFleeing = false;
            } else if (nextEnemy.distanceTo(myLocation) < 5) {
                isFleeing = true;
            }

            if (isFleeing && !hasMoved) {
                if (LJ_tryMove(nextEnemy.directionTo(myLocation), 20, 6)) {
                    hasMoved = true;
                    myLocation = rc.getLocation();
                } else {
                    //Welp
                    isFleeing = false;
                    System.out.println("Gardener stuck, resigning");
                }
            }

            if (rc.getRoundNum() - frame > 0) {
                System.out.println("Gardener took " + (rc.getRoundNum() - frame) + " frames at " + frame);
            }

        } catch (
                Exception e
                )

        {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }

    }

    private boolean canMove(Direction dir) {
        MapLocation nloc = rc.getLocation().add(dir, RobotType.SCOUT.strideRadius);
        float br = rc.getType().bodyRadius;
        for (BulletInfo bi : bullets) {
            if (bi.location.distanceTo(nloc) < br) {
                return false;
            }
        }
        return rc.canMove(dir);
    }

    private boolean canMove(Direction dir, float dist) {
        try {
            MapLocation nloc = rc.getLocation().add(dir, dist);
            float br = rc.getType().bodyRadius;
            for (BulletInfo bi : bullets) {
                if (bi.location.distanceTo(nloc) < br) {
                    return false;
                }
            }
            return rc.canMove(dir, dist);
        } catch (Exception ex) {

            System.out.println("canMove exception with args " + dir + ": " + dist);
            ex.printStackTrace();
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir) {
        try {
            return LJ_tryMove(dir, 42, 2);
        } catch (Exception ex) {
            return false;
        }
    }

    boolean LJ_tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        if (canMove(dir)) {
            rc.move(dir);
            return true;
        }
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            try {
                Direction d = dir.rotateLeftDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
                d = dir.rotateRightDegrees(degreeOffset * currentCheck);
                if (canMove(d)) {
                    rc.move(d);
                    return true;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            currentCheck++;
        }
        return false;
    }
}
