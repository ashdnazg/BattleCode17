package johnny4_20170111;
import battlecode.common.*;
import johnny4_20170111.Lumberjack;
import johnny4_20170111.Soldier;

public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
    **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        Util.rc = rc;

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                new johnny4_20170111.Archon(rc).run();
                break;
            case GARDENER:
                new johnny4_20170111.Gardener(rc).run();
                break;
            case SOLDIER:
                new Soldier(rc).run();
                break;
            case SCOUT:
                new Scout(rc).run();
                break;
            case LUMBERJACK:
                new Lumberjack(rc).run();
                break;
            case TANK:
                new Tank(rc).run();
                break;
        }
	}



}
