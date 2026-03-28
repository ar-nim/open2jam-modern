package org.open2jam.gui.parts;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FlowLayout;
import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import org.open2jam.render.lwjgl.Keyboard;
import org.open2jam.Config;
import org.open2jam.Main;
import org.open2jam.GameOptions;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.open2jam.parsers.Event;
import org.open2jam.render.DisplayMode;
import org.open2jam.render.lwjgl.LWJGLGameWindow;
import org.open2jam.render.lwjgl.TrueTypeFont;
import org.open2jam.util.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Configuration panel for keyboard mappings.
 * Refactored to use standard Swing without beansbinding.
 *
 * @author CdK
 */
public class Configuration extends JPanel {

    private EnumMap<Event.Channel, Integer> kb_map;
    private HashMap<Integer, Event.Channel> table_map = new HashMap<>();

    // Display configuration fields
    private List<DisplayMode> displayModes;
    private JComboBox<DisplayMode> combo_displays;
    private JCheckBox jc_full_screen;
    private JCheckBox jc_vsync;
    private JComboBox<GameOptions.FpsLimiter> combo_fpsLimiter;
    private JCheckBox jc_custom_size;
    private JTextField txt_res_width;
    private JTextField txt_res_height;
    private JLabel lbl_res_x;

    // GUI configuration fields
    private JComboBox<GameOptions.UiTheme> combo_uiTheme;
    private JPanel panel_gui;

    private final JButton bSave;
    private final JPanel panel_keys;
    private final JPanel panel_display;
    private final JLabel jLabel1;
    private final JComboBox<String> combo_keyboardConfig;
    private final JScrollPane tKeys_scroll;
    private final JTable tKeys;
    private JLabel lbl_saveFeedback;
    private javax.swing.Timer saveFeedbackTimer;

    public Configuration() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Initialize display modes
        LWJGLGameWindow gameWindow = new LWJGLGameWindow();
        displayModes = gameWindow.getAvailableDisplayModes();

        bSave = new JButton("Save");
        bSave.setFont(new Font("SansSerif", Font.BOLD, 11));
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

        // Create display configuration panel
        panel_display = createDisplayPanel();

        // Create GUI configuration panel
        panel_gui = createGUIPanel();

        // Create bottom panel with display and GUI
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS)); // Stack display and GUI
        bottomPanel.setAlignmentX(CENTER_ALIGNMENT);
        bottomPanel.add(panel_display);
        bottomPanel.add(Box.createVerticalStrut(10));
        bottomPanel.add(panel_gui);

        add(panel_keys);
        add(Box.createVerticalStrut(10));
        add(bottomPanel);
        add(Box.createVerticalStrut(10));
        
        // Center the save button
        JPanel savePanel = new JPanel();
        savePanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        savePanel.setAlignmentX(CENTER_ALIGNMENT);
        savePanel.add(bSave);
        add(savePanel);
        
        // Save feedback label (initially hidden)
        lbl_saveFeedback = new JLabel("Settings saved successfully!", SwingConstants.CENTER);
        lbl_saveFeedback.setAlignmentX(CENTER_ALIGNMENT);
        lbl_saveFeedback.setForeground(new java.awt.Color(0, 128, 0));  // Green color
        lbl_saveFeedback.setVisible(false);
        add(lbl_saveFeedback);
        add(Box.createVerticalStrut(10));  // Padding below feedback label

        // Initialize save feedback timer
        saveFeedbackTimer = new javax.swing.Timer(3000, e -> lbl_saveFeedback.setVisible(false));
        saveFeedbackTimer.setRepeats(false);

        loadTableKeys(Config.KeyboardType.K7);

        // Load display settings
        loadDisplaySettings();
        
        // Load GUI settings
        loadGUISettings();
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
        // Save keyboard map
        Config config = Config.getInstance();
        for (Map.Entry<Event.Channel, Integer> entry : kb_map.entrySet()) {
            config.setKeyCode(kt, entry.getKey(), entry.getValue());
        }

        GameOptions op = config.getGameOptions().toGameOptions();

        // Save display settings
        saveDisplaySettings(op);
        
        // Save GUI settings
        saveGUISettings(op);

        config.setGameOptions(Config.GameOptionsWrapper.fromGameOptions(op));

        // Apply theme immediately without restart
        FlatAnimatedLafChange.showSnapshot();
        Main.initFlatLaf();
        SwingUtilities.updateComponentTreeUI(getTopLevelAncestor());
        FlatAnimatedLafChange.hideSnapshotWithAnimation();

        // Show feedback label instead of popup
        lbl_saveFeedback.setVisible(true);
        
        // Auto-hide after 3 seconds
        if (saveFeedbackTimer.isRunning()) {
            saveFeedbackTimer.stop();
        }
        saveFeedbackTimer.start();
    }

    private void tKeysMouseClicked(java.awt.event.MouseEvent evt) {
        final int row = tKeys.getSelectedRow();
        if (row < 0) return;
        if (tKeys.getValueAt(row, 0) == null) return;
        // Allow clicking on empty cells to bind keys
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
        // Allow empty values - user can bind a key to this channel
        int lastkey = -1;  // Default to -1 if cell is empty
        if (currentValue != null && !currentValue.isEmpty()) {
            lastkey = Keyboard.translateKeyCode(getGlfwKeyFromName(currentValue));
        }
        int code;
        try {
            code = read_keyboard_key(lastkey);
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to read keyboard key", e);
            return;
        }

        Event.Channel c = table_map.get(row);
        if (c == null) return;

        // Check if user pressed ESC to unbind (read_keyboard_key returns -1 for ESC)
        if (code == -1) {
            // Unbind this channel
            kb_map.remove(c);
            SwingUtilities.invokeLater(() -> {
                tKeys.setValueAt("", row, 1);
            });
            return;
        }
        
        // Check if this key is already bound to another channel
        if (kb_map.containsValue(code)) {
            // Find which channel currently uses this key and unbind it
            Event.Channel existingChannel = null;
            for (Map.Entry<Event.Channel, Integer> entry : kb_map.entrySet()) {
                if (entry.getValue().equals(code)) {
                    existingChannel = entry.getKey();
                    break;
                }
            }
            // Unbind from the existing channel (set to -1 or remove)
            if (existingChannel != null && !existingChannel.equals(c)) {
                kb_map.remove(existingChannel);
                // Update the table UI for the unbound channel
                for (int i = 0; i < tKeys.getRowCount(); i++) {
                    if (table_map.get(i).equals(existingChannel)) {
                        int finalI = i;
                        SwingUtilities.invokeLater(() -> {
                            tKeys.setValueAt("", finalI, 1);
                        });
                        break;
                    }
                }
            }
        }
        
        // Bind the key to the new channel
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

    private void loadTableKeys(Config.KeyboardType kt) {
        // Get key codes as primitive array and convert to map for display
        int[] keyCodes = Config.getInstance().getKeyCodes(kt);
        kb_map = new java.util.EnumMap<>(Event.Channel.class);
        for (Event.Channel channel : Event.Channel.values()) {
            if (keyCodes[channel.ordinal()] != -1) {
                kb_map.put(channel, keyCodes[channel.ordinal()]);
            }
        }
        
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

    /**
     * Create the display configuration panel with all components.
     */
    private JPanel createDisplayPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Display Configuration"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        // Display mode dropdown
        JPanel displayPanel = new JPanel();
        displayPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        displayPanel.setAlignmentX(LEFT_ALIGNMENT);
        
        JLabel displayLabel = new JLabel("Display:");
        combo_displays = new JComboBox<>();
        for (DisplayMode mode : displayModes) {
            combo_displays.addItem(mode);
        }
        displayPanel.add(displayLabel);
        displayPanel.add(combo_displays);
        
        panel.add(displayPanel);
        panel.add(Box.createVerticalStrut(5));

        // Custom size checkbox and inputs
        JPanel customSizePanel = new JPanel();
        customSizePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        customSizePanel.setAlignmentX(LEFT_ALIGNMENT);
        
        jc_custom_size = new JCheckBox("Custom size:");
        jc_custom_size.addActionListener(e -> {
            boolean enabled = jc_custom_size.isSelected();
            txt_res_width.setEnabled(enabled);
            txt_res_height.setEnabled(enabled);
        });
        
        txt_res_width = new JTextField("800", 6);
        txt_res_width.setEnabled(false);
        
        lbl_res_x = new JLabel("x");
        
        txt_res_height = new JTextField("600", 6);
        txt_res_height.setEnabled(false);
        
        customSizePanel.add(jc_custom_size);
        customSizePanel.add(txt_res_width);
        customSizePanel.add(lbl_res_x);
        customSizePanel.add(txt_res_height);
        
        panel.add(customSizePanel);
        panel.add(Box.createVerticalStrut(5));

        // Fullscreen and VSync checkboxes
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.setAlignmentX(LEFT_ALIGNMENT);
        
        jc_full_screen = new JCheckBox("Full screen");
        jc_vsync = new JCheckBox("Use VSync");
        jc_vsync.setSelected(true);
        
        // FPS Limiter dropdown
        JLabel fpsLimiterLabel = new JLabel("FPS Limiter:");
        combo_fpsLimiter = new JComboBox<>(GameOptions.FpsLimiter.values());
        combo_fpsLimiter.setSelectedItem(GameOptions.FpsLimiter.X1);
        updateFpsLimiterEnabled();  // Set initial enabled state
        
        // Add listener to update FPS limiter enabled state when VSync changes
        jc_vsync.addItemListener(e -> updateFpsLimiterEnabled());

        optionsPanel.add(jc_full_screen);
        optionsPanel.add(jc_vsync);
        optionsPanel.add(fpsLimiterLabel);
        optionsPanel.add(combo_fpsLimiter);
        
        panel.add(optionsPanel);
        panel.add(Box.createVerticalStrut(5));

        return panel;
    }

    /**
     * Create the GUI configuration panel.
     */
    private JPanel createGUIPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "GUI Settings"));
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        // UI Theme
        panel.add(new JLabel("Theme:"));
        combo_uiTheme = new JComboBox<>(GameOptions.UiTheme.values());
        panel.add(combo_uiTheme);

        return panel;
    }

    private void loadGUISettings() {
        GameOptions go = Config.getInstance().getGameOptions().toGameOptions();
        
        // Set UI Theme
        combo_uiTheme.setSelectedItem(go.getUiTheme());
    }

    private void saveGUISettings(GameOptions go) {
        Object themeObj = combo_uiTheme.getSelectedItem();
        if (themeObj instanceof GameOptions.UiTheme) {
            go.setUiTheme((GameOptions.UiTheme) themeObj);
        }
    }

    /**
     * Load display settings from GameOptions into the UI components.
     */
    private void loadDisplaySettings() {
        GameOptions go = Config.getInstance().getGameOptions().toGameOptions();

        // Select the matching display mode
        DisplayMode currentMode = go.getDisplay();
        for (int i = 0; i < combo_displays.getItemCount(); i++) {
            DisplayMode mode = combo_displays.getItemAt(i);
            if (go.isDisplaySaved(mode)) {
                combo_displays.setSelectedIndex(i);
                break;
            }
        }

        // Set fullscreen and vsync
        jc_full_screen.setSelected(go.isDisplayFullscreen());
        jc_vsync.setSelected(go.isDisplayVsync());
        
        // Set FPS limiter
        combo_fpsLimiter.setSelectedItem(go.getFpsLimiter());
        updateFpsLimiterEnabled();

        // Set custom size if applicable
        boolean hasCustomSize = false;
        for (DisplayMode mode : displayModes) {
            if (mode.getWidth() == currentMode.getWidth() &&
                mode.getHeight() == currentMode.getHeight()) {
                hasCustomSize = true;
                break;
            }
        }

        if (!hasCustomSize && currentMode.getWidth() > 0 && currentMode.getHeight() > 0) {
            jc_custom_size.setSelected(true);
            txt_res_width.setText(String.valueOf(currentMode.getWidth()));
            txt_res_height.setText(String.valueOf(currentMode.getHeight()));
            txt_res_width.setEnabled(true);
            txt_res_height.setEnabled(true);
        }
    }

    /**
     * Update the enabled state of the FPS limiter dropdown.
     * Greyed out when VSync is enabled.
     */
    private void updateFpsLimiterEnabled() {
        boolean vsyncEnabled = jc_vsync.isSelected();
        combo_fpsLimiter.setEnabled(!vsyncEnabled);
        combo_fpsLimiter.setToolTipText(vsyncEnabled ? 
            "Disabled when VSync is enabled" : 
            "Limit frame rate to multiple of refresh rate");
    }

    /**
     * Save display settings from UI components to GameOptions.
     */
    private void saveDisplaySettings(GameOptions go) {
        DisplayMode selectedMode = (DisplayMode) combo_displays.getSelectedItem();

        // Check if custom size is enabled
        if (jc_custom_size.isSelected()) {
            try {
                int width = Integer.parseInt(txt_res_width.getText().trim());
                int height = Integer.parseInt(txt_res_height.getText().trim());
                if (width > 0 && height > 0) {
                    // Create custom display mode
                    selectedMode = new DisplayMode(width, height, 32, 60);
                }
            } catch (NumberFormatException e) {
                // Invalid input, use selected mode from dropdown
            }
        }

        go.setDisplay(selectedMode);
        go.setDisplayFullscreen(jc_full_screen.isSelected());
        go.setDisplayVsync(jc_vsync.isSelected());
        go.setFpsLimiter((GameOptions.FpsLimiter) combo_fpsLimiter.getSelectedItem());
    }

    private static Font font = new Font("SansSerif", Font.BOLD, 14);

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
        Integer capturedKey = null;  // null = waiting for input, -1 = ESC, >=0 = key code

        // Show and focus window
        GLFW.glfwShowWindow(window);
        GLFW.glfwFocusWindow(window);

        // Wait for key press using polling with edge detection
        long startTime = System.currentTimeMillis();
        while (capturedKey == null) {
            // Timeout after 10 seconds - keep existing binding
            if (System.currentTimeMillis() - startTime > 10000) {
                capturedKey = lastkey;  // Keep original key (or -1 if was empty)
                break;
            }

            // Clear and draw
            GL11.glClearColor(0.15f, 0.15f, 0.2f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glLoadIdentity();

            trueTypeFont.drawString(20, 20, "Press a KEY", 1, -1);
            trueTypeFont.drawString(20, 50, "for: " + place, 1, -1);
            trueTypeFont.drawString(20, 80, "ESC to unbind", 1, -1);

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();

            // Check for NEW key press (edge detection)
            for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
                boolean isPressed = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;

                // Detect key press edge (was not pressed, now is pressed)
                if (isPressed && !keyWasPressed[key]) {
                    if (key == GLFW.GLFW_KEY_ESCAPE) {
                        capturedKey = -1;  // Special value to indicate unbind
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
                capturedKey = lastkey;  // Keep original key on interrupt
                break;
            }
        }

        // Ensure capturedKey has a value
        if (capturedKey == null) {
            capturedKey = lastkey;
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

        // Return captured key: -1 for ESC (unbind), or the key code
        // On timeout/interrupt, return lastkey (original binding)
        return capturedKey;
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
