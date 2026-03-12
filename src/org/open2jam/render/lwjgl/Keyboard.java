package org.open2jam.render.lwjgl;

import org.lwjgl.glfw.GLFW;

/**
 * GLFW keyboard compatibility bridge.
 * Maps GLFW key codes to LWJGL 2 key codes for backward compatibility.
 */
public class Keyboard {

    // LWJGL 2 key codes - common keys
    public static final int KEY_NONE = 0x00;
    public static final int KEY_ESCAPE = 0x01;
    public static final int KEY_1 = 0x02;
    public static final int KEY_2 = 0x03;
    public static final int KEY_3 = 0x04;
    public static final int KEY_4 = 0x05;
    public static final int KEY_5 = 0x06;
    public static final int KEY_6 = 0x07;
    public static final int KEY_7 = 0x08;
    public static final int KEY_8 = 0x09;
    public static final int KEY_9 = 0x0A;
    public static final int KEY_0 = 0x0B;
    public static final int KEY_MINUS = 0x0C;
    public static final int KEY_EQUALS = 0x0D;
    public static final int KEY_BACK = 0x0E;
    public static final int KEY_TAB = 0x0F;
    public static final int KEY_Q = 0x10;
    public static final int KEY_W = 0x11;
    public static final int KEY_E = 0x12;
    public static final int KEY_R = 0x13;
    public static final int KEY_T = 0x14;
    public static final int KEY_Y = 0x15;
    public static final int KEY_U = 0x16;
    public static final int KEY_I = 0x17;
    public static final int KEY_O = 0x18;
    public static final int KEY_P = 0x19;
    public static final int KEY_LBRACKET = 0x1A;
    public static final int KEY_RBRACKET = 0x1B;
    public static final int KEY_RETURN = 0x1C;
    public static final int KEY_LCONTROL = 0x1D;
    public static final int KEY_A = 0x1E;
    public static final int KEY_S = 0x1F;
    public static final int KEY_D = 0x20;
    public static final int KEY_F = 0x21;
    public static final int KEY_G = 0x22;
    public static final int KEY_H = 0x23;
    public static final int KEY_J = 0x24;
    public static final int KEY_K = 0x25;
    public static final int KEY_L = 0x26;
    public static final int KEY_SEMICOLON = 0x27;
    public static final int KEY_APOSTROPHE = 0x28;
    public static final int KEY_GRAVE = 0x29;
    public static final int KEY_LSHIFT = 0x2A;
    public static final int KEY_BACKSLASH = 0x2B;
    public static final int KEY_Z = 0x2C;
    public static final int KEY_X = 0x2D;
    public static final int KEY_C = 0x2E;
    public static final int KEY_V = 0x2F;
    public static final int KEY_B = 0x30;
    public static final int KEY_N = 0x31;
    public static final int KEY_M = 0x32;
    public static final int KEY_COMMA = 0x33;
    public static final int KEY_PERIOD = 0x34;
    public static final int KEY_SLASH = 0x35;
    public static final int KEY_RSHIFT = 0x36;
    public static final int KEY_MULTIPLY = 0x37;
    public static final int KEY_LMENU = 0x38;  // Alt
    public static final int KEY_SPACE = 0x39;
    public static final int KEY_CAPITAL = 0x3A;
    public static final int KEY_F1 = 0x3B;
    public static final int KEY_F2 = 0x3C;
    public static final int KEY_F3 = 0x3D;
    public static final int KEY_F4 = 0x3E;
    public static final int KEY_F5 = 0x3F;
    public static final int KEY_F6 = 0x40;
    public static final int KEY_F7 = 0x41;
    public static final int KEY_F8 = 0x42;
    public static final int KEY_F9 = 0x43;
    public static final int KEY_F10 = 0x44;
    public static final int KEY_NUMLOCK = 0x45;
    public static final int KEY_SCROLL = 0x46;
    public static final int KEY_NUMPAD7 = 0x47;
    public static final int KEY_NUMPAD8 = 0x48;
    public static final int KEY_NUMPAD9 = 0x49;
    public static final int KEY_SUBTRACT = 0x4A;
    public static final int KEY_NUMPAD4 = 0x4B;
    public static final int KEY_NUMPAD5 = 0x4C;
    public static final int KEY_NUMPAD6 = 0x4D;
    public static final int KEY_ADD = 0x4E;
    public static final int KEY_NUMPAD1 = 0x4F;
    public static final int KEY_NUMPAD2 = 0x50;
    public static final int KEY_NUMPAD3 = 0x51;
    public static final int KEY_NUMPAD0 = 0x52;
    public static final int KEY_DECIMAL = 0x53;
    public static final int KEY_F11 = 0x57;
    public static final int KEY_F12 = 0x58;
    public static final int KEY_F13 = 0x64;
    public static final int KEY_F14 = 0x65;
    public static final int KEY_F15 = 0x66;
    public static final int KEY_F16 = 0x67;
    public static final int KEY_F17 = 0x68;
    public static final int KEY_F18 = 0x69;
    public static final int KEY_KANA = 0x70;
    public static final int KEY_F19 = 0x71;
    public static final int KEY_CONVERT = 0x79;
    public static final int KEY_NOCONVERT = 0x7B;
    public static final int KEY_YEN = 0x7D;
    public static final int KEY_NUMPADEQUALS = 0x8D;
    public static final int KEY_CIRCUMFLEX = 0x90;
    public static final int KEY_AT = 0x91;
    public static final int KEY_COLON = 0x92;
    public static final int KEY_UNDERLINE = 0x93;
    public static final int KEY_KANJI = 0x94;
    public static final int KEY_STOP = 0x95;
    public static final int KEY_AX = 0x96;
    public static final int KEY_UNLABELED = 0x97;
    public static final int KEY_NUMPADENTER = 0x9C;
    public static final int KEY_RCONTROL = 0x9D;
    public static final int KEY_SECTION = 0xA7;
    public static final int KEY_NUMPADCOMMA = 0xB3;
    public static final int KEY_DIVIDE = 0xB5;
    public static final int KEY_SYSRQ = 0xB7;
    public static final int KEY_RMENU = 0xB8;  // Alt
    public static final int KEY_FUNCTION = 0xC4;
    public static final int KEY_PAUSE = 0xC5;
    public static final int KEY_HOME = 0xC7;
    public static final int KEY_UP = 0xC8;
    public static final int KEY_PRIOR = 0xC9;  // Page Up
    public static final int KEY_LEFT = 0xCB;
    public static final int KEY_RIGHT = 0xCD;
    public static final int KEY_END = 0xCF;
    public static final int KEY_DOWN = 0xD0;
    public static final int KEY_NEXT = 0xD1;   // Page Down
    public static final int KEY_INSERT = 0xD2;
    public static final int KEY_DELETE = 0xD3;
    public static final int KEY_CLEAR = 0xDA;
    public static final int KEY_LMETA = 0xDB;  // Windows key
    public static final int KEY_RMETA = 0xDC;  // Windows key

    // Key state tracking
    private static final boolean[] keyStates = new boolean[256];
    private static final boolean[] keyStatesPrevious = new boolean[256];

    /**
     * Translate GLFW key code to LWJGL 2 key code.
     */
    public static int translateKeyCode(int glfwKeyCode) {
        return switch (glfwKeyCode) {
            case GLFW.GLFW_KEY_SPACE -> KEY_SPACE;
            case GLFW.GLFW_KEY_APOSTROPHE -> KEY_APOSTROPHE;
            case GLFW.GLFW_KEY_COMMA -> KEY_COMMA;
            case GLFW.GLFW_KEY_MINUS -> KEY_MINUS;
            case GLFW.GLFW_KEY_PERIOD -> KEY_PERIOD;
            case GLFW.GLFW_KEY_SLASH -> KEY_SLASH;
            case GLFW.GLFW_KEY_0 -> KEY_0;
            case GLFW.GLFW_KEY_1 -> KEY_1;
            case GLFW.GLFW_KEY_2 -> KEY_2;
            case GLFW.GLFW_KEY_3 -> KEY_3;
            case GLFW.GLFW_KEY_4 -> KEY_4;
            case GLFW.GLFW_KEY_5 -> KEY_5;
            case GLFW.GLFW_KEY_6 -> KEY_6;
            case GLFW.GLFW_KEY_7 -> KEY_7;
            case GLFW.GLFW_KEY_8 -> KEY_8;
            case GLFW.GLFW_KEY_9 -> KEY_9;
            case GLFW.GLFW_KEY_SEMICOLON -> KEY_SEMICOLON;
            case GLFW.GLFW_KEY_EQUAL -> KEY_EQUALS;
            case GLFW.GLFW_KEY_A -> KEY_A;
            case GLFW.GLFW_KEY_B -> KEY_B;
            case GLFW.GLFW_KEY_C -> KEY_C;
            case GLFW.GLFW_KEY_D -> KEY_D;
            case GLFW.GLFW_KEY_E -> KEY_E;
            case GLFW.GLFW_KEY_F -> KEY_F;
            case GLFW.GLFW_KEY_G -> KEY_G;
            case GLFW.GLFW_KEY_H -> KEY_H;
            case GLFW.GLFW_KEY_I -> KEY_I;
            case GLFW.GLFW_KEY_J -> KEY_J;
            case GLFW.GLFW_KEY_K -> KEY_K;
            case GLFW.GLFW_KEY_L -> KEY_L;
            case GLFW.GLFW_KEY_M -> KEY_M;
            case GLFW.GLFW_KEY_N -> KEY_N;
            case GLFW.GLFW_KEY_O -> KEY_O;
            case GLFW.GLFW_KEY_P -> KEY_P;
            case GLFW.GLFW_KEY_Q -> KEY_Q;
            case GLFW.GLFW_KEY_R -> KEY_R;
            case GLFW.GLFW_KEY_S -> KEY_S;
            case GLFW.GLFW_KEY_T -> KEY_T;
            case GLFW.GLFW_KEY_U -> KEY_U;
            case GLFW.GLFW_KEY_V -> KEY_V;
            case GLFW.GLFW_KEY_W -> KEY_W;
            case GLFW.GLFW_KEY_X -> KEY_X;
            case GLFW.GLFW_KEY_Y -> KEY_Y;
            case GLFW.GLFW_KEY_Z -> KEY_Z;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> KEY_LBRACKET;
            case GLFW.GLFW_KEY_BACKSLASH -> KEY_BACKSLASH;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> KEY_RBRACKET;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> KEY_GRAVE;
            case GLFW.GLFW_KEY_ESCAPE -> KEY_ESCAPE;
            case GLFW.GLFW_KEY_ENTER -> KEY_RETURN;
            case GLFW.GLFW_KEY_TAB -> KEY_TAB;
            case GLFW.GLFW_KEY_BACKSPACE -> KEY_BACK;
            case GLFW.GLFW_KEY_INSERT -> KEY_INSERT;
            case GLFW.GLFW_KEY_DELETE -> KEY_DELETE;
            case GLFW.GLFW_KEY_RIGHT -> KEY_RIGHT;
            case GLFW.GLFW_KEY_LEFT -> KEY_LEFT;
            case GLFW.GLFW_KEY_DOWN -> KEY_DOWN;
            case GLFW.GLFW_KEY_UP -> KEY_UP;
            case GLFW.GLFW_KEY_PAGE_UP -> KEY_PRIOR;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KEY_NEXT;
            case GLFW.GLFW_KEY_HOME -> KEY_HOME;
            case GLFW.GLFW_KEY_END -> KEY_END;
            case GLFW.GLFW_KEY_CAPS_LOCK -> KEY_CAPITAL;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> KEY_SCROLL;
            case GLFW.GLFW_KEY_NUM_LOCK -> KEY_NUMLOCK;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> KEY_SYSRQ;
            case GLFW.GLFW_KEY_PAUSE -> KEY_PAUSE;
            case GLFW.GLFW_KEY_F1 -> KEY_F1;
            case GLFW.GLFW_KEY_F2 -> KEY_F2;
            case GLFW.GLFW_KEY_F3 -> KEY_F3;
            case GLFW.GLFW_KEY_F4 -> KEY_F4;
            case GLFW.GLFW_KEY_F5 -> KEY_F5;
            case GLFW.GLFW_KEY_F6 -> KEY_F6;
            case GLFW.GLFW_KEY_F7 -> KEY_F7;
            case GLFW.GLFW_KEY_F8 -> KEY_F8;
            case GLFW.GLFW_KEY_F9 -> KEY_F9;
            case GLFW.GLFW_KEY_F10 -> KEY_F10;
            case GLFW.GLFW_KEY_F11 -> KEY_F11;
            case GLFW.GLFW_KEY_F12 -> KEY_F12;
            case GLFW.GLFW_KEY_F13 -> KEY_F13;
            case GLFW.GLFW_KEY_F14 -> KEY_F14;
            case GLFW.GLFW_KEY_F15 -> KEY_F15;
            case GLFW.GLFW_KEY_F16 -> KEY_F16;
            case GLFW.GLFW_KEY_F17 -> KEY_F17;
            case GLFW.GLFW_KEY_F18 -> KEY_F18;
            case GLFW.GLFW_KEY_F19 -> KEY_F19;
            case GLFW.GLFW_KEY_KP_0 -> KEY_NUMPAD0;
            case GLFW.GLFW_KEY_KP_1 -> KEY_NUMPAD1;
            case GLFW.GLFW_KEY_KP_2 -> KEY_NUMPAD2;
            case GLFW.GLFW_KEY_KP_3 -> KEY_NUMPAD3;
            case GLFW.GLFW_KEY_KP_4 -> KEY_NUMPAD4;
            case GLFW.GLFW_KEY_KP_5 -> KEY_NUMPAD5;
            case GLFW.GLFW_KEY_KP_6 -> KEY_NUMPAD6;
            case GLFW.GLFW_KEY_KP_7 -> KEY_NUMPAD7;
            case GLFW.GLFW_KEY_KP_8 -> KEY_NUMPAD8;
            case GLFW.GLFW_KEY_KP_9 -> KEY_NUMPAD9;
            case GLFW.GLFW_KEY_KP_DECIMAL -> KEY_DECIMAL;
            case GLFW.GLFW_KEY_KP_DIVIDE -> KEY_DIVIDE;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> KEY_MULTIPLY;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> KEY_SUBTRACT;
            case GLFW.GLFW_KEY_KP_ADD -> KEY_ADD;
            case GLFW.GLFW_KEY_KP_ENTER -> KEY_NUMPADENTER;
            case GLFW.GLFW_KEY_KP_EQUAL -> KEY_NUMPADEQUALS;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> KEY_LSHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL -> KEY_LCONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT -> KEY_LMENU;
            case GLFW.GLFW_KEY_LEFT_SUPER -> KEY_LMETA;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> KEY_RSHIFT;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> KEY_RCONTROL;
            case GLFW.GLFW_KEY_RIGHT_ALT -> KEY_RMENU;
            case GLFW.GLFW_KEY_RIGHT_SUPER -> KEY_RMETA;
            default -> KEY_NONE;
        };
    }

    /**
     * Update key state - call this from GLFW key callback.
     */
    public static void setKeyState(int keyCode, boolean isPressed) {
        if (keyCode >= 0 && keyCode < keyStates.length) {
            keyStatesPrevious[keyCode] = keyStates[keyCode];
            keyStates[keyCode] = isPressed;
        }
    }

    /**
     * Check if a key is currently pressed.
     */
    public static boolean isKeyDown(int keyCode) {
        if (keyCode >= 0 && keyCode < keyStates.length) {
            return keyStates[keyCode];
        }
        return false;
    }

    /**
     * Check if a key was just pressed (this frame only).
     */
    public static boolean isKeyPressed(int keyCode) {
        if (keyCode >= 0 && keyCode < keyStates.length) {
            return keyStates[keyCode] && !keyStatesPrevious[keyCode];
        }
        return false;
    }

    /**
     * Poll keyboard state (no-op for GLFW, states updated via callbacks).
     */
    public static void poll() {
        // No-op: GLFW updates key states via callbacks
    }

    /**
     * Get the name of a key given its LWJGL 2 code.
     * Returns LWJGL 2 style key names for compatibility.
     */
    public static String getKeyName(int keyCode) {
        return switch (keyCode) {
            case KEY_NONE -> "NONE";
            case KEY_ESCAPE -> "Escape";
            case KEY_1 -> "1";
            case KEY_2 -> "2";
            case KEY_3 -> "3";
            case KEY_4 -> "4";
            case KEY_5 -> "5";
            case KEY_6 -> "6";
            case KEY_7 -> "7";
            case KEY_8 -> "8";
            case KEY_9 -> "9";
            case KEY_0 -> "0";
            case KEY_MINUS -> "-";
            case KEY_EQUALS -> "=";
            case KEY_BACK -> "Back";
            case KEY_TAB -> "Tab";
            case KEY_Q -> "Q";
            case KEY_W -> "W";
            case KEY_E -> "E";
            case KEY_R -> "R";
            case KEY_T -> "T";
            case KEY_Y -> "Y";
            case KEY_U -> "U";
            case KEY_I -> "I";
            case KEY_O -> "O";
            case KEY_P -> "P";
            case KEY_LBRACKET -> "[";
            case KEY_RBRACKET -> "]";
            case KEY_RETURN -> "Return";
            case KEY_LCONTROL -> "LControl";
            case KEY_A -> "A";
            case KEY_S -> "S";
            case KEY_D -> "D";
            case KEY_F -> "F";
            case KEY_G -> "G";
            case KEY_H -> "H";
            case KEY_J -> "J";
            case KEY_K -> "K";
            case KEY_L -> "L";
            case KEY_SEMICOLON -> ";";
            case KEY_APOSTROPHE -> "'";
            case KEY_GRAVE -> "`";
            case KEY_LSHIFT -> "LShift";
            case KEY_BACKSLASH -> "\\";
            case KEY_Z -> "Z";
            case KEY_X -> "X";
            case KEY_C -> "C";
            case KEY_V -> "V";
            case KEY_B -> "B";
            case KEY_N -> "N";
            case KEY_M -> "M";
            case KEY_COMMA -> ",";
            case KEY_PERIOD -> ".";
            case KEY_SLASH -> "/";
            case KEY_RSHIFT -> "RShift";
            case KEY_MULTIPLY -> "*";
            case KEY_LMENU -> "LMenu";
            case KEY_SPACE -> "Space";
            case KEY_CAPITAL -> "Capital";
            case KEY_F1 -> "F1";
            case KEY_F2 -> "F2";
            case KEY_F3 -> "F3";
            case KEY_F4 -> "F4";
            case KEY_F5 -> "F5";
            case KEY_F6 -> "F6";
            case KEY_F7 -> "F7";
            case KEY_F8 -> "F8";
            case KEY_F9 -> "F9";
            case KEY_F10 -> "F10";
            case KEY_NUMLOCK -> "NumLock";
            case KEY_SCROLL -> "Scroll";
            case KEY_NUMPAD7 -> "Numpad7";
            case KEY_NUMPAD8 -> "Numpad8";
            case KEY_NUMPAD9 -> "Numpad9";
            case KEY_SUBTRACT -> "-";
            case KEY_NUMPAD4 -> "Numpad4";
            case KEY_NUMPAD5 -> "Numpad5";
            case KEY_NUMPAD6 -> "Numpad6";
            case KEY_ADD -> "+";
            case KEY_NUMPAD1 -> "Numpad1";
            case KEY_NUMPAD2 -> "Numpad2";
            case KEY_NUMPAD3 -> "Numpad3";
            case KEY_NUMPAD0 -> "Numpad0";
            case KEY_DECIMAL -> ".";
            case KEY_F11 -> "F11";
            case KEY_F12 -> "F12";
            case KEY_F13 -> "F13";
            case KEY_F14 -> "F14";
            case KEY_F15 -> "F15";
            case KEY_F16 -> "F16";
            case KEY_F17 -> "F17";
            case KEY_F18 -> "F18";
            case KEY_F19 -> "F19";
            case KEY_NUMPADENTER -> "NumpadEnter";
            case KEY_RCONTROL -> "RControl";
            case KEY_NUMPADCOMMA -> ",";
            case KEY_DIVIDE -> "/";
            case KEY_SYSRQ -> "SysRq";
            case KEY_RMENU -> "RMenu";
            case KEY_PAUSE -> "Pause";
            case KEY_HOME -> "Home";
            case KEY_UP -> "Up";
            case KEY_PRIOR -> "PgUp";
            case KEY_LEFT -> "Left";
            case KEY_RIGHT -> "Right";
            case KEY_END -> "End";
            case KEY_DOWN -> "Down";
            case KEY_NEXT -> "PgDn";
            case KEY_INSERT -> "Insert";
            case KEY_DELETE -> "Delete";
            case KEY_LMETA -> "LWin";
            case KEY_RMETA -> "RWin";
            default -> "Key" + keyCode;
        };
    }

    /**
     * Get the key index from a name (legacy compatibility).
     */
    public static int getKeyIndex(String keyName) {
        if (keyName == null || keyName.isEmpty()) return KEY_NONE;
        // Simple mapping for common keys
        switch (keyName) {
            case "Space": return KEY_SPACE;
            case "A": return KEY_A;
            case "B": return KEY_B;
            case "C": return KEY_C;
            case "D": return KEY_D;
            case "E": return KEY_E;
            case "F": return KEY_F;
            case "G": return KEY_G;
            case "H": return KEY_H;
            case "I": return KEY_I;
            case "J": return KEY_J;
            case "K": return KEY_K;
            case "L": return KEY_L;
            case "M": return KEY_M;
            case "N": return KEY_N;
            case "O": return KEY_O;
            case "P": return KEY_P;
            case "Q": return KEY_Q;
            case "R": return KEY_R;
            case "S": return KEY_S;
            case "T": return KEY_T;
            case "U": return KEY_U;
            case "V": return KEY_V;
            case "W": return KEY_W;
            case "X": return KEY_X;
            case "Y": return KEY_Y;
            case "Z": return KEY_Z;
            default: return KEY_NONE;
        }
    }
}
