package org.openmrs.module;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.util.OpenmrsUtil;

/**
 * Utility methods for working and manipulating modules
 * 
 * @author Ben Wolfe
 * @version 1.0
 */
public class ModuleUtil {

	private static Log log = LogFactory.getLog(ModuleUtil.class);

	/**
	 * Start up the module system
	 * 
	 * @param props
	 */
	public static void startup(Properties props) {

		// Attempt to get all of the modules from the modules folder
		// and store them in the modules list
		ModuleFactory.loadAndStartModules();

		Collection<Module> modules = ModuleFactory.getStartedModules();

		if (modules == null || modules.size() == 0)
			log.debug("No modules loaded");
		else
			log.debug("Found and loaded " + modules.size() + " module(s)");

	}

	/**
	 * Stops the module system
	 */
	public static void shutdown() {

		for (Module mod : ModuleFactory.getStartedModules()) {
			log.debug("stopping module: " + mod.getModuleId());
			ModuleFactory.stopModule(mod, true);
		}
		log.debug("done shutting down modules");

	}

	/**
	 * Add the stream as a file in the modules repository
	 * 
	 * @param stream
	 * @return file just loaded
	 */
	public static File insertModuleFile(InputStream inputStream, String filename) {
		File folder = getModuleRepository();

		// check if module filename is already loaded
		if (OpenmrsUtil.folderContains(folder, filename))
			throw new ModuleException(filename
					+ " is already associated with a loaded module.");

		File file = new File(folder.getAbsolutePath() + File.separator
				+ filename);

		try {
			FileOutputStream outputStream = new FileOutputStream(file);
			OpenmrsUtil.copyFile(inputStream, outputStream);
			inputStream.close();
			outputStream.close();
		} catch (FileNotFoundException e) {
			throw new ModuleException("Can't create module file for "
					+ filename, e);
		} catch (IOException e) {
			throw new ModuleException("Can't create module file for "
					+ filename, e);
		}

		return file;
	}

	/**
	 * Compares <code>version</code> to <code>value</code>
	 * version and value are strings like w.x.y.z
	 * 
	 * @param version
	 * @param value
	 * @return	the value <code>0</code> if <code>version</code> is
     * 		equal to the argument <code>value</code>; a value less than
     * 		<code>0</code> if <code>version</code> is numerically less
     * 		than the argument <code>value</code>; and a value greater 
     * 		than <code>0</code> if <code>version</code> is numerically
     * 		 greater than the argument <code>value</code>
	 */
	public static int compareVersion(String version, String value) {
		try {
			Integer val = new Integer(value.replace(".", ""));
			Integer ver = new Integer(version.replace(".", ""));
			return (ver.compareTo(val));
		} catch (NumberFormatException e) {
			log.error("Error while converting a version to an integer", e);
			return 0;
		}
	}

	/**
	 * Gets the folder where modules are stored. ModuleExceptions are thrown on
	 * errors
	 * 
	 * @return folder containing modules
	 */
	public static File getModuleRepository() {
		
		String filepath = OpenmrsUtil.getApplicationDataDirectory() + "modules";
		
		File folder = new File(filepath);

		if (!folder.exists()) {
			log.warn("Module repository doesn't exist: "
					+ folder.getAbsolutePath());

			// create the modules folder if it doesn't exist
			if (!folder.exists()) {
				log.warn(folder.getAbsolutePath() + " doesn't exist.  Creating directories now.");
				folder.mkdirs();
			}
		}

		if (!folder.isDirectory())
			throw new ModuleException("Module repository is not a directory at: "
					+ folder.getAbsolutePath());

		return folder;
	}

	/**
	 * Utility method to convert a {@link File} object to a local URL.
	 * 
	 * @param file
	 *            a file object
	 * @return absolute URL that points to the given file
	 * @throws MalformedURLException
	 *             if file can't be represented as URL for some reason
	 */
	public static URL file2url(final File file) throws MalformedURLException {
		if (file == null)
			return null;
		try {
			return file.getCanonicalFile().toURI().toURL();
		} catch (MalformedURLException mue) {
			throw mue;
		} catch (IOException ioe) {
			throw new MalformedURLException("Cannot convert: " + file.getName()
					+ " to url");
		} catch (NoSuchMethodError nsme) {
			throw new MalformedURLException("Cannot convert: " + file.getName()
					+ " to url");
		}
	}

	/**
	 * Expand the given fileToExpand to the tmpModuleFile (expected to be a
	 * directory) If <code>name</code> is null, the entire jar is expanded. if
	 * <code>name</code> is not null, then only that file is expanded.
	 * 
	 * @param fileToExpand
	 * @param tmpModuleDir
	 * @param name
	 * @param keepFullPath
	 */
	public static void expandJar(File fileToExpand, File tmpModuleDir,
			String name, boolean keepFullPath) throws IOException {
		JarFile jarFile = null;
		InputStream input = null;
		String docBase = tmpModuleDir.getAbsolutePath();
		try {
			jarFile = new JarFile(fileToExpand);
			Enumeration jarEntries = jarFile.entries();
			boolean foundName = (name == null);
			while (jarEntries.hasMoreElements()) {
				JarEntry jarEntry = (JarEntry) jarEntries.nextElement();
				if (name == null || jarEntry.getName().startsWith(name)) {
					String entryName = jarEntry.getName();
					// trim out the name path from the name of the new file
					if (keepFullPath == false && name != null)
						entryName = entryName.replaceFirst(name, "");
					int last = entryName.lastIndexOf('/');
					if (last >= 0) {
						File parent = new File(docBase, entryName.substring(0,
								last));
						parent.mkdirs();
						log.warn("Creating parent dirs: "
								+ parent.getAbsolutePath());
					}
					if (entryName.endsWith("/") || entryName.equals("")) {
						continue;
					}
					input = jarFile.getInputStream(jarEntry);
					expand(input, docBase, entryName);
					input.close();
					input = null;
					foundName = true;
				}
			}
			if (!foundName)
				log.warn("Unable to find: " + name + " in file "
						+ fileToExpand.getAbsolutePath());

		} catch (IOException e) {
			log.warn("Unable to delete tmpModuleFile on error", e);
			throw e;
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Throwable t) {
					;
				}
				input = null;
			}
			if (jarFile != null) {
				try {
					jarFile.close();
				} catch (Throwable t) {
					;
				}
				jarFile = null;
			}
		}
	}

	private static void expand(InputStream input, String docBase, String name)
			throws IOException {
		log.warn("expanding: " + name);
		File file = new File(docBase, name);
		FileOutputStream outStream = null;
		BufferedOutputStream output = null;
		try {
			outStream = new FileOutputStream(file);
			OpenmrsUtil.copyFile(input, outStream);
		} catch (IOException io) {
			throw io;
		} finally {
			try {
				if (outStream != null)
					outStream.close();
			} catch (IOException io) {
				// pass;
			}
			try {
				if (output != null)
					output.close();
			} catch (IOException io) {
				// pass;
			}
		}

	}
	
	/**
	 * Downloads the contents of a URL and copies them to a string
	 * (Borrowed from oreilly)
	 * 
	 * @param URL
	 * @return String contents of the URL
	 **/
	public static String getURL(URL url) {
		if (!url.toString().endsWith("update.rdf")) {
			log.warn("Illegal url: " + url);
			return "";
		}
		
		InputStream in = null;   
	    OutputStream out = null;
	    String output = "";
	    try {
	        URLConnection uc = url.openConnection();
	        uc.setDefaultUseCaches(false);
	        uc.setUseCaches(false);
	        uc.setRequestProperty("Cache-Control","max-age=0,no-cache");
	        uc.setRequestProperty("Pragma","no-cache");
	        //uc.setRequestProperty("Cache-Control","no-cache");
	        
	        log.error("Logging an attempt to connect to: " + url);
	        
	        in = uc.getInputStream();
	        out = new ByteArrayOutputStream();
	        
	        // Now copy bytes from the URL to the output stream
	        byte[] buffer = new byte[4096];
	        int bytes_read;
	        while((bytes_read = in.read(buffer)) != -1)
	            out.write(buffer, 0, bytes_read);
	        output = out.toString();
	    }
	    catch (IOException io) {
	    	log.warn("io while reading: " + url, io);
	    }
	    finally {
	        try { in.close();  out.close(); } catch (Exception e) {}
	    }
	    
	    return output;
	}
	
	public static void checkForModuleUpdates() throws ModuleException {
		
		for (Module mod : ModuleFactory.getLoadedModules()) {
			String updateURL = mod.getUpdateURL();
			if (updateURL != null && !updateURL.equals("")) {
				try {
					// get the contents pointed to by the url
					URL url = new URL(updateURL);
					String content = getURL(url);
					
					// skip empty or invalid updates
					if (content.equals(""))
						continue;
					
					// process and parse the contents 
					UpdateFileParser parser = new UpdateFileParser(content);
					parser.parse();
					
					log.warn("Update for mod: " + mod.getModuleId() + " compareVersion result: " + compareVersion(mod.getVersion(), parser.getCurrentVersion()));
					
					// check the udpate.rdf version against the installed version
					if (compareVersion(mod.getVersion(), parser.getCurrentVersion()) < 0) {
						if (mod.getModuleId().equals(parser.getModuleId())) {
							mod.setDownloadURL(parser.getDownloadURL());
							mod.setUpdateVersion(parser.getCurrentVersion());
						}
						else
							log.warn("Module id does not match in update.rdf:" + parser.getModuleId());
					}
					else {
						mod.setDownloadURL(null);
						mod.setUpdateVersion(null);
					}
				}
				catch (MalformedURLException e) {
					log.warn("Unable to form a URL object out of: " + updateURL, e);
				}
			}
		}
	}
	
}
