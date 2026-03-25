package org.open2jam.gui.parts;

import org.open2jam.Config;
import org.open2jam.GameOptions;

import javax.swing.*;
import javax.swing.GroupLayout;
import java.awt.*;

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

    public AdvancedOptions() {
        go = Config.getInstance().getGameOptions().toGameOptions();

        hasteModeCheckbox = new JCheckBox("Haste Mode");
        bufferSize = new JTextField(String.valueOf(go.getBufferSize()), 10);
        jLabel1 = new JLabel("Buffer Size");
        normalizeSpeedCheckbox = new JCheckBox("Normalize Speed");

        // Set initial values from GameOptions
        hasteModeCheckbox.setSelected(go.isHasteMode());
        normalizeSpeedCheckbox.setSelected(go.isHasteModeNormalizeSpeed());
        normalizeSpeedCheckbox.setEnabled(go.isHasteMode());

        // Add listeners to update GameOptions
        hasteModeCheckbox.addActionListener(e -> {
            go.setHasteMode(hasteModeCheckbox.isSelected());
            normalizeSpeedCheckbox.setEnabled(hasteModeCheckbox.isSelected());
        });

        normalizeSpeedCheckbox.addActionListener(e -> {
            go.setHasteModeNormalizeSpeed(normalizeSpeedCheckbox.isSelected());
        });

        bufferSize.addActionListener(e -> {
            try {
                int size = Integer.parseInt(bufferSize.getText());
                if (size > 0 && size <= 4096) {
                    go.setBufferSize(size);
                }
            } catch (NumberFormatException ex) {
                // Invalid input, ignore
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
}
