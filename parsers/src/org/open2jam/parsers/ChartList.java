package org.open2jam.parsers;

import java.io.File;
import java.util.ArrayList;

/**
 * the chart list contains all charts
 * produced by a source, ordered by level
 *
 * @author fox
 */
@SuppressWarnings("java:S2160")  // Equality comparison not used - ChartList is only iterated, never compared
public class ChartList extends ArrayList<Chart>
{
    File sourceFile;

    public File getSource()
    {
        return sourceFile;
    }
}
