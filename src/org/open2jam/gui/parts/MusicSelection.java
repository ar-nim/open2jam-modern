package org.open2jam.gui.parts;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.open2jam.AppContext;
import org.open2jam.Config;
import org.open2jam.GameOptions;
import org.open2jam.GameOptions.ChannelMod;
import org.open2jam.GameOptions.SpeedType;
import org.open2jam.GameOptions.VisibilityMod;
import org.open2jam.game.judgment.BeatJudgment;
import org.open2jam.game.judgment.TimeJudgment;
import org.open2jam.gui.ChartListTableModel;
import org.open2jam.gui.ChartModelLoader;
import org.open2jam.gui.ChartTableModel;
import org.open2jam.parsers.Chart;
import org.open2jam.parsers.ChartList;
import org.open2jam.render.DisplayMode;
import org.open2jam.render.Render;
import org.open2jam.sound.SoundSystemException;
import org.open2jam.util.DebugLogger;
import org.open2jam.util.Logger;

import com.github.dtinth.partytime.server.Server;
import com.github.dtinth.partytime.server.ServerUI;

@SuppressWarnings({"unchecked", "rawtypes"})
public class MusicSelection extends javax.swing.JPanel
    implements PropertyChangeListener, ListSelectionListener {
    private final AppContext context;  // NEW: Store AppContext
    private Server lastServer;

    private class PopupListener extends MouseAdapter {

	private final JPopupMenu menu;
	
	public PopupListener(JPopupMenu menu) {
	    this.menu = menu;
	}

	@Override
	public void mousePressed(MouseEvent e) {
	    showPopup(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	    showPopup(e);
	}
	
	private void showPopup(MouseEvent e) {
	    if(e.isPopupTrigger()) {
		Component c = e.getComponent();
		if(c instanceof ListSelectionListener) {
		    JTable t = (JTable) c;
		    int row = t.rowAtPoint(e.getPoint());
		    t.getSelectionModel().setSelectionInterval(row, row);
		}
		
		if(menu == null) return;
		
		menu.show(e.getComponent(), e.getX(), e.getY());
	    }
	}
    }

    private ChartListTableModel model_songlist;
    private ChartTableModel model_chartlist;
    //private File cwd;
    private int rank = 0;
    private ChartList selected_chart;
    private Chart selected_header;
    private int last_model_idx;
    private final TableRowSorter<ChartListTableModel> table_sorter;
    
    // Track currently selected directory (not from SQLite)
    private File currentDirectory = null;
    
    // Prevent recursive dropdown selection changes
    private boolean isRefreshingDropdown = false;
    
    private class FileItem {
        File file;
        
        public FileItem(File f) {
            this.file = f;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 59 * hash + (this.file != null ? this.file.hashCode() : 0);
            return hash;
        }
        
        @Override
        public boolean equals(Object o) {
            return (o instanceof FileItem) && 
                    ((FileItem)o).file.equals(file);
        }
        
        @Override
        public String toString() {
            return file.getName();
        }
    }

    /** Creates new form Interface */
    public MusicSelection(AppContext context) {  // UPDATED: Accept AppContext
        this.context = context;
        initLogic();
        initComponents();
        load_progress.setVisible(false);

        // Load libraries from SQLite
        try {
            List<org.open2jam.parsers.Library> libs = org.open2jam.parsers.ChartCacheSQLite.getAllLibraries();
            org.open2jam.parsers.Library lastOpenedLib = null;
            Integer lastLibId = context.config.getLastOpenedLibraryId();  // Use context

            for (org.open2jam.parsers.Library lib : libs) {
                combo_dirs.addItem(new FileItem(new File(lib.rootPath)));

                // Find last opened library
                if (lastLibId != null && lib.id == lastLibId) {
                    lastOpenedLib = lib;
                }
            }
            
            // Set flag to prevent dropdown listener from triggering during initialization
            isRefreshingDropdown = true;
            
            // Restore last opened library or use first one
            if (lastOpenedLib != null) {
                FileItem lastItem = new FileItem(new File(lastOpenedLib.rootPath));
                combo_dirs.setSelectedItem(lastItem);
                setCurrentDirectory(new File(lastOpenedLib.rootPath));
                loadDir(new File(lastOpenedLib.rootPath));
            } else if (!libs.isEmpty()) {
                // No last opened or invalid - use first library
                File firstDir = new File(libs.get(0).rootPath);
                setCurrentDirectory(firstDir);
                loadDir(firstDir);
            } else {
                // No libraries configured - open file chooser
                openFileChooser();
            }
            
            // Clear flag after initialization
            isRefreshingDropdown = false;
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to load libraries", e);
            openFileChooser();
        }

        table_sorter = new TableRowSorter<ChartListTableModel>(model_songlist);
        table_songlist.setRowSorter(table_sorter);
        txt_filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {updateFilter();}
            @Override
            public void removeUpdate(DocumentEvent e) {updateFilter();}
            @Override
            public void changedUpdate(DocumentEvent e) {}
        });

        javax.swing.table.TableColumn col;
        col = table_songlist.getColumnModel().getColumn(0);
        col.setPreferredWidth(180);
        col = table_songlist.getColumnModel().getColumn(1);
        col.setPreferredWidth(30);
        col = table_songlist.getColumnModel().getColumn(2);
        col.setPreferredWidth(80);

        table_chartlist.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e)
            {
                if (e.getValueIsAdjusting()) return;
                javax.swing.ListSelectionModel lsm = (javax.swing.ListSelectionModel)e.getSource();
                if(lsm.isSelectionEmpty()) return;
                int selectedRow = lsm.getMinSelectionIndex();
                if(selectedRow < 0) return;
                if(selected_chart.get(selectedRow) == selected_header)return;
                selected_header = selected_chart.get(selectedRow);

                updateInfo();
                updateRankFromChartSelection();
            }
        });

        readGameOptions();

        btn_autoplay_keys.setEnabled(jc_autoplay.isSelected());

        // Attach auto-save listeners to all modifier controls
        attachAutoSaveListeners();

        // Add "Refresh Library" button to popup menu
        JPopupMenu popMenu = new JPopupMenu();
        JMenuItem refreshItem = new JMenuItem("Refresh Library");
        refreshItem.addActionListener(e -> {
            File currentDir = getCurrentDirectory();
            if (currentDir != null) {
                refreshLibrary(currentDir);
            }
        });
        popMenu.add(refreshItem);

        // Keysound Extractor - Extract audio files from OJN
        JMenuItem extractKeysoundItem = new JMenuItem("Extract Keysounds");
        extractKeysoundItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                try {
                    // Create output directory: extraction/[filename_without_ojn]/
                    String baseName = selected_header.getSource().getName();
                    int dotIndex = baseName.lastIndexOf(".");
                    if (dotIndex > 0) {
                        baseName = baseName.substring(0, dotIndex);
                    }
                    File outputDir = new File("extraction/" + baseName);
                    outputDir.mkdirs();
                    
                    // Extract keysounds
                    selected_header.copySampleFiles(outputDir);
                    
                    JOptionPane.showMessageDialog(null, 
                        "Keysounds extracted to: " + outputDir.getAbsolutePath(),
                        "Extraction Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null, 
                        "Failed to extract keysounds: " + ex.getMessage(), 
                        "Extraction Error", 
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    System.gc();
                }
            }
        });
        popMenu.add(extractKeysoundItem);

	//table_chartlist.addMouseListener(new PopupListener(popMenu));
	table_songlist.addMouseListener(new PopupListener(popMenu));
    }
    
    private void readGameOptions() {
        // TODO: read Config gameOptions and set them on the GUI
        GameOptions go = context.config.getGameOptions().toGameOptions();

        jc_autoplay.setSelected(go.isAutoplay());
	jc_autosound.setSelected(go.isAutosound());
        combo_channelModifier.setSelectedItem(go.getChannelModifier());
        combo_visibilityModifier.setSelectedItem(go.getVisibilityModifier());
        slider_main_vol.setValue(Math.round(go.getMasterVolume()*100));
        slider_key_vol.setValue(Math.round(go.getKeyVolume()*100));
        slider_bgm_vol.setValue(Math.round(go.getBGMVolume()*100));
        js_hispeed.setValue(go.getSpeedMultiplier());
        combo_speedType.setSelectedItem(go.getSpeedType());
        txt_displayLag.setText(go.getDisplayLag() + "");
        txt_audioLatency.setText(go.getAudioLatency() + "");
        jc_timed_judgment.setSelected(go.getJudgmentType() == GameOptions.JudgmentType.TIME_JUDGMENT);

    }

    /**
     * Attach auto-save listeners to all modifier controls.
     * Changes are saved immediately with debounced disk writes.
     */
    private void attachAutoSaveListeners() {
        // Volume sliders - save on state released (when user stops dragging)
        javax.swing.event.ChangeListener sliderListener = e -> {
            if (!slider_main_vol.getValueIsAdjusting() && 
                !slider_key_vol.getValueIsAdjusting() && 
                !slider_bgm_vol.getValueIsAdjusting()) {
                saveVolumeSettings();
            }
        };
        slider_main_vol.addChangeListener(sliderListener);
        slider_key_vol.addChangeListener(sliderListener);
        slider_bgm_vol.addChangeListener(sliderListener);

        // Checkboxes - save on state change
        java.awt.event.ItemListener checkboxListener = e -> saveModifierSettings();
        jc_autoplay.addItemListener(checkboxListener);
        jc_autosound.addItemListener(checkboxListener);
        jc_timed_judgment.addItemListener(checkboxListener);

        // ComboBoxes - save on selection change
        java.awt.event.ItemListener comboListener = e -> {
            if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                saveModifierSettings();
            }
        };
        combo_channelModifier.addItemListener(comboListener);
        combo_visibilityModifier.addItemListener(comboListener);
        combo_speedType.addItemListener(comboListener);

        // HiSpeed spinner - save on value change
        javax.swing.event.ChangeListener spinnerListener = e -> saveModifierSettings();
        js_hispeed.addChangeListener(spinnerListener);

        // Text fields (displayLag, audioLatency) - save on focus lost with validation
        java.awt.event.FocusListener textFieldFocusListener = new java.awt.event.FocusListener() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveDisplaySettings();
            }
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                // Do nothing
            }
        };
        txt_displayLag.addFocusListener(textFieldFocusListener);
        txt_audioLatency.addFocusListener(textFieldFocusListener);
    }

    /**
     * Save volume settings from sliders to config.
     */
    private void saveVolumeSettings() {
        GameOptions go = context.config.getGameOptions().toGameOptions();
        go.setMasterVolume(slider_main_vol.getValue() / 100f);
        go.setKeyVolume(slider_key_vol.getValue() / 100f);
        go.setBGMVolume(slider_bgm_vol.getValue() / 100f);
        context.config.setGameOptions(Config.GameOptionsWrapper.fromGameOptions(go));
        DebugLogger.debug("Auto-saved volume settings: main=" + slider_main_vol.getValue() + 
                         ", key=" + slider_key_vol.getValue() + 
                         ", bgm=" + slider_bgm_vol.getValue());
    }

    /**
     * Save modifier settings (autoplay, autosound, channel/visibility mods, speed, judgment) to config.
     */
    private void saveModifierSettings() {
        GameOptions go = context.config.getGameOptions().toGameOptions();
        go.setAutoplay(jc_autoplay.isSelected());
        go.setAutosound(jc_autosound.isSelected());
        go.setChannelModifier((ChannelMod) combo_channelModifier.getSelectedItem());
        go.setVisibilityModifier((VisibilityMod) combo_visibilityModifier.getSelectedItem());
        go.setSpeedMultiplier((Double) js_hispeed.getValue());
        go.setSpeedType((SpeedType) combo_speedType.getSelectedItem());
        go.setJudgmentType(jc_timed_judgment.isSelected() ? GameOptions.JudgmentType.TIME_JUDGMENT : GameOptions.JudgmentType.BEAT_JUDGMENT);

        // Update autoplay keys button state
        btn_autoplay_keys.setEnabled(jc_autoplay.isSelected());
        
        context.config.setGameOptions(Config.GameOptionsWrapper.fromGameOptions(go));
        DebugLogger.debug("Auto-saved modifier settings: autoplay=" + jc_autoplay.isSelected() + 
                         ", autosound=" + jc_autosound.isSelected() +
                         ", channelMod=" + combo_channelModifier.getSelectedItem() +
                         ", visibilityMod=" + combo_visibilityModifier.getSelectedItem() +
                         ", speed=" + js_hispeed.getValue() +
                         ", speedType=" + combo_speedType.getSelectedItem());
    }

    /**
     * Save display settings (displayLag, audioLatency) from text fields to config.
     * Validates input before saving.
     */
    private void saveDisplaySettings() {
        try {
            double displayLag = Double.parseDouble(txt_displayLag.getText().trim());
            double audioLatency = Double.parseDouble(txt_audioLatency.getText().trim());
            
            GameOptions go = context.config.getGameOptions().toGameOptions();
            go.setDisplayLag(displayLag);
            go.setAudioLatency(audioLatency);
            context.config.setGameOptions(Config.GameOptionsWrapper.fromGameOptions(go));
            DebugLogger.debug("Auto-saved display settings: displayLag=" + displayLag + 
                             ", audioLatency=" + audioLatency);
        } catch (NumberFormatException e) {
            // Invalid input - show error and revert to saved value
            GameOptions go = context.config.getGameOptions().toGameOptions();
            txt_displayLag.setText(go.getDisplayLag() + "");
            txt_audioLatency.setText(go.getAudioLatency() + "");
            JOptionPane.showMessageDialog(this, 
                "Invalid number format. Please enter a valid number.",
                "Invalid Input", 
                JOptionPane.WARNING_MESSAGE);
            DebugLogger.debug("Invalid display/audio latency input, reverted to saved values");
        }
    }

    /*
     * the parent is telling us the window is closing
     * now is a good time to save the game options
     * This acts as a safety net to capture any final state
     */
    public void windowClosing() {
        // Save ALL settings as a safety net (in case any listeners didn't fire)
        GameOptions go = context.config.getGameOptions().toGameOptions();

        go.setAutoplay(jc_autoplay.isSelected());
	go.setAutosound(jc_autosound.isSelected());
        go.setChannelModifier((ChannelMod)combo_channelModifier.getSelectedItem());
        go.setVisibilityModifier((VisibilityMod)combo_visibilityModifier.getSelectedItem());
        go.setMasterVolume(slider_main_vol.getValue()/100f);
        go.setKeyVolume(slider_key_vol.getValue()/100f);
        go.setBGMVolume(slider_bgm_vol.getValue()/100f);
        go.setSpeedMultiplier((Double)js_hispeed.getValue());
        go.setSpeedType((SpeedType)combo_speedType.getSelectedItem());
        go.setJudgmentType(jc_timed_judgment.isSelected() ? GameOptions.JudgmentType.TIME_JUDGMENT : GameOptions.JudgmentType.BEAT_JUDGMENT);
        
        // Also save displayLag and audioLatency from text fields
        try {
            go.setDisplayLag(Double.parseDouble(txt_displayLag.getText().trim()));
            go.setAudioLatency(Double.parseDouble(txt_audioLatency.getText().trim()));
        } catch (NumberFormatException e) {
            // Use existing values if text fields are invalid
            DebugLogger.debug("Invalid displayLag/audioLatency on window close, using existing values");
        }

        context.config.setGameOptions(Config.GameOptionsWrapper.fromGameOptions(go));
        DebugLogger.debug("windowClosing() - Saved all game options as safety net");
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rank_group = new javax.swing.ButtonGroup();
        panel_list = new javax.swing.JPanel();
        table_scroll = new javax.swing.JScrollPane();
        table_songlist = new javax.swing.JTable();
        txt_filter = new javax.swing.JTextField();
        lbl_search = new javax.swing.JLabel();
        bt_choose_dir = new javax.swing.JButton();
        load_progress = new javax.swing.JProgressBar();
        jLabel2 = new javax.swing.JLabel();
        combo_dirs = new javax.swing.JComboBox();
        btn_reload = new javax.swing.JButton();
        btn_delete = new javax.swing.JButton();
        panel_setting = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        txt_displayLag = new javax.swing.JTextField();
        cb_autoSyncDisplay = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        txt_audioLatency = new javax.swing.JTextField();
        cb_autoSyncAudio = new javax.swing.JCheckBox();
        panel_song = new javax.swing.JPanel();
        panel_modifiers = new javax.swing.JPanel();
        lbl_main_vol = new javax.swing.JLabel();
        slider_main_vol = new javax.swing.JSlider();
        lbl_key_vol = new javax.swing.JLabel();
        slider_key_vol = new javax.swing.JSlider();
        lbl_bgm_vol = new javax.swing.JLabel();
        slider_bgm_vol = new javax.swing.JSlider();
        lbl_channelModifier = new javax.swing.JLabel();
        combo_channelModifier = new javax.swing.JComboBox();
        lbl_visibilityModifier = new javax.swing.JLabel();
        combo_visibilityModifier = new javax.swing.JComboBox();
        js_hispeed = new javax.swing.JSpinner();
        lbl_rank = new javax.swing.JLabel();
        jr_rank_easy = new javax.swing.JRadioButton();
        jr_rank_normal = new javax.swing.JRadioButton();
        jr_rank_hard = new javax.swing.JRadioButton();
        jc_autoplay = new javax.swing.JCheckBox();
        jc_timed_judgment = new javax.swing.JCheckBox();
        combo_speedType = new javax.swing.JComboBox();
        btn_autoplay_keys = new javax.swing.JButton();
        jc_autosound = new javax.swing.JCheckBox();
        panel_info = new javax.swing.JPanel();
        lbl_level1 = new javax.swing.JLabel();
        lbl_bpm1 = new javax.swing.JLabel();
        lbl_time1 = new javax.swing.JLabel();
        lbl_level = new javax.swing.JLabel();
        lbl_genre1 = new javax.swing.JLabel();
        lbl_filename = new javax.swing.JLabel();
        lbl_genre = new javax.swing.JLabel();
        lbl_artist = new javax.swing.JLabel();
        lbl_title = new javax.swing.JLabel();
        lbl_notes = new javax.swing.JLabel();
        lbl_notes1 = new javax.swing.JLabel();
        lbl_time = new javax.swing.JLabel();
        lbl_keys1 = new javax.swing.JLabel();
        lbl_bpm = new javax.swing.JLabel();
        lbl_keys = new javax.swing.JLabel();
        lbl_cover = new javax.swing.JLabel();
        table_scroll2 = new javax.swing.JScrollPane();
        table_chartlist = new javax.swing.JTable();
        bt_play = new javax.swing.JButton();
        cb_startPaused = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        txtLocalMatchingServer = new javax.swing.JTextField();
        btnCreateServer = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(900, 673));

        panel_list.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Selection List", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.TOP));

        table_songlist.setModel(model_songlist);
        table_songlist.setAutoCreateRowSorter(true);
        table_songlist.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table_songlist.getSelectionModel().addListSelectionListener(this);
        table_scroll.setViewportView(table_songlist);

        lbl_search.setText("Search:");

        bt_choose_dir.setText("Choose dir");
        bt_choose_dir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_choose_dirActionPerformed(evt);
            }
        });

        load_progress.setStringPainted(true);

        jLabel2.setText("Saved dirs:");

        combo_dirs.setMaximumSize(new java.awt.Dimension(34, 35));
        combo_dirs.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                combo_dirsItemStateChanged(evt);
            }
        });

        btn_reload.setText("Reload");
        btn_reload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_reloadActionPerformed(evt);
            }
        });

        btn_delete.setText("Remove Dir");
        btn_delete.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_deleteActionPerformed(evt);
            }
        });

        jLabel1.setText("Display Lag:");

        txt_displayLag.setText("0");
        txt_displayLag.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txt_displayLagActionPerformed(evt);
            }
        });

        cb_autoSyncDisplay.setText("autosync");
        cb_autoSyncDisplay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cb_autoSyncDisplayActionPerformed(evt);
            }
        });

        jLabel3.setText("Audio Latency:");

        txt_audioLatency.setText("0");
        txt_audioLatency.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txt_audioLatencyActionPerformed(evt);
            }
        });

        cb_autoSyncAudio.setText("autosync");
        cb_autoSyncAudio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cb_autoSyncAudioActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panel_settingLayout = new javax.swing.GroupLayout(panel_setting);
        panel_setting.setLayout(panel_settingLayout);
        panel_settingLayout.setHorizontalGroup(
            panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_settingLayout.createSequentialGroup()
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(txt_displayLag)
                    .addComponent(txt_audioLatency, javax.swing.GroupLayout.DEFAULT_SIZE, 164, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cb_autoSyncDisplay)
                    .addComponent(cb_autoSyncAudio))
                .addContainerGap(20, Short.MAX_VALUE))
        );
        panel_settingLayout.setVerticalGroup(
            panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_settingLayout.createSequentialGroup()
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel1)
                    .addComponent(txt_displayLag, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cb_autoSyncDisplay))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_settingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(jLabel3)
                    .addComponent(txt_audioLatency, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cb_autoSyncAudio))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout panel_listLayout = new javax.swing.GroupLayout(panel_list);
        panel_list.setLayout(panel_listLayout);
        panel_listLayout.setHorizontalGroup(
            panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_listLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addGroup(panel_listLayout.createSequentialGroup()
                        .addComponent(bt_choose_dir)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combo_dirs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(load_progress, javax.swing.GroupLayout.PREFERRED_SIZE, 91, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_reload, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_delete))
                    .addComponent(table_scroll))
                .addContainerGap())
            .addGroup(javax.swing.GroupLayout.Alignment.CENTER, panel_listLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(lbl_search)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txt_filter)
                .addGap(10, 10, 10))
            .addGroup(panel_listLayout.createSequentialGroup()
                .addComponent(panel_setting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        panel_listLayout.setVerticalGroup(
            panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_listLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bt_choose_dir)
                    .addComponent(jLabel2)
                    .addComponent(combo_dirs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn_reload)
                    .addComponent(btn_delete)
                    .addComponent(load_progress, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(table_scroll, javax.swing.GroupLayout.DEFAULT_SIZE, 370, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_listLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txt_filter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbl_search))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panel_setting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        panel_song.setName("");

        panel_modifiers.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Modifiers", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.TOP));
        panel_modifiers.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        lbl_main_vol.setText("Main Volume:");

        slider_main_vol.setPaintLabels(true);
        slider_main_vol.setToolTipText("Main Volume");
        slider_main_vol.setMaximumSize(new java.awt.Dimension(10, 32767));

        lbl_key_vol.setText("Key Volume:");

        slider_key_vol.setPaintLabels(true);
        slider_key_vol.setToolTipText("Key Volume");
        slider_key_vol.setValue(100);
        slider_key_vol.setMaximumSize(new java.awt.Dimension(10, 32767));

        lbl_bgm_vol.setText("BGM Volume:");

        slider_bgm_vol.setPaintLabels(true);
        slider_bgm_vol.setToolTipText("BGM Volume");
        slider_bgm_vol.setValue(100);

        lbl_channelModifier.setText("Channel Modifier:");

        combo_channelModifier.setModel(new javax.swing.DefaultComboBoxModel(GameOptions.ChannelMod.values()));

        lbl_visibilityModifier.setText("Visibility Modifier:");

        combo_visibilityModifier.setModel(new javax.swing.DefaultComboBoxModel(GameOptions.VisibilityMod.values()));

        js_hispeed.setModel(new javax.swing.SpinnerNumberModel(1.0d, 0.5d, 10.0d, 0.5d));
        js_hispeed.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

        lbl_rank.setText("Rank:");

        rank_group.add(jr_rank_easy);
        jr_rank_easy.setSelected(true);
        jr_rank_easy.setText("Easy");
        jr_rank_easy.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jr_rank_easy.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jr_rank_easy.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jr_rank_easyActionPerformed(evt);
            }
        });

        rank_group.add(jr_rank_normal);
        jr_rank_normal.setText("Normal");
        jr_rank_normal.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jr_rank_normal.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jr_rank_normal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jr_rank_normalActionPerformed(evt);
            }
        });

        rank_group.add(jr_rank_hard);
        jr_rank_hard.setText("Hard");
        jr_rank_hard.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jr_rank_hard.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jr_rank_hard.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jr_rank_hardActionPerformed(evt);
            }
        });

        jc_autoplay.setText("Autoplay");
        jc_autoplay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jc_autoplayActionPerformed(evt);
            }
        });

        jc_timed_judgment.setText("Use timed judgment");
        jc_timed_judgment.setToolTipText("Like Bemani games");

        combo_speedType.setModel(new javax.swing.DefaultComboBoxModel(GameOptions.SpeedType.values()));

        btn_autoplay_keys.setText("Keys");
        btn_autoplay_keys.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btn_autoplay_keysActionPerformed(evt);
            }
        });

        jc_autosound.setText("AutoSound");

        javax.swing.GroupLayout panel_modifiersLayout = new javax.swing.GroupLayout(panel_modifiers);
        panel_modifiers.setLayout(panel_modifiersLayout);
        panel_modifiersLayout.setHorizontalGroup(
            panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_modifiersLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                        .addComponent(lbl_channelModifier)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combo_channelModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 96, Short.MAX_VALUE)
                        .addComponent(lbl_visibilityModifier)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(combo_visibilityModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbl_bgm_vol)
                            .addComponent(lbl_key_vol)
                            .addComponent(lbl_main_vol))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(slider_key_vol, javax.swing.GroupLayout.Alignment.TRAILING, 0, 0, Short.MAX_VALUE)
                            .addComponent(slider_bgm_vol, javax.swing.GroupLayout.Alignment.TRAILING, 0, 0, Short.MAX_VALUE)
                            .addComponent(slider_main_vol, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(panel_modifiersLayout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(lbl_rank)
                                .addGap(27, 27, 27)
                                .addComponent(jr_rank_easy)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jr_rank_normal)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jr_rank_hard))
                            .addGroup(panel_modifiersLayout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(combo_speedType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(js_hispeed, javax.swing.GroupLayout.PREFERRED_SIZE, 76, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                        .addComponent(jc_timed_judgment)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jc_autosound)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jc_autoplay)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_autoplay_keys)))
                .addContainerGap())
        );
        panel_modifiersLayout.setVerticalGroup(
            panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_modifiersLayout.createSequentialGroup()
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_modifiersLayout.createSequentialGroup()
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(lbl_main_vol)
                            .addComponent(slider_main_vol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbl_rank))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(lbl_key_vol)
                            .addComponent(slider_key_vol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(lbl_bgm_vol)
                            .addComponent(slider_bgm_vol, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(js_hispeed, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(combo_speedType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(jr_rank_easy)
                    .addComponent(jr_rank_normal)
                    .addComponent(jr_rank_hard))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lbl_visibilityModifier)
                        .addComponent(combo_visibilityModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(combo_channelModifier, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(lbl_channelModifier)))
                .addGap(18, 18, 18)
                .addGroup(panel_modifiersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jc_timed_judgment)
                    .addComponent(btn_autoplay_keys)
                    .addComponent(jc_autoplay)
                    .addComponent(jc_autosound)))
        );

        panel_info.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Song Info", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.TOP));

        lbl_level1.setText("Level:");

        lbl_bpm1.setText("BPM:");

        lbl_time1.setText("Time:");

        lbl_level.setFont(lbl_level.getFont());
        lbl_level.setText("content");

        lbl_genre1.setText("Genre:");

        lbl_filename.setFont(lbl_filename.getFont().deriveFont(lbl_filename.getFont().getSize()-1f));
        lbl_filename.setText("filename");

        lbl_genre.setFont(lbl_genre.getFont());
        lbl_genre.setText("content");

        lbl_artist.setFont(lbl_artist.getFont().deriveFont((lbl_artist.getFont().getStyle() | java.awt.Font.ITALIC)));
        lbl_artist.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl_artist.setText("Artist");

        lbl_title.setFont(lbl_title.getFont().deriveFont(lbl_title.getFont().getSize()+7f));
        lbl_title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl_title.setText("Title");

        lbl_notes.setFont(lbl_notes.getFont());
        lbl_notes.setText("content");

        lbl_notes1.setText("Notes:");

        lbl_time.setFont(lbl_time.getFont());
        lbl_time.setText("content");

        lbl_keys1.setText("Keys:");

        lbl_bpm.setFont(lbl_bpm.getFont());
        lbl_bpm.setText("content");

        lbl_keys.setFont(lbl_keys.getFont());
        lbl_keys.setText("content");

        lbl_cover.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbl_cover.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        lbl_cover.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        lbl_cover.setIconTextGap(0);
        lbl_cover.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lbl_coverMouseClicked(evt);
            }
        });

        table_chartlist.setModel(model_chartlist);
        table_chartlist.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table_scroll2.setViewportView(table_chartlist);

        javax.swing.GroupLayout panel_infoLayout = new javax.swing.GroupLayout(panel_info);
        panel_info.setLayout(panel_infoLayout);
        panel_infoLayout.setHorizontalGroup(
            panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 473, Short.MAX_VALUE)
            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panel_infoLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(table_scroll2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE)
                        .addComponent(lbl_title, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE)
                        .addGroup(panel_infoLayout.createSequentialGroup()
                            .addComponent(lbl_cover, javax.swing.GroupLayout.PREFERRED_SIZE, 146, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addGroup(panel_infoLayout.createSequentialGroup()
                                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(lbl_bpm1)
                                        .addComponent(lbl_genre1)
                                        .addComponent(lbl_level1)
                                        .addComponent(lbl_notes1)
                                        .addComponent(lbl_time1)
                                        .addComponent(lbl_keys1))
                                    .addGap(18, 18, 18)
                                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(lbl_level, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)
                                        .addComponent(lbl_notes, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)
                                        .addComponent(lbl_time, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)
                                        .addComponent(lbl_genre, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)
                                        .addComponent(lbl_bpm, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)
                                        .addComponent(lbl_keys, javax.swing.GroupLayout.DEFAULT_SIZE, 214, Short.MAX_VALUE)))
                                .addComponent(lbl_filename)))
                        .addComponent(lbl_artist, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE))
                    .addContainerGap()))
        );
        panel_infoLayout.setVerticalGroup(
            panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 352, Short.MAX_VALUE)
            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(panel_infoLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(lbl_cover, javax.swing.GroupLayout.PREFERRED_SIZE, 146, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(panel_infoLayout.createSequentialGroup()
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_bpm1)
                                .addComponent(lbl_bpm))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_genre)
                                .addComponent(lbl_genre1))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_level)
                                .addComponent(lbl_level1))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_notes1)
                                .addComponent(lbl_notes))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_time)
                                .addComponent(lbl_time1))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addGroup(panel_infoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbl_keys1)
                                .addComponent(lbl_keys))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbl_filename)))
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(lbl_title, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(lbl_artist)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(table_scroll2, javax.swing.GroupLayout.DEFAULT_SIZE, 69, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        javax.swing.GroupLayout panel_songLayout = new javax.swing.GroupLayout(panel_song);
        panel_song.setLayout(panel_songLayout);
        panel_songLayout.setHorizontalGroup(
            panel_songLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_songLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_songLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(panel_info, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_modifiers, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        panel_songLayout.setVerticalGroup(
            panel_songLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_songLayout.createSequentialGroup()
                .addComponent(panel_info, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(panel_modifiers, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        bt_play.setFont(new java.awt.Font("SansSerif", 1, 24));
        bt_play.setIcon(new javax.swing.ImageIcon(getClass().getResource("/resources/open2jam_icon.png")));
        bt_play.setText("PLAY !!!");
        bt_play.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bt_playActionPerformed(evt);
            }
        });

        cb_startPaused.setText("Start Paused");
        cb_startPaused.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cb_startPausedActionPerformed(evt);
            }
        });

        jLabel4.setText("<html>Local Matching Server<br><small>(host:port)</small>");

        txtLocalMatchingServer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtLocalMatchingServerActionPerformed(evt);
            }
        });

        btnCreateServer.setText("Create Server");
        btnCreateServer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCreateServerActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(panel_song, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(bt_play, javax.swing.GroupLayout.PREFERRED_SIZE, 359, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cb_startPaused)))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(panel_list, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(26, 26, 26)
                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtLocalMatchingServer, javax.swing.GroupLayout.PREFERRED_SIZE, 141, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCreateServer)
                        .addGap(176, 176, 176))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(panel_song, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(panel_list, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bt_play)
                    .addComponent(cb_startPaused)
                    .addComponent(txtLocalMatchingServer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCreateServer)
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(27, 27, 27))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bt_choose_dirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_choose_dirActionPerformed
       openFileChooser();
}//GEN-LAST:event_bt_choose_dirActionPerformed

    private void btn_reloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_reloadActionPerformed
        updateSelection(getCurrentDirectory());
}//GEN-LAST:event_btn_reloadActionPerformed

    private void btn_deleteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_deleteActionPerformed
        String str = "Are you sure?";
        if(JOptionPane.showConfirmDialog(this, str, "Warning",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;

        ArrayList<File> dir_list = getLibraryDirectories();

        File rem = getCurrentDirectory();
        removeLibrary(rem);  // Now deletes from SQLite database
        
        // Refresh dropdown from database (more reliable than manual removal)
        refreshSavedDirsDropdown();
        
        // Reload first available directory
        if(dir_list.isEmpty())
            openFileChooser();
        else
        {
            File f = dir_list.get(0);
            loadDir(f);
        }
}//GEN-LAST:event_btn_deleteActionPerformed

    private void lbl_coverMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lbl_coverMouseClicked
        if(selected_header == null)return;
        BufferedImage i = selected_header.getCover();
        if(i == null) return;
        JOptionPane.showMessageDialog(this, null, "Cover",
                JOptionPane.INFORMATION_MESSAGE, new ImageIcon(i));
}//GEN-LAST:event_lbl_coverMouseClicked

    private void bt_playActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bt_playActionPerformed
        try {
            if(selected_header == null) return;

            // Lazy validation: if in Database Mode, validate and parse file now
            Chart chartToPlay = selected_header;
            if (model_songlist.isDatabaseMode()) {
                // Get selected chart metadata
                int selectedRow = table_songlist.getSelectedRow();
                if (selectedRow < 0) selectedRow = 0;
                
                org.open2jam.parsers.ChartMetadata cached = model_songlist.getMetadata(selectedRow);
                if (cached != null) {
                    // Validate and load chart (checks file modification, re-parses if needed)
                    chartToPlay = org.open2jam.parsers.ChartCacheSQLite.loadChartForPlay(cached);

                    if (chartToPlay == null) {
                        JOptionPane.showMessageDialog(this,
                            "Chart file is missing or corrupted.\nPlease refresh the library.",
                            "Chart Loading Error",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    DebugLogger.debug("Loaded chart for play: " + cached.getRelativePath());
                }
            }

            final double hispeed = (Double) js_hispeed.getValue();

            // Get display mode from Configuration tab settings
            final DisplayMode dm = context.config.getGameOptions().toGameOptions().getDisplay();

            final boolean autoplay = jc_autoplay.isSelected();
            final boolean autosound = jc_autosound.isSelected();

            final boolean time_judgment = jc_timed_judgment.isSelected();

            final SpeedType speed_type =(SpeedType) combo_speedType.getSelectedItem();

            final ChannelMod channelModifier = (ChannelMod) combo_channelModifier.getSelectedItem();
            final VisibilityMod visibilityModifier = (VisibilityMod) combo_visibilityModifier.getSelectedItem();

            final float mainVol = slider_main_vol.getValue() / 100f;
            final float keyVol = slider_key_vol.getValue() / 100f;
            final float bgmVol = slider_bgm_vol.getValue() / 100f;

            final GameOptions go = context.config.getGameOptions().toGameOptions();
            go.setAutoplay(autoplay);
            go.setAutosound(autosound);
            go.setChannelModifier(channelModifier);
            go.setVisibilityModifier(visibilityModifier);
            go.setMasterVolume(mainVol);
            go.setKeyVolume(keyVol);
            go.setBGMVolume(bgmVol);
            go.setSpeedMultiplier(hispeed);
            go.setSpeedType(speed_type);
            go.setJudgmentType(jc_timed_judgment.isSelected() ? GameOptions.JudgmentType.TIME_JUDGMENT : GameOptions.JudgmentType.BEAT_JUDGMENT);

            System.out.println(go.isAutoplay());

            try{
                go.setDisplayLag(Double.parseDouble(txt_displayLag.getText()));
            }catch(Exception e){
                JOptionPane.showMessageDialog(this, "Invalid display lag value", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try{
                go.setAudioLatency(Double.parseDouble(txt_audioLatency.getText()));
            }catch(Exception e){
                JOptionPane.showMessageDialog(this, "Invalid audio latency value", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            final Render r;
            r = new Render(chartToPlay, go, dm, this.context);  // Use validated chart with context
            
            if (cb_startPaused.isSelected()) {
                r.setStartPaused();
            }
            
            if (cb_autoSyncDisplay.isSelected()) {
                r.setAutosyncDisplay();
                r.setAutosyncCallback(new Render.AutosyncCallback() {

                    @Override
                    public void autosyncFinished(double displayLag) {
                        if (JOptionPane.showConfirmDialog(MusicSelection.this, "This display latency has changed from\n"
                                + go.getDisplayLag() + "\nto\n" + displayLag + "\n\nSave this change?",
                                "Save Display Latency", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                            go.setDisplayLag(displayLag);
                            txt_displayLag.setText(displayLag + "");
                            cb_autoSyncDisplay.setSelected(false);
                        }
                    }
                });
            }
            
            else if (cb_autoSyncAudio.isSelected()) {
                r.setAutosyncAudio();
                r.setAutosyncCallback(new Render.AutosyncCallback() {

                    @Override
                    public void autosyncFinished(double audioLatency) {
                        if (JOptionPane.showConfirmDialog(MusicSelection.this, "This audio latency has changed from\n"
                                + go.getAudioLatency() + "\nto\n" + audioLatency + "\n\nSave this change?",
                                "Save Audio Latency", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                            go.setAudioLatency(audioLatency);
                            txt_audioLatency.setText(audioLatency + "");
                            cb_autoSyncAudio.setSelected(false);
                        }
                    }
                });
            }
            
            r.setLocalMatchingServer(txtLocalMatchingServer.getText());
            
            r.setRank(rank);
            
            r.setJudge(jc_timed_judgment.isSelected()
                    ? new TimeJudgment()
                    : new BeatJudgment());
            
            if (lastServer != null && !lastServer.isClosed()) {
                r.setServer(lastServer);
            }

            // CRITICAL: Run game loop directly on EDT for Wayland compatibility
            // GLFW operations (glfwSwapBuffers, glfwPollEvents) MUST run on main thread
            DebugLogger.debug("Starting game on EDT for Wayland compatibility");
            this.setEnabled(false);
            r.startRendering();
            // Game ended - re-enable and bring to front
            this.setEnabled(true);
            // Force GUI repaint and bring to front
            SwingUtilities.invokeLater(() -> {
                this.repaint();
                this.revalidate();
                // Bring parent frame to front
                java.awt.Window window = SwingUtilities.getWindowAncestor(this);
                if (window != null) {
                    window.toFront();
                    window.repaint();
                }
            });
            DebugLogger.debug("Game ended, GUI re-enabled and brought to front");
        } catch (SoundSystemException ex) {
            java.util.logging.Logger.getLogger(MusicSelection.class.getName()).log(Level.SEVERE, "{0}", ex);
        }
}//GEN-LAST:event_bt_playActionPerformed

    public void setRank(int rank) {
        this.rank = rank;
        
        if (rank == 0) jr_rank_easy.setSelected(true);
        if (rank == 1) jr_rank_normal.setSelected(true);
        if (rank == 2) jr_rank_hard.setSelected(true);
        
        int sel_row = table_songlist.getSelectedRow();
        if(sel_row >= 0)last_model_idx = table_songlist.convertRowIndexToModel(sel_row);
        model_songlist.setRank(rank);
    }
    
    private void jr_rank_easyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jr_rank_easyActionPerformed
        setRank(0);
}//GEN-LAST:event_jr_rank_easyActionPerformed

    private void jr_rank_normalActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jr_rank_normalActionPerformed
        setRank(1);
}//GEN-LAST:event_jr_rank_normalActionPerformed

    private void jr_rank_hardActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jr_rank_hardActionPerformed
        setRank(2);
}//GEN-LAST:event_jr_rank_hardActionPerformed

    private void combo_dirsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_combo_dirsItemStateChanged
        // Skip if we're refreshing the dropdown (prevent recursive calls)
        if (isRefreshingDropdown) return;
        
        // Only respond to selection events (not deselection)
        if (evt.getStateChange() != java.awt.event.ItemEvent.SELECTED) return;
        if (combo_dirs.getSelectedIndex() == -1) return;
        
        File dir = ((FileItem)combo_dirs.getSelectedItem()).file;
        
        // Prevent loading the same directory twice
        if (currentDirectory != null && dir.equals(currentDirectory)) return;
        
        // Set current directory BEFORE loading
        setCurrentDirectory(dir);
        
        loadDir(dir);
    }//GEN-LAST:event_combo_dirsItemStateChanged

    private void jc_autoplayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jc_autoplayActionPerformed
	btn_autoplay_keys.setEnabled(jc_autoplay.isSelected());
    }//GEN-LAST:event_jc_autoplayActionPerformed

    private void btn_autoplay_keysActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btn_autoplay_keysActionPerformed

    }//GEN-LAST:event_btn_autoplay_keysActionPerformed

    private void txt_displayLagActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txt_displayLagActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txt_displayLagActionPerformed

    private void cb_autoSyncDisplayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cb_autoSyncDisplayActionPerformed
        if (cb_autoSyncDisplay.isSelected()) cb_autoSyncAudio.setSelected(false);
    }//GEN-LAST:event_cb_autoSyncDisplayActionPerformed

    private void txt_audioLatencyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txt_audioLatencyActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txt_audioLatencyActionPerformed

    private void cb_autoSyncAudioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cb_autoSyncAudioActionPerformed
        if (cb_autoSyncAudio.isSelected()) {
            cb_autoSyncDisplay.setSelected(false);
            jc_autosound.setSelected(true);
        }
    }//GEN-LAST:event_cb_autoSyncAudioActionPerformed

    private void cb_startPausedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cb_startPausedActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_cb_startPausedActionPerformed

    private void txtLocalMatchingServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtLocalMatchingServerActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtLocalMatchingServerActionPerformed

    private void btnCreateServerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCreateServerActionPerformed
        
        String portText = JOptionPane.showInputDialog("What port?", "7273");
        
        if (portText == null || portText.isEmpty()) {
            return;
        }
        
        int port = 0;
        
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "Error", "Invalid port", JOptionPane.ERROR_MESSAGE, null);
            return;
        }
        
        txtLocalMatchingServer.setText("localhost:" + port);
        
        Server server = new Server(port);
        ServerUI ui = new ServerUI(server);
        
        SwingUtilities.invokeLater(ui);
        server.start();
        lastServer = server;

    }//GEN-LAST:event_btnCreateServerActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bt_choose_dir;
    private javax.swing.JButton bt_play;
    private javax.swing.JButton btnCreateServer;
    private javax.swing.JButton btn_autoplay_keys;
    private javax.swing.JButton btn_delete;
    private javax.swing.JButton btn_reload;
    private javax.swing.JCheckBox cb_autoSyncAudio;
    private javax.swing.JCheckBox cb_autoSyncDisplay;
    private javax.swing.JCheckBox cb_startPaused;
    private javax.swing.JComboBox combo_channelModifier;
    private javax.swing.JComboBox combo_dirs;
    private javax.swing.JComboBox combo_speedType;
    private javax.swing.JComboBox combo_visibilityModifier;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JCheckBox jc_autoplay;
    private javax.swing.JCheckBox jc_autosound;
    private javax.swing.JCheckBox jc_timed_judgment;
    private javax.swing.JRadioButton jr_rank_easy;
    private javax.swing.JRadioButton jr_rank_hard;
    private javax.swing.JRadioButton jr_rank_normal;
    private javax.swing.JSpinner js_hispeed;
    private javax.swing.JLabel lbl_artist;
    private javax.swing.JLabel lbl_bgm_vol;
    private javax.swing.JLabel lbl_bpm;
    private javax.swing.JLabel lbl_bpm1;
    private javax.swing.JLabel lbl_channelModifier;
    private javax.swing.JLabel lbl_cover;
    private javax.swing.JLabel lbl_filename;
    private javax.swing.JLabel lbl_genre;
    private javax.swing.JLabel lbl_genre1;
    private javax.swing.JLabel lbl_key_vol;
    private javax.swing.JLabel lbl_keys;
    private javax.swing.JLabel lbl_keys1;
    private javax.swing.JLabel lbl_level;
    private javax.swing.JLabel lbl_level1;
    private javax.swing.JLabel lbl_main_vol;
    private javax.swing.JLabel lbl_notes;
    private javax.swing.JLabel lbl_notes1;
    private javax.swing.JLabel lbl_rank;
    private javax.swing.JLabel lbl_search;
    private javax.swing.JLabel lbl_time;
    private javax.swing.JLabel lbl_time1;
    private javax.swing.JLabel lbl_title;
    private javax.swing.JLabel lbl_visibilityModifier;
    private javax.swing.JProgressBar load_progress;
    private javax.swing.JPanel panel_info;
    private javax.swing.JPanel panel_list;
    private javax.swing.JPanel panel_modifiers;
    private javax.swing.JPanel panel_setting;
    private javax.swing.JPanel panel_song;
    private javax.swing.ButtonGroup rank_group;
    private javax.swing.JSlider slider_bgm_vol;
    private javax.swing.JSlider slider_key_vol;
    private javax.swing.JSlider slider_main_vol;
    private javax.swing.JTable table_chartlist;
    private javax.swing.JScrollPane table_scroll;
    private javax.swing.JScrollPane table_scroll2;
    private javax.swing.JTable table_songlist;
    private javax.swing.JTextField txtLocalMatchingServer;
    private javax.swing.JTextField txt_audioLatency;
    private javax.swing.JTextField txt_displayLag;
    private javax.swing.JTextField txt_filter;
    // End of variables declaration//GEN-END:variables
    private void initLogic() {
        model_songlist = new ChartListTableModel();
        model_chartlist = new ChartTableModel();
    }

    /**
     * Load directory charts (from cache or scan if needed).
     * 
     * <p>Does NOT add library - that should be done separately when user adds a new directory.</p>
     */
    private void loadDir(File dir)
    {
        if(dir == null) return;

        // Set current directory BEFORE loading (prevents recursive calls)
        setCurrentDirectory(dir);

        // Clear model first to prevent duplicates
        model_songlist.clear();
        
        DebugLogger.debug("=== loadDir called for: " + dir.getAbsolutePath());

        // Load from SQLite cache (Database Mode - instant load!)
        try {
            List<org.open2jam.parsers.Library> libs = org.open2jam.parsers.ChartCacheSQLite.getAllLibraries();
            DebugLogger.debug("Found " + libs.size() + " libraries in SQLite");
            
            org.open2jam.parsers.Library library = null;
            String dirPathWithSlash = dir.getAbsolutePath().replace("\\", "/");
            if (!dirPathWithSlash.endsWith("/")) {
                dirPathWithSlash += "/";
            }
            
            for (org.open2jam.parsers.Library lib : libs) {
                DebugLogger.debug("  Library: " + lib.rootPath + " (id=" + lib.id + ")");
                // Compare with trailing slash (matches how SQLite stores paths)
                if (lib.rootPath.equals(dirPathWithSlash)) {
                    library = lib;
                    DebugLogger.debug("  ✓ Found matching library with id=" + lib.id);
                    break;
                }
            }

            if (library != null) {
                // Load cached metadata from SQLite
                ArrayList<org.open2jam.parsers.ChartMetadata> allMetadata =
                    new ArrayList<>(org.open2jam.parsers.ChartCacheSQLite.getCachedCharts(library.id));

                DebugLogger.debug("Loaded " + allMetadata.size() + " chart metadata entries from cache");

                if (!allMetadata.isEmpty()) {
                    DebugLogger.debug("✓ Using cache - " + allMetadata.size() + " entries for " + dir.getName());

                    // Set libraryRootPath for all metadata (in case JOIN didn't populate it)
                    for (org.open2jam.parsers.ChartMetadata m : allMetadata) {
                        if (m.getLibraryRootPath() == null || m.getLibraryRootPath().isEmpty()) {
                            m.setLibraryRootPath(library.rootPath);
                        }
                    }

                    // Group metadata by song_group_id to reconstruct ChartList-like structure
                    // This mimics the old VoileMap behavior where one ChartList = one file with multiple difficulties
                    java.util.Map<String, ArrayList<org.open2jam.parsers.ChartMetadata>> groupedBySong =
                        new java.util.HashMap<>();

                    for (org.open2jam.parsers.ChartMetadata m : allMetadata) {
                        String key = m.getSongGroupId();
                        if (!groupedBySong.containsKey(key)) {
                            groupedBySong.put(key, new ArrayList<>());
                        }
                        groupedBySong.get(key).add(m);
                    }

                    DebugLogger.debug("Grouped into " + groupedBySong.size() + " songs");
                    
                    // For each song group, we need to load the actual ChartList from disk
                    // This is necessary because the UI expects Chart objects, not just metadata
                    ArrayList<org.open2jam.parsers.ChartList> chartLists = new ArrayList<>();

                    for (ArrayList<org.open2jam.parsers.ChartMetadata> group : groupedBySong.values()) {
                        if (group.isEmpty()) continue;

                        // Load the actual ChartList from the first metadata's source file
                        // (all difficulties in a group come from the same file)
                        org.open2jam.parsers.ChartMetadata first = group.get(0);

                        // Build full path from library and relative path
                        String fullPath;
                        if (first.getLibraryRootPath() != null && !first.getLibraryRootPath().isEmpty()) {
                            // Remove trailing slash from rootPath if present, then add single slash
                            String rootPath = first.getLibraryRootPath().endsWith("/") ?
                                first.getLibraryRootPath().substring(0, first.getLibraryRootPath().length() - 1) :
                                first.getLibraryRootPath();
                            fullPath = rootPath + "/" + first.getRelativePath();
                        } else {
                            // Fallback: reconstruct from library info
                            String rootPath = dirPathWithSlash.endsWith("/") ?
                                dirPathWithSlash.substring(0, dirPathWithSlash.length() - 1) :
                                dirPathWithSlash;
                            fullPath = rootPath + "/" + first.getRelativePath();
                        }

                        DebugLogger.debug("  Loading ChartList from: " + fullPath);
                        File sourceFile = new File(fullPath);

                        if (sourceFile.exists()) {
                            org.open2jam.parsers.ChartList chartList =
                                org.open2jam.parsers.ChartParser.parseFile(sourceFile);

                            if (!chartList.isEmpty()) {
                                chartLists.add(chartList);
                                DebugLogger.debug("  ✓ Loaded ChartList with " + chartList.size() + " difficulties");
                            } else {
                                Logger.global.warning("  ⚠ ChartList is empty for: " + fullPath);
                            }
                        } else {
                            Logger.global.warning("  ⚠ File not found: " + fullPath);
                        }
                    }

                    DebugLogger.debug("Loaded " + chartLists.size() + " ChartList objects");
                    model_songlist.setRawList(chartLists);

                    // Save last opened library ID
                    context.config.setLastOpenedLibraryId(library.id);
                    return;  // ✅ Loaded from cache - no scan needed!
                } else {
                    Logger.global.warning("Cache is empty for library " + library.id + " - will scan files");
                }
            } else {
                Logger.global.warning("Library not found in SQLite for: " + dirPathWithSlash);
            }
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to load from cache, will scan files", e);
        }

        // Cache empty or error - scan files (ChartModelLoader will populate cache)
        DebugLogger.debug("⚠ Calling updateSelection() - will scan files");
        updateSelection(dir);
    }

    /**
     * Refresh library by rescanning files.
     * 
     * @param dir Directory to rescan
     */
    private void refreshLibrary(File dir) {
        DebugLogger.debug("Refreshing library: " + dir.getAbsolutePath());
        
        // Show progress
        load_progress.setValue(0);
        load_progress.setVisible(true);
        
        // Create new ChartModelLoader to rescan
        ChartModelLoader task = new ChartModelLoader(model_songlist, dir);
        task.addPropertyChangeListener(this);
        task.execute();
    }
    
    private void openFileChooser()
    {
        JFileChooser jfc = new JFileChooser();
        jfc.setCurrentDirectory(getCurrentDirectory());
        jfc.setDialogTitle("Choose a directory");
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        jfc.setAcceptAllFileFilterUsed(false);
        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDir = jfc.getSelectedFile();
            
            // Add library to SQLite first (if not already exists)
            addLibrary(selectedDir);
            
            // Add to dropdown if not already there
            FileItem selectedItem = new FileItem(selectedDir);
            boolean found = false;
            for (int i = 0; i < combo_dirs.getItemCount(); i++) {
                if (combo_dirs.getItemAt(i).equals(selectedItem)) {
                    found = true;
                    combo_dirs.setSelectedIndex(i);
                    break;
                }
            }
            if (!found) {
                combo_dirs.addItem(selectedItem);
                combo_dirs.setSelectedItem(selectedItem);
            }
            
            // Set current directory and load charts (from cache or scan)
            setCurrentDirectory(selectedDir);
            loadDir(selectedDir);
        }
    }

    private void updateSelection(File f) {
        bt_choose_dir.setEnabled(false);
        btn_reload.setVisible(false);
        btn_delete.setVisible(false);
        combo_dirs.setEnabled(false);
        txt_filter.setEnabled(false);
        table_songlist.setEnabled(false);
        load_progress.setValue(0);
        load_progress.setVisible(true);
        ChartModelLoader task = new ChartModelLoader(model_songlist, f);
        task.addPropertyChangeListener(this);
        task.execute();
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if("progress".equals(evt.getPropertyName()))
        {
            int i = (Integer) evt.getNewValue();
            load_progress.setValue(i);
            if(i == 100)
            {
                bt_choose_dir.setEnabled(true);
                btn_delete.setVisible(true);
                combo_dirs.setEnabled(true);
                btn_reload.setVisible(true);
                load_progress.setVisible(false);
                txt_filter.setEnabled(true);
                table_songlist.setEnabled(true);

                // Save last opened library ID after scan completes
                if (currentDirectory != null) {
                    try {
                        List<org.open2jam.parsers.Library> libs = org.open2jam.parsers.ChartCacheSQLite.getAllLibraries();
                        for (org.open2jam.parsers.Library lib : libs) {
                            if (lib.rootPath.equals(currentDirectory.getAbsolutePath().replace("\\", "/"))) {
                                context.config.setLastOpenedLibraryId(lib.id);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        Logger.global.log(Level.WARNING, "Failed to save last opened library", e);
                    }
                }

                // Refresh dropdown from SQLite (fixes empty dropdown on first cache init)
                refreshSavedDirsDropdown();
            }
        }
    }

    void updateFilter() {
        try {
            if(txt_filter.getText().length() == 0)table_sorter.setRowFilter(null);
            else table_sorter.setRowFilter(RowFilter.regexFilter("(?i)"+txt_filter.getText()));
        } catch (java.util.regex.PatternSyntaxException ignored) {
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        int i = table_songlist.getSelectedRow();
        if(i < 0 && last_model_idx >= 0){
            try {
                i = last_model_idx;
                int i_view = table_songlist.convertRowIndexToView(i);
                table_songlist.getSelectionModel().setSelectionInterval(0, i_view);
                table_scroll.getVerticalScrollBar().setValue(table_songlist.getCellRect(i_view, 0, false).y);
            } catch (IndexOutOfBoundsException up) {
                table_songlist.getSelectionModel().setSelectionInterval(0, -1);
                // not sure what to do with it here...
            }
        }else{
            i = table_songlist.convertRowIndexToModel(i);
        }
        if(model_songlist.getRow(i) == selected_chart)return;
        selected_chart = model_songlist.getRow(i);
        if(selected_chart.size() > rank)selected_header = selected_chart.get(rank);
        if(selected_chart != model_chartlist.getChartList()){
            model_chartlist.clear();
            model_chartlist.setChartList(selected_chart);
        }
        updateChartSelectionFromRank();
        updateInfo();
    }
    
    private void updateChartSelectionFromRank() {
        if (selected_chart == null) return;
        if (rank >= selected_chart.size())
            table_chartlist.getSelectionModel().setSelectionInterval(0, 0);
        else
            table_chartlist.getSelectionModel().setSelectionInterval(0, rank);
    }
    
    private void updateRankFromChartSelection() {
        if (selected_chart == null) return;
        int selectedIndex = table_chartlist.getSelectedRow();
        if (0 <= selectedIndex && selectedIndex < 3) setRank(selectedIndex);
    }

    private DecimalFormat bpm_format = new DecimalFormat(".##");
    private void updateInfo()
    {
        if(selected_header == null)return;
        if(!selected_header.getSource().exists()) {JOptionPane.showMessageDialog(this, "Doesn't Exist"); return;}
        lbl_artist.setText(resizeString(selected_header.getArtist(), 40));
        lbl_title.setText(resizeString(selected_header.getTitle(), 30));
        lbl_filename.setText(resizeString(selected_header.getSource().getName(), 30));
        lbl_genre.setText(resizeString(selected_header.getGenre(), 30));
        lbl_level.setText(selected_header.getLevel()+"");
        lbl_bpm.setText(bpm_format.format(selected_header.getBPM()));
        lbl_notes.setText(selected_header.getNoteCount()+"");
	lbl_keys.setText(selected_header.getKeys()+"");
        lbl_time.setText(time2Text(selected_header.getDuration()));

        BufferedImage i = selected_header.getCover();

        if(i != null)
        lbl_cover.setIcon(new ImageIcon(i.getScaledInstance(
                lbl_cover.getWidth(),
                lbl_cover.getHeight(),
                BufferedImage.SCALE_SMOOTH
                )));
        else
            lbl_cover.setIcon(null);
    }
    
    private String time2Text(int secs) {
        int h = secs / 60;
        int m = secs % 60;
        return m < 10 ? h+":0"+m : h+":"+m;
    }

    private String resizeString(String string, int size)
    {
        if(string == null)return "";
        if(string.length() > size)
            string = string.substring(0, size)+"...";
        return string;
    }

    // ===== Library Management Helpers =====

    /**
     * Get current directory (currently selected in dropdown).
     */
    private File getCurrentDirectory() {
        return currentDirectory;
    }
    
    /**
     * Set current directory (called when dropdown selection changes).
     */
    private void setCurrentDirectory(File dir) {
        this.currentDirectory = dir;
    }

    /**
     * Get all library directories.
     */
    private ArrayList<File> getLibraryDirectories() {
        try {
            List<org.open2jam.parsers.Library> libs = org.open2jam.parsers.ChartCacheSQLite.getAllLibraries();
            ArrayList<File> files = new ArrayList<>();
            if (libs != null) {
                for (org.open2jam.parsers.Library lib : libs) {
                    if (lib.isActive) {
                        files.add(new File(lib.rootPath));
                    }
                }
            }
            return files;
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to get library directories", e);
            return new ArrayList<>();
        }
    }

    /**
     * Add library directory.
     */
    private void addLibrary(File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            try {
                org.open2jam.parsers.ChartCacheSQLite.addLibrary(dir.getAbsolutePath(), dir.getName());
            } catch (Exception e) {
                Logger.global.log(Level.WARNING, "Failed to add library", e);
            }
        }
    }

    /**
     * Remove library directory.
     * 
     * <p>Deletes both chart cache AND library entry from SQLite database.</p>
     */
    private void removeLibrary(File dir) {
        if (dir != null) {
            try {
                // Find library by path
                List<org.open2jam.parsers.Library> libs = org.open2jam.parsers.ChartCacheSQLite.getAllLibraries();
                if (libs != null) {
                    for (org.open2jam.parsers.Library lib : libs) {
                        if (lib.rootPath.equals(dir.getAbsolutePath().replace("\\", "/"))) {
                            // Delete chart cache first
                            org.open2jam.parsers.ChartCacheSQLite.deleteCacheForLibrary(lib.id);
                            
                            // Delete library entry from database (with CASCADE for chart_cache)
                            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                                    "jdbc:sqlite:save/songcache.db");
                                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                                    "DELETE FROM libraries WHERE id = ?")) {
                                stmt.setInt(1, lib.id);
                                stmt.executeUpdate();
                            }
                            
                            DebugLogger.debug("Removed library: " + lib.name);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.global.log(Level.WARNING, "Failed to remove library", e);
            }
        }
    }

    /**
     * Refresh saved directories dropdown from SQLite database.
     * 
     * <p>Call this after library changes (add/delete/scan complete).</p>
     */
    private void refreshSavedDirsDropdown() {
        // Save current selection
        File previouslySelectedDir = currentDirectory;
        DebugLogger.debug("=== refreshSavedDirsDropdown() - previously selected: " + previouslySelectedDir);
        
        // Set flag to prevent dropdown listener from triggering
        isRefreshingDropdown = true;
        
        try {
            // Clear current items
            combo_dirs.removeAllItems();

            // Reload from SQLite
            List<org.open2jam.parsers.Library> libs = org.open2jam.parsers.ChartCacheSQLite.getAllLibraries();
            FileItem selectedItemToRestore = null;
            
            DebugLogger.debug("Reloading " + libs.size() + " libraries from SQLite");
            
            if (libs != null) {
                for (org.open2jam.parsers.Library lib : libs) {
                    FileItem item = new FileItem(new File(lib.rootPath));
                    combo_dirs.addItem(item);
                    DebugLogger.debug("  Added: " + lib.rootPath);
                    
                    // Find the previously selected directory
                    if (previouslySelectedDir != null) {
                        // Compare with trailing slash (matches how SQLite stores paths)
                        String libPath = lib.rootPath;
                        String currentPath = previouslySelectedDir.getAbsolutePath().replace("\\", "/");
                        if (!currentPath.endsWith("/")) {
                            currentPath += "/";
                        }
                        DebugLogger.debug("  Comparing: '" + libPath + "' == '" + currentPath + "' ? " + libPath.equals(currentPath));
                        
                        if (libPath.equals(currentPath)) {
                            selectedItemToRestore = item;
                            DebugLogger.debug("  ✓ Found match to restore");
                        }
                    }
                }
            }
            
            // Restore selection using setSelectedItem (forces display refresh)
            if (selectedItemToRestore != null) {
                DebugLogger.debug("Restoring selection to: " + selectedItemToRestore.file);
                combo_dirs.setSelectedItem(selectedItemToRestore);
            } else if (combo_dirs.getItemCount() > 0) {
                // If previously selected not found, select first
                DebugLogger.debug("No match found, selecting first item");
                combo_dirs.setSelectedIndex(0);
            }
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to refresh libraries dropdown", e);
        } finally {
            // Clear flag
            isRefreshingDropdown = false;
            DebugLogger.debug("=== refreshSavedDirsDropdown() complete");
        }
    }
}
