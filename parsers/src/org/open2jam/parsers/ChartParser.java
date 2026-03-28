package org.open2jam.parsers;

import java.io.File;

/** this is the main parser class.
*** it has methods to find out the type of file
*** and delegate the job to the right parser
**/
public abstract class ChartParser
{
	// Prevent instantiation and subclassing - utility class with only static methods
	private ChartParser() {}

	/** parse and returns a ChartList object */
	public static ChartList parseFile(File file)
	{
	    if(OJNParser.canRead(file)) return OJNParser.parseFile(file);
	    return new ChartList();
	}
}
