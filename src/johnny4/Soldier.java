package johnny4;

import battlecode.common.*;

import java.util.Optional;

import static johnny4.Util.*;

public class Soldier {

    RobotController rc;
    Map map;

    public Soldier(RobotController rc){
        this.rc = rc;
        this.map = new Map(rc);
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
            map.sense();

            MapLocation myLocation = rc.getLocation();

            // See if there are any nearby enemy robots
            Optional<MapLocation> target = map.getTarget(myLocation);

            if (!target.isPresent()) {
                tryMove(randomDirection());
            }else{
                if (myLocation.distanceTo(target.get()) < 0.7f * RobotType.SOLDIER.sensorRadius){
                    tryMove(target.get().directionTo(myLocation));
                }else{
                    tryMove(myLocation.directionTo(target.get()));
                }
                rc.fireSingleShot(myLocation.directionTo(target.get()));
            }


        } catch (Exception e) {
            System.out.println("Soldier Exception");
            e.printStackTrace();
        }
    }
}
