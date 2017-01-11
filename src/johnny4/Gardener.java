package johnny4;

import battlecode.common.*;

import static johnny4.Util.*;

public class Gardener {

    RobotController rc;
    Radio radio;

    Map map;
    Direction[] treeDirs;
    Team myTeam;
    int lastWatered;
    RobotType lastBuilt;

    public Gardener(RobotController rc){
        this.rc = rc;
        this.radio = new Radio(rc);
        this.map = new Map(rc, radio);
        this.treeDirs = new Direction[6];
        float angle = (float)Math.PI / 3.0f;
        for (int i = 0; i < 6; i++) {
            this.treeDirs[i] = new Direction(angle * i);
        }
        this.lastWatered = 0;
        this.myTeam = rc.getTeam();
        this.lastBuilt = RobotType.GARDENER;
    }

    public void run(){
        while(true){
            tick();
            Clock.yield();
        }
    }

    protected void tick(){
        try {
            if (rc.getTeamBullets() >= 10000f){
                rc.donate(10000f);
            }

            int frame = rc.getRoundNum();
            MapLocation myLocation = rc.getLocation();
            if (frame % 8 == 0) {
                radio.reportMyPosition(myLocation);
            }

            TreeInfo[] tis = rc.senseNearbyTrees(2.0f);
            if (tis.length < 5) {
                boolean freePos = false;
                for (int i = 0; i < 6; i++) {
                    if (rc.canPlantTree(treeDirs[i]) && radio.getUnitCounter() >= 3) {
                        if (!freePos) {
                            freePos = true;
                            continue;
                        }
                        rc.plantTree(treeDirs[i]);
                        break;
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

            for (int i = 0; i < 6; i++) {
                // Check for soldier on purpose to allow the Archon to build gardeners
                if (rc.canBuildRobot(RobotType.SOLDIER, treeDirs[i]) || (rc.canBuildRobot(RobotType.SCOUT, treeDirs[i]) && radio.getUnitCounter() < 3)) {
                    int scoutCount = radio.countAllies(RobotType.SCOUT);
                    if (scoutCount < 3) {
                        rc.buildRobot(RobotType.SCOUT, treeDirs[i]);
                        lastBuilt = RobotType.SCOUT;
                    } else if (scoutCount < 15 && lastBuilt == RobotType.SOLDIER) {
                        rc.buildRobot(RobotType.SCOUT, treeDirs[i]);
                        lastBuilt = RobotType.SCOUT;
                    } else if (lastBuilt != RobotType.LUMBERJACK) {               // UNCOMMENT WHEN LUMBERJACK HAS AI
                        if (radio.countAllies(RobotType.LUMBERJACK) < 10) {
                            rc.buildRobot(RobotType.LUMBERJACK, treeDirs[i]);
                        }
                        lastBuilt = RobotType.LUMBERJACK;
                    } else {
                        if (radio.countAllies(RobotType.SOLDIER) < 20) {
                            rc.buildRobot(RobotType.SOLDIER, treeDirs[i]);
                        }
                        lastBuilt = RobotType.SOLDIER;
                    }
                }
            }

            // Generate a random direction
            // Direction dir = randomDirection();

            // // Randomly attempt to build a soldier or lumberjack in this direction
            // if (rc.canBuildRobot(RobotType.SOLDIER, dir) && Math.random() < .02) {
                // rc.buildRobot(RobotType.SOLDIER, dir);
            // }

        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
    }
}
