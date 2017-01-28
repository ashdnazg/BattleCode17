package johnny4_sprint;

import battlecode.common.*;



// An insane stream broadcast
// starts in some index and has 2 * SIZE + 2 bytes.
// similar to counter, there's last time's info and this time's info

public class Stream {

    static RobotController rc;

    int readIndex;
    int maxSize;
    int writeIndex;
    public Stream(int index, int size) {
        readIndex = index;
        maxSize = size;
        writeIndex = readIndex + maxSize + 1;
    }

    public int read(int[] outArray) throws GameActionException {
        int start = readIndex + 1;
        int length = rc.readBroadcast(readIndex);
        for (int i = 0; i < length; ++i) {
            outArray[i] = rc.readBroadcast(start + i);
        }
        return length;
    }

    public int write(int[] reportArray) throws GameActionException {
        int offset = rc.readBroadcast(writeIndex);
        int start = writeIndex + offset;
        int length = Math.min(maxSize - offset, reportArray.length);
        for (int i = 0; i < length; ++i) {
            rc.broadcast(start + i, reportArray[i]);
        }
        rc.broadcast(writeIndex, offset + length);
        return length;
    }

    public int write(int value) throws GameActionException {
        int offset = rc.readBroadcast(writeIndex);
        if (offset < maxSize) {
            rc.broadcast(writeIndex + offset + 1, value);
            rc.broadcast(writeIndex, offset + 1);
            return 1;
        }
        return 0;
    }

    public void swap() {
        writeIndex = writeIndex ^ readIndex ^ (readIndex = writeIndex);
    }

    public void swapAndCommit() throws GameActionException {
        writeIndex = writeIndex ^ readIndex ^ (readIndex = writeIndex);
        rc.broadcast(writeIndex, 0);
    }

}
