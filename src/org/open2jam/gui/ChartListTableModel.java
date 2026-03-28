package org.open2jam.gui;

import org.open2jam.parsers.Chart;
import org.open2jam.parsers.ChartList;
import org.open2jam.parsers.ChartMetadata;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for song list display.
 * 
 * <p>Supports two modes:</p>
 * <ol>
 *   <li><strong>Memory Mode:</strong> Stores ChartList objects (legacy, for refresh scans)</li>
 *   <li><strong>Database Mode:</strong> Stores ChartMetadata from SQLite (fast startup)</li>
 * </ol>
 * 
 * <p>Database Mode is preferred - loads instantly from cache without file parsing.</p>
 * 
 * @author open2jam-modern team
 */
public class ChartListTableModel implements TableModel {
    
    private ArrayList<ChartList> chartLists;           // Memory mode
    private ArrayList<ChartMetadata> chartMetadata;    // Database mode
    private final String[] col_names = new String[] { "Name", "Level", "Genre" };
    private int rank;
    private final ArrayList<TableModelListener> listeners;

    public ChartListTableModel() {
        listeners = new ArrayList<TableModelListener>();
        chartLists = new ArrayList<ChartList>();
        chartMetadata = new ArrayList<ChartMetadata>();
    }

    /**
     * Clear all data.
     */
    public void clear() {
        chartLists.clear();
        chartMetadata.clear();
    }

    /**
     * Add rows from parsed ChartList objects (Memory Mode).
     * 
     * @param l List of ChartList from file parsing
     */
    public void addRows(List<ChartList> l) {
        chartLists.addAll(l);
        fireListeners();
    }

    /**
     * Set data from ChartMetadata list (Database Mode).
     * 
     * @param metadata List of ChartMetadata from SQLite
     */
    public void setMetadataList(ArrayList<ChartMetadata> metadata) {
        chartMetadata = metadata;
        chartLists.clear();  // Clear memory mode
        fireListeners();
    }

    /**
     * Set raw ChartList (legacy, for backward compatibility).
     * 
     * @param list ChartList array
     * @deprecated Use setMetadataList() for database mode
     */
    @Deprecated
    public void setRawList(ArrayList<ChartList> list) {
        chartLists = list;
        chartMetadata.clear();  // Clear database mode
        fireListeners();
    }

    /**
     * Get raw ChartList (legacy).
     * 
     * @return ChartList array
     * @deprecated Use getMetadataList() for database mode
     */
    @Deprecated
    public ArrayList<ChartList> getRawList() {
        return chartLists;
    }

    /**
     * Get metadata list (Database Mode).
     * 
     * @return ChartMetadata array
     */
    public ArrayList<ChartMetadata> getMetadataList() {
        return chartMetadata;
    }

    /**
     * Set rank (difficulty selection: 0=Easy, 1=Normal, 2=Hard).
     * 
     * @param rank Difficulty index
     */
    public void setRank(int rank) {
        this.rank = rank;
        fireListeners();
    }

    /**
     * Get ChartList at row (Memory Mode).
     * 
     * @param row Row index
     * @return ChartList or null
     */
    public ChartList getRow(int row) {
        try {
            return chartLists.get(row);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Get ChartMetadata at row (Database Mode).
     * 
     * @param row Row index
     * @return ChartMetadata or null
     */
    public ChartMetadata getMetadata(int row) {
        try {
            return chartMetadata.get(row);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * Check if model is in Database Mode.
     * 
     * @return true if using ChartMetadata from SQLite
     */
    public boolean isDatabaseMode() {
        return !chartMetadata.isEmpty();
    }

    @Override
    public int getRowCount() {
        // Use whichever mode is active
        return chartMetadata.isEmpty() ? chartLists.size() : chartMetadata.size();
    }

    @Override
    public int getColumnCount() {
        return col_names.length;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return col_names[columnIndex];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch(columnIndex) {
            case 0: return String.class;
            case 1: return Integer.class;
            case 2: return String.class;
        }
        return Object.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        // Database Mode - read from ChartMetadata
        if (isDatabaseMode()) {
            ChartMetadata m = getMetadata(rowIndex);
            if (m == null) return null;

            // Get the specific difficulty's metadata
            // (chartMetadata has one row per difficulty)
            switch(columnIndex) {
                case 0:
                    String title = m.getDisplayTitle();
                    // Show difficulty indicator
                    if (m.getChartIndex() == 0) title = "[EX] " + title;
                    else if (m.getChartIndex() == 1) title = "[NX] " + title;
                    else if (m.getChartIndex() == 2) title = "[HX] " + title;
                    return title;
                case 1: return m.getLevel();
                case 2: return m.getGenre() != null ? m.getGenre() : "";
            }
            return null;
        }
        
        // Memory Mode - read from ChartList (legacy)
        Chart c;
        if (chartLists.size() <= rowIndex) return null;
        if (chartLists.get(rowIndex).isEmpty()) return null;
        
        if (chartLists.get(rowIndex).size() - 1 < rank)
            c = chartLists.get(rowIndex).get(0);
        else
            c = chartLists.get(rowIndex).get(rank);
            
        switch(columnIndex) {
            case 0:
                String str = c.getTitle();
                if (chartLists.get(rowIndex).size() - 1 < rank)
                    str = "[AUTO-EASY] " + str;
                return str;
            case 1: return c.getLevel();
            case 2: return c.getGenre();
        }
        return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new UnsupportedOperationException("Can't do that cowboy");
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
        listeners.remove(l);
    }

    private void fireListeners() {
        TableModelEvent e = new TableModelEvent(this);
        fireListeners(e);
    }

    private void fireListeners(TableModelEvent e) {
        for (TableModelListener l : listeners) {
            l.tableChanged(e);
        }
    }
}
