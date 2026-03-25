package org.open2jam.gui.parts;

import org.open2jam.Config;
import org.open2jam.GameOptions;
import org.open2jam.util.DebugLogger;

import javax.swing.*;
import javax.swing.GroupLayout;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * Advanced options panel for game settings.
 * Refactored to use standard Swing without beansbinding.
 *
 * @author Thai Pangsakulyanont
 */
public class AdvancedOptions extends JPanel {

    private final JCheckBox hasteModeCheckbox;
    private final JTextField bufferSize;
    private final JLabel jLabel1;
    private final JCheckBox normalizeSpeedCheckbox;
    private final GameOptions go;
    private final Config config;

    public AdvancedOptions() {
        config = Config.getInstance();
        go = config.getGameOptions().toGameOptions();

        hasteModeCheckbox = new JCheckBox("Haste Mode");
        bufferSize = new JTextField(String.valueOf(go.getBufferSize()), 10);
        jLabel1 = new JLabel("Buffer Size");
        normalizeSpeedCheckbox = new JCheckBox("Normalize Speed");

        // Set initial values from GameOptions
        hasteModeCheckbox.setSelected(go.isHasteMode());
        normalizeSpeedCheckbox.setSelected(go.isHasteModeNormalizeSpeed());
        normalizeSpeedCheckbox.setEnabled(go.isHasteMode());

        // Add listeners to update GameOptions and persist to Config
        hasteModeCheckbox.addActionListener(e -> {
            go.setHasteMode(hasteModeCheckbox.isSelected());
            normalizeSpeedCheckbox.setEnabled(hasteModeCheckbox.isSelected());
            saveSettings();
        });

        normalizeSpeedCheckbox.addActionListener(e -> {
            go.setHasteModeNormalizeSpeed(normalizeSpeedCheckbox.isSelected());
            saveSettings();
        });

        // Save buffer size on focus lost (when user clicks away)
        bufferSize.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                saveBufferSizeFromText();
            }
        });

        // Also save on Enter key press (original behavior)
        bufferSize.addActionListener(e -> {
            if (saveBufferSizeFromText()) {
                // Transfer focus after successful save on Enter
                bufferSize.transferFocus();
            }
        });

        // Layout
        GroupLayout layout = new GroupLayout(this);
        this.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
            layout.createSequentialGroup()
                .addGap(47)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(hasteModeCheckbox)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(normalizeSpeedCheckbox))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(bufferSize, GroupLayout.PREFERRED_SIZE, 56, GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel1)))
        );

        layout.setVerticalGroup(
            layout.createSequentialGroup()
                .addGap(29)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(hasteModeCheckbox)
                    .addComponent(normalizeSpeedCheckbox))
                .addGap(26)
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(bufferSize, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addGap(296)
        );
    }

    /**
     * Persist settings from local GameOptions copy to Config.
     * Updates Config.GameOptionsWrapper and triggers debounced save.
     */
    private void saveSettings() {
        // Update Config's GameOptionsWrapper with current values
        Config.GameOptionsWrapper wrapper = config.getGameOptions();
        wrapper.hasteMode = go.isHasteMode();
        wrapper.hasteModeNormalizeSpeed = go.isHasteModeNormalizeSpeed();
        wrapper.bufferSize = go.getBufferSize();
        
        // Trigger debounced save to persist to disk
        config.scheduleSave();
        
        DebugLogger.debug("AdvancedOptions saved: hasteMode=" + go.isHasteMode() 
            + ", normalizeSpeed=" + go.isHasteModeNormalizeSpeed() 
            + ", bufferSize=" + go.getBufferSize());
    }

    /**
     * Parse and save buffer size from text field.
     * @return true if successful, false if invalid input
     */
    private boolean saveBufferSizeFromText() {
        try {
            int size = Integer.parseInt(bufferSize.getText().trim());
            if (size > 0 && size <= 4096) {
                go.setBufferSize(size);
                saveSettings();
                return true;
            } else {
                showInvalidInputMessage();
                bufferSize.setText(String.valueOf(go.getBufferSize()));
                return false;
            }
        } catch (NumberFormatException ex) {
            showInvalidInputMessage();
            bufferSize.setText(String.valueOf(go.getBufferSize()));
            return false;
        }
    }

    /**
     * Show error message for invalid buffer size input.
     */
    private void showInvalidInputMessage() {
        JOptionPane.showMessageDialog(
            this,
            "Buffer size must be between 1 and 4096.",
            "Invalid Input",
            JOptionPane.ERROR_MESSAGE
        );
    }
}
