package org.open2jam.gui.parts;

import java.awt.Font;
import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import org.open2jam.render.lwjgl.Keyboard;
import org.open2jam.Config;
import org.open2jam.GameOptions;
import org.open2jam.parsers.Event;
import org.open2jam.render.lwjgl.TrueTypeFont;
import org.open2jam.util.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Configuration panel for keyboard mappings and VLC path.
 * Refactored to use standard Swing without beansbinding.
 *
 * @author CdK
 */
public class Configuration extends JPanel {

    private EnumMap<Event.Channel, Integer> kb_map;
    private HashMap<Integer, Event.Channel> table_map = new HashMap<>();
    private String vlc_path;

    private final JButton bSave;
    private final JPanel panel_keys;
    private final JLabel jLabel1;
    private final JComboBox<String> combo_keyboardConfig;
    private final JScrollPane tKeys_scroll;
    private final JTable tKeys;
    private final JLabel lbl_vlc;
    private final JButton btn_vlc;

    private static FileFilter vlc_filter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            if (file.isDirectory()) return true;
            String name = file.getName().toLowerCase();
            return name.startsWith("libvlc");
        }

        @Override
        public String getDescription() {
            return "libvlc file";
        }
    };

    public Configuration() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        bSave = new JButton("Save");
        bSave.setFont(new Font("Tahoma", 1, 11));
        bSave.setMaximumSize(new java.awt.Dimension(65, 23));
        bSave.addActionListener(e -> bSaveActionPerformed());

        panel_keys = new JPanel();
        jLabel1 = new JLabel("Select the keyboard configuration you want to edit:");
        combo_keyboardConfig = new JComboBox<>(new String[]{"7 Keys", "5 Keys", "6 Keys", "4 Keys", "8 Keys"});
        combo_keyboardConfig.addActionListener(e -> combo_keyboardConfigActionPerformed());

        tKeys = new JTable(new DefaultTableModel(new Object[][]{}, new String[]{"Key", "Assign"}) {
            Class[] types = new Class[]{String.class, Object.class};
            boolean[] canEdit = new boolean[]{false, false};

            public Class getColumnClass(int columnIndex) {
                return types[columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit[columnIndex];
            }
        });
        tKeys.setColumnSelectionAllowed(true);
        tKeys.getTableHeader().setReorderingAllowed(false);
        tKeys.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                tKeysMouseClicked(evt);
            }
        });
        tKeys_scroll = new JScrollPane(tKeys);

        panel_keys.setLayout(new BoxLayout(panel_keys, BoxLayout.Y_AXIS));
        JPanel topPanel = new JPanel();
        topPanel.add(jLabel1);
        topPanel.add(combo_keyboardConfig);
        panel_keys.add(topPanel);
        panel_keys.add(tKeys_scroll);

        lbl_vlc = new JLabel("VLC isn't selected");
        lbl_vlc.setAlignmentX(CENTER_ALIGNMENT);
        btn_vlc = new JButton("Select VLC path");
        btn_vlc.addActionListener(e -> btn_vlcActionPerformed());

        add(panel_keys);
        add(Box.createVerticalStrut(10));
        add(lbl_vlc);
        add(Box.createVerticalStrut(10));
        add(btn_vlc);
        add(Box.createVerticalStrut(10));
        add(bSave);

        loadTableKeys(Config.KeyboardType.K7);
        vlc_path = Config.getGameOptions().getVLCLibraryPath();
        if (vlc_path.isEmpty()) {
            lbl_vlc.setText("Select the VLC path");
        } else {
            lbl_vlc.setText("VLC Path: " + vlc_path);
        }
    }

    private void bSaveActionPerformed() {
        Config.KeyboardType kt;
        switch (combo_keyboardConfig.getSelectedIndex()) {
            case 0: kt = Config.KeyboardType.K7; break;
            case 1: kt = Config.KeyboardType.K5; break;
            case 2: kt = Config.KeyboardType.K6; break;
            case 3: kt = Config.KeyboardType.K4; break;
            case 4: kt = Config.KeyboardType.K8; break;
            default: return;
        }
        Config.setKeyboardMap(kb_map, kt);
        GameOptions op = Config.getGameOptions();
        op.setVLCLibraryPath(vlc_path);
        Config.setGameOptions(op);
    }

    private void tKeysMouseClicked(java.awt.event.MouseEvent evt) {
        final int row = tKeys.getSelectedRow();
        if (row < 0) return;
        if (tKeys.getValueAt(row, 0) == null) return;
        if (tKeys.getValueAt(row, 1) == null) return;
        getTopLevelAncestor().setEnabled(false);
        new Thread(() -> {
            work(row);
            SwingUtilities.invokeLater(() -> getTopLevelAncestor().setEnabled(true));
        }).start();
    }

    private void work(int row) {
        Object valueObj = tKeys.getValueAt(row, 1);
        if (valueObj == null) return;
        String currentValue = valueObj.toString();
        if (currentValue == null || currentValue.isEmpty()) return;
        int lastkey = Keyboard.translateKeyCode(getGlfwKeyFromName(currentValue));
        int code;
        try {
            code = read_keyboard_key(lastkey);
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to read keyboard key", e);
            return;
        }
        if (kb_map.containsValue(code)) return;
        Event.Channel c = table_map.get(row);
        if (c == null) return;
        kb_map.put(c, code);
        String keyName = Keyboard.getKeyName(code);
        SwingUtilities.invokeLater(() -> tKeys.setValueAt(keyName, row, 1));
    }

    private void combo_keyboardConfigActionPerformed() {
        switch (combo_keyboardConfig.getSelectedIndex()) {
            case 0: loadTableKeys(Config.KeyboardType.K7); break;
            case 1: loadTableKeys(Config.KeyboardType.K5); break;
            case 2: loadTableKeys(Config.KeyboardType.K6); break;
            case 3: loadTableKeys(Config.KeyboardType.K4); break;
            case 4: loadTableKeys(Config.KeyboardType.K8); break;
        }
    }

    private void btn_vlcActionPerformed() {
        JFileChooser jfc = new JFileChooser();
        if (!vlc_path.isEmpty())
            jfc.setCurrentDirectory(new File(vlc_path));
        jfc.setDialogTitle("Choose the libvlc file");
        jfc.addChoosableFileFilter(vlc_filter);
        jfc.setAcceptAllFileFilterUsed(false);
        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            vlc_path = jfc.getSelectedFile().getParent();
            lbl_vlc.setText("VLC Path: " + vlc_path);
        }
    }

    private void loadTableKeys(Config.KeyboardType kt) {
        kb_map = Config.getKeyboardMap(kt).clone();
        DefaultTableModel dm = (DefaultTableModel) tKeys.getModel();
        dm.setRowCount(0);
        dm.setRowCount(kb_map.size());
        int i = 0;
        for (Map.Entry<Event.Channel, Integer> entry : kb_map.entrySet()) {
            tKeys.setValueAt(entry.getKey().toString(), i, 0);
            tKeys.setValueAt(Keyboard.getKeyName(entry.getValue()), i, 1);
            table_map.put(i, entry.getKey());
            i++;
        }
    }

    private static Font font = new Font("Tahoma", Font.BOLD, 14);

    private int read_keyboard_key(int lastkey) throws Exception {
        String place = tKeys.getValueAt(tKeys.getSelectedRow(), 0).toString();

        // Initialize GLFW for key capture window
        boolean glfwAlreadyInit = GLFW.glfwInit();
        
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        // Remove FLOATING - it can cause issues on some systems

        long window = GLFW.glfwCreateWindow(320, 120, place, 0, 0);
        if (window == 0) {
            if (!glfwAlreadyInit) GLFW.glfwTerminate();
            throw new Exception("Failed to create GLFW window");
        }

        // Center the window
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var winX = stack.mallocInt(1);
            var winY = stack.mallocInt(1);
            var monX = stack.mallocInt(1);
            var monY = stack.mallocInt(1);
            var monW = stack.mallocInt(1);
            var monH = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(window, winX, winY);
            GLFW.glfwGetMonitorWorkarea(GLFW.glfwGetPrimaryMonitor(), monX, monY, monW, monH);
            GLFW.glfwSetWindowPos(window, monX.get(0) + (monW.get(0) - 320) / 2, monY.get(0) + (monH.get(0) - 120) / 2);
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(0);

        // Create OpenGL capabilities for this context (LWJGL 3 requirement)
        GL.createCapabilities();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 320, 120, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        TrueTypeFont trueTypeFont = new TrueTypeFont(font, false);

        // Track key states for edge detection
        boolean[] keyWasPressed = new boolean[GLFW.GLFW_KEY_LAST + 1];
        int capturedKey = -1;
        
        // Show and focus window
        GLFW.glfwShowWindow(window);
        GLFW.glfwFocusWindow(window);
        
        // Wait for key press using polling with edge detection
        long startTime = System.currentTimeMillis();
        while (capturedKey < 0) {
            // Timeout after 10 seconds
            if (System.currentTimeMillis() - startTime > 10000) {
                capturedKey = lastkey;
                break;
            }
            
            // Clear and draw
            GL11.glClearColor(0.15f, 0.15f, 0.2f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glLoadIdentity();
            
            trueTypeFont.drawString(20, 20, "Press a KEY", 1, -1);
            trueTypeFont.drawString(20, 50, "for: " + place, 1, -1);
            trueTypeFont.drawString(20, 80, "ESC to cancel", 1, -1);
            
            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
            
            // Check for NEW key press (edge detection)
            for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
                boolean isPressed = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
                
                // Detect key press edge (was not pressed, now is pressed)
                if (isPressed && !keyWasPressed[key]) {
                    if (key == GLFW.GLFW_KEY_ESCAPE) {
                        capturedKey = lastkey;
                    } else {
                        capturedKey = Keyboard.translateKeyCode(key);
                    }
                    break;
                }
                keyWasPressed[key] = isPressed;
            }
            
            // Small sleep to prevent CPU spinning
            try {
                Thread.sleep(8);
            } catch (InterruptedException e) {
                capturedKey = lastkey;
                break;
            }
        }

        // Ensure window closes properly
        GLFW.glfwSetWindowShouldClose(window, true);
        GLFW.glfwSwapBuffers(window);
        
        // Give window a chance to close
        for (int i = 0; i < 5; i++) {
            GLFW.glfwPollEvents();
            try { Thread.sleep(16); } catch (Exception e) {}
        }
        
        trueTypeFont.destroy();
        GLFW.glfwSetKeyCallback(window, null);
        GLFW.glfwHideWindow(window);
        
        // Poll a few more times after hide
        for (int i = 0; i < 5; i++) {
            GLFW.glfwPollEvents();
            try { Thread.sleep(8); } catch (Exception e) {}
        }
        
        GLFW.glfwDestroyWindow(window);
        
        // Final poll to ensure cleanup
        GLFW.glfwPollEvents();
        
        // Don't terminate GLFW - main app may still need it
        
        return capturedKey >= 0 ? capturedKey : lastkey;
    }

    private int getGlfwKeyFromName(String keyName) {
        if (keyName == null || keyName.isEmpty()) return GLFW.GLFW_KEY_UNKNOWN;
        // Handle common key names
        return switch (keyName) {
            case "Space" -> GLFW.GLFW_KEY_SPACE;
            case "A" -> GLFW.GLFW_KEY_A;
            case "B" -> GLFW.GLFW_KEY_B;
            case "C" -> GLFW.GLFW_KEY_C;
            case "D" -> GLFW.GLFW_KEY_D;
            case "E" -> GLFW.GLFW_KEY_E;
            case "F" -> GLFW.GLFW_KEY_F;
            case "G" -> GLFW.GLFW_KEY_G;
            case "H" -> GLFW.GLFW_KEY_H;
            case "I" -> GLFW.GLFW_KEY_I;
            case "J" -> GLFW.GLFW_KEY_J;
            case "K" -> GLFW.GLFW_KEY_K;
            case "L" -> GLFW.GLFW_KEY_L;
            case "M" -> GLFW.GLFW_KEY_M;
            case "N" -> GLFW.GLFW_KEY_N;
            case "O" -> GLFW.GLFW_KEY_O;
            case "P" -> GLFW.GLFW_KEY_P;
            case "Q" -> GLFW.GLFW_KEY_Q;
            case "R" -> GLFW.GLFW_KEY_R;
            case "S" -> GLFW.GLFW_KEY_S;
            case "T" -> GLFW.GLFW_KEY_T;
            case "U" -> GLFW.GLFW_KEY_U;
            case "V" -> GLFW.GLFW_KEY_V;
            case "W" -> GLFW.GLFW_KEY_W;
            case "X" -> GLFW.GLFW_KEY_X;
            case "Y" -> GLFW.GLFW_KEY_Y;
            case "Z" -> GLFW.GLFW_KEY_Z;
            case "0" -> GLFW.GLFW_KEY_0;
            case "1" -> GLFW.GLFW_KEY_1;
            case "2" -> GLFW.GLFW_KEY_2;
            case "3" -> GLFW.GLFW_KEY_3;
            case "4" -> GLFW.GLFW_KEY_4;
            case "5" -> GLFW.GLFW_KEY_5;
            case "6" -> GLFW.GLFW_KEY_6;
            case "7" -> GLFW.GLFW_KEY_7;
            case "8" -> GLFW.GLFW_KEY_8;
            case "9" -> GLFW.GLFW_KEY_9;
            case "Return", "Enter" -> GLFW.GLFW_KEY_ENTER;
            case "Escape" -> GLFW.GLFW_KEY_ESCAPE;
            case "Back" -> GLFW.GLFW_KEY_BACKSPACE;
            case "Tab" -> GLFW.GLFW_KEY_TAB;
            case "LShift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RShift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "LControl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "RControl" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "LMenu", "LAlt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "RMenu", "RAlt" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "Up" -> GLFW.GLFW_KEY_UP;
            case "Down" -> GLFW.GLFW_KEY_DOWN;
            case "Left" -> GLFW.GLFW_KEY_LEFT;
            case "Right" -> GLFW.GLFW_KEY_RIGHT;
            default -> {
                if (keyName.startsWith("Key")) {
                    try {
                        yield Integer.parseInt(keyName.substring(3));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                yield GLFW.GLFW_KEY_UNKNOWN;
            }
        };
    }
}
