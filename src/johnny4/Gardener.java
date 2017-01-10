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
                for (int i = 0; i < 6; i++) {
                    if (rc.canPlantTree(treeDirs[i])) {
                        rc.plantTree(treeDirs[i]);
                        break;
                    }
                }
            }

            for (int i = 0; i < 6; i++) {
                if (rc.canBuildRobot(RobotType.TANK, treeDirs[i])) {
                    if (Math.random()> 0.5) rc.buildRobot(RobotType.SCOUT, treeDirs[i]);
                    else rc.buildRobot(RobotType.SOLDIER, treeDirs[i]);
                    break;
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
