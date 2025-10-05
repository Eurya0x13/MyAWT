package com.github.caciocavallosilano.cacio.ctc;

public class CTCAndroidInput {
    public static final int EVENT_TYPE_CHAR = 1000;
    // public static final int EVENT_TYPE_CHAR_MODS = 1001;
    // public static final int EVENT_TYPE_CURSOR_ENTER = 1002;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    // public static final int EVENT_TYPE_FRAMEBUFFER_SIZE = 1004;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;
    public static final int EVENT_TYPE_SCROLL = 1007;
    // public static final int EVENT_TYPE_WINDOW_SIZE = 1008;

    public static CTCRobotPeer mRobotPeer = new CTCRobotPeer();

    public static void receiveData(int type, int i1, int i2, int i3, int i4) {
        switch (type) {
            case EVENT_TYPE_CURSOR_POS:
                mRobotPeer.mouseMove(i1, i2);
                break;

            case EVENT_TYPE_KEY:
                // TODO implement hold state
                if(i3 == 1)mRobotPeer.keyPress(i2);
                else mRobotPeer.keyRelease(i2);
                break;
                
                case EVENT_TYPE_CHAR:
                // Send the char without checking shit
                mRobotPeer.keyPressUnchecked((char)i1);
                break;

            case EVENT_TYPE_MOUSE_BUTTON:
                if (i2 == 1) {
                    mRobotPeer.mousePress(i1);
                } else {
                    mRobotPeer.mouseRelease(i1);
                }
                break;
        }
    }
}
