package org.werelate.crawler;

/**
 * @author Christiaan Hees
 * 
 * This product includes software developed by Gargoyle Software Inc.
 * (http://www.GargoyleSoftware.com/).
 * 
 * Used versions: htmlunit-1.6 and log4j-1.2.8
 * 
 * Release date: 30 June 2005
 * 
 * 
 * This class provides the common methods that all crawlers use.
 * Extend this class to make a crawler for a specific site.
 */


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.ThreadedRefreshHandler;
import com.gargoylesoftware.htmlunit.Page;

import org.apache.log4j.Logger;

public class BasicCrawler {
	protected static String PROGRAM_NAME = "org.werelate.BasicCrawler";
	protected static Logger logger = Logger.getLogger(BasicCrawler.class);
	
	private static int delay; // The delay between page fetches in seconds
	private long lastPageRequestTime;
	private static final int MAX_RETRIES = 5; // How many times getPage() should try
	private static final long MILLIS_BETWEEN_ERRORS = 15000;
	
	private BufferedWriter out; // The output stream
	
	protected WebClient webClient;
    private static final int DEFAULT_PAGE_DELAY = 5;

    /**
	 * Sets up the default logger, output stream and WebClient.
	 * Also parses the arguments from main() to setup the delay. 
	 * @param args The arguments from main()
	 */
	public BasicCrawler(String[] args) {
		//PropertyConfigurator.configure("log4j.properties");
		logger.info(PROGRAM_NAME+" program started.");
		
		// Parse the argument and set the delay
		if(args.length<1) {
			delay = DEFAULT_PAGE_DELAY; // Default delay is 5 seconds
		}
		else {
			try {
				delay = Integer.parseInt(args[0]);
			} catch(NumberFormatException e) {
				logger.fatal("Wrong argument: "+args[0]);
				System.out.println("Please give an integer as the argument specifying the delay between page fetches in seconds.");
				System.out.println("Example:   "+PROGRAM_NAME+" 10");
				System.exit(1);
			}
		}
		
		// Setup the output stream.
		// The output will be saved in PROGRAM_NAME+".out".
		// For example: FuneralHomes1.out
		try {
	        out = new BufferedWriter(new FileWriter(PROGRAM_NAME+".out"));
	    } catch (IOException e) {
	    	logger.fatal("IOException for "+PROGRAM_NAME+".out");
	    	e.printStackTrace();
	    	System.exit(1);
	    }
	    
	    reset();	    
	}
	
	/**
	 * Makes a new WebClient object and gives it the default values.
	 * The Newspapers1 program needs to call this every once in a while to clean up
	 * the lingering connections. Without this the connections will stay in a 
	 * CLOSE_WAIT state.
	 */
	public void reset() {
		webClient = new WebClient(BrowserVersion.INTERNET_EXPLORER_7);
		// There are a lot of broken JavaScripts so disabling it saves some errors:
	    webClient.setJavaScriptEnabled(false);
	    webClient.setTimeout(18000); // Note: the timeout seems to be in ms
	    
	    // java.lang.RuntimeException: Refresh Aborted by HtmlUnit: Attempted to refresh a page
		// using an ImmediateRefreshHandler which could have caused an OutOfMemoryError Please use
		// WaitingRefreshHandler or ThreadedRefreshHandler instead.
		// Caused by: <META HTTP-EQUIV="Refresh" CONTENT="3300; URL=http://newslink.org">
		// A ThreadedRefreshHandler avoids the above exception, but introduces the new problem
	    // that the connections will stay open since they stay in a Threads that don't seem to
	    // stop until the end of the program.
	    // The solution seems to be to use a ThreadedRefreshHandler except for in certain cases.
	    // Newspapers1 and Newspapers3 use setNullRefresh() and setThreadRefresh() to switch
	    // between those RefreshHandlers.
	    webClient.setRefreshHandler(new ThreadedRefreshHandler());
	    
	    System.runFinalization();
	    System.gc();
	}
	public void setNullRefresh() {
		webClient.setRefreshHandler(null);
	}
	public void setThreadRefresh() {
		webClient.setRefreshHandler(new ThreadedRefreshHandler());
	}
	
	/**
	 * Tries to get a Page. Waits N seconds between each request, where N is the
	 * argument of the current program or 45 if none was given. If the page could not
	 * be retrieved it waits 15 seconds and retries up to 4 times.
	 * @param url The url of the page that should be retrieved.
	 * @return The HtmlPage containing the retrieved page or null if there was an error.
	 */
	public Page getPage(URL url) throws IOException
   {
		long curTime = System.currentTimeMillis();
		if (curTime < lastPageRequestTime + delay*1000) {
			try {
				Thread.sleep(lastPageRequestTime + delay*1000 - curTime);
			} catch(InterruptedException e) {
				// This should not happen
				logger.error("InterruptedException in getPage 1.");
				e.printStackTrace();
			}
		}
		
		int exceptionCount = 0;
		Page page = null;
		while (page == null && exceptionCount <= MAX_RETRIES) {
//			try {
				lastPageRequestTime = System.currentTimeMillis();
				page = webClient.getPage(url);
//			} catch (Exception e) {
//				logger.warn("request " + url.toString()
//						+ " generated exception: " + e.toString());
//				exceptionCount++;
//				try {
//					Thread.sleep(MILLIS_BETWEEN_ERRORS);
//				} catch(InterruptedException ex) {
//					// This should not happen
//					logger.error("InterruptedException in getPage 2.");
//					e.printStackTrace();
//				}
//			}
		}
		
		if(page==null) {
			logger.error("Unable to get page: "+url);
		}
		return page;
	}
	
	/**
	 * Converts urlString to an URL object and then calls getPage(URL).
	 * @param urlString The url of the page that should be retrieved.
	 * @return Returns the result from getPage(URL).
	 */
	public Page getPage(String urlString) throws IOException
   {
		URL url = null;
		try {
			url = new URL(urlString);
		} catch(MalformedURLException e) {
			logger.error("The URL is not valid: "+urlString);
			return null;
		}
		return getPage(url);
	}
	
	/**
	 * Writes elements to a file, delimiting them with "|" and ending with a newline.
	 * @param elements An array of Strings that should be written to the outputfile.
	 */
	public void printline(String[] elements) {
		for(int i=0; i<elements.length; i++) {
			try {
				out.write(elements[i]);
				if(i+1<elements.length)
					out.write("|");
				else
					out.write("\n");
				out.flush();
			} catch(IOException e) {
				logger.error("Error writing to "+PROGRAM_NAME+".out");
				e.printStackTrace();
			}
		}
	}

   public void printline(String line) {
      try {
         out.write(line);
         out.write("\n");
         out.flush();
      } catch(IOException e) {
         logger.error("Error writing to "+PROGRAM_NAME+".out");
         e.printStackTrace();
      }
   }
}

