package org.openmrs.module;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.GlobalProperty;
import org.openmrs.Privilege;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class will parse a file into an org.openmrs.module.Module
 * 
 * @author bwolfe
 * @version 1.0
 */
public class ModuleFileParser {
	
	private Log log = LogFactory.getLog(this.getClass());
	
	private File moduleFile = null;
	
	/**
	 * Contructor 
	 * @param moduleFile the module (jar)file that will be parsed
	 */
	public ModuleFileParser(File moduleFile) {
		if (moduleFile == null)
			throw new ModuleException("Module file cannot be null");
		
		if (!moduleFile.getName().endsWith(".omod"))
			throw new ModuleException("Module file does not have the correct .omod file extension", moduleFile.getName());
		
		this.moduleFile = moduleFile;
	}
	
	private List<String> validConfigVersions() {
		List<String> versions = new Vector<String>();
		versions.add("1.0");
		return versions;
	}
	
	/**
	 * Get the module 
	 * @return new module object
	 */
	public Module parse() throws ModuleException {
		
		Module module = null;
		JarFile jarfile = null;
		InputStream configStream = null;
		
		try {
			try {
				jarfile = new JarFile(moduleFile);
			}
			catch (IOException e) {
				throw new ModuleException("Unable to get jar file", moduleFile.getName(), e);
			}
			
			// look for config.xml in the root of the module
			ZipEntry config = jarfile.getEntry("config.xml");
			if (config == null)
				throw new ModuleException("Error loading module. No config.xml found.", moduleFile.getName());
			
			
			// get a config file stream
			try {
				configStream = jarfile.getInputStream(config);
			}
			catch (IOException e) {
				throw new ModuleException("Unable to get config file stream", moduleFile.getName(), e);
			}
			
			
			// turn the config file into an xml document
			Document configDoc = null;
			try {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				configDoc = db.parse(configStream);
			}
			catch (Exception e) {
				throw new ModuleException("Error parsing module config.xml file", moduleFile.getName(), e);
			}
			
			Element rootNode = configDoc.getDocumentElement();
			
			String configVersion = rootNode.getAttribute("configVersion");
			
			if (!validConfigVersions().contains(configVersion))
				throw new ModuleException("Invalid config version: " + configVersion, moduleFile.getName());
			
			String name = getElement(rootNode, configVersion, "name");
			String moduleId = getElement(rootNode, configVersion, "id");
			String packageName = getElement(rootNode, configVersion, "package");
			String author = getElement(rootNode, configVersion, "author");
			String desc = getElement(rootNode, configVersion, "description");
			String version = getElement(rootNode, configVersion, "version");
			
			// do some validation
			if (name == null || name.length() == 0)
				throw new ModuleException("name cannot be empty", moduleFile.getName());
			if (moduleId == null || moduleId.length() == 0)
				throw new ModuleException("module id cannot be empty", name);
			if (packageName == null || packageName.length() == 0)
				throw new ModuleException("package cannot be empty", name);
			
			// create the module object
			module = new Module(name, moduleId, packageName, author, desc, version);
			
			// find and load the activator class
			module.setActivatorName(getElement(rootNode, configVersion, "activator"));
			
			// get libraries
			List<Library> libraries = new Vector<Library>();
			for (ModelLibrary model : getLibraries(rootNode, configVersion))
				libraries.add(new Library(module, model));
			module.setLibraries(libraries);
			
			
			module.setRequireDatabaseVersion(getElement(rootNode, configVersion, "require_database_version"));
			module.setRequireOpenmrsVersion(getElement(rootNode, configVersion, "require_version"));
			module.setUpdateURL(getElement(rootNode, configVersion, "updateURL"));
			module.setRequiredModules(getRequiredModules(rootNode, configVersion));
			
			module.setAdvicePoints(getAdvice(rootNode, configVersion, module));
			module.setExtensionNames(getExtensions(rootNode, configVersion));
			
			module.setPrivileges(getPrivileges(rootNode, configVersion));
			module.setGlobalProperties(getGlobalProperties(rootNode, configVersion));
			
			module.setMessages(getMessages(rootNode, configVersion, jarfile));
			
			module.setConfig(configDoc);
			
			module.setFile(moduleFile);
		}
		catch (ModuleException e) {
			if (configStream != null) {
				try {
					configStream.close();					
				}
				catch (IOException io) {
					log.error("Error while closing config stream for module: " + moduleFile.getAbsolutePath(), io);
				}
			}
			// rethrow the moduleException
			throw e;
		}
		finally {
			try {
				jarfile.close();
			}
			catch (IOException e) {
				log.warn("Unable to close jarfile: " + jarfile.getName());
			}
		}
		
		return module;
	}
	
	/**
	 * Generic method to get a module tag
	 * @param root
	 * @param version
	 * @param tag
	 * @return
	 */
	private String getElement(Element root, String version, String tag) {
		if (root.getElementsByTagName(tag).getLength() > 0)
			return root.getElementsByTagName(tag).item(0).getTextContent();
		return "";
	}
	
	/**
	 * load in required modules list
	 * @param root
	 * @param version
	 * @return
	 */
	private List<String> getRequiredModules(Element root, String version) {
		NodeList mods = root.getElementsByTagName("require_modules");
		
		List<String> requiredModules = new Vector<String>();
		
		// TODO test require_modules section
		if (mods.getLength() > 0) {
			int i = 0;
			while (i < mods.getLength()) {
				Node n = mods.item(i);
				if (n != null && "require_module".equals(n.getNodeName()))
					requiredModules.add(n.getTextContent());
				
				i++;
			}
		}
		
		return requiredModules;
	}
	
	/**
	 * load in advicePoints
	 * 
	 * @param root
	 * @param version
	 * @return
	 */
	private List<AdvicePoint> getAdvice(Element root, String version, Module mod) {
		
		List<AdvicePoint> advicePoints = new Vector<AdvicePoint>();
		
		NodeList advice = root.getElementsByTagName("advice");
		if (advice.getLength() > 0) {
			log.debug("# advice: " + advice.getLength());
			int i = 0;
			while (i < advice.getLength()) {
				Node node = advice.item(i);
				NodeList nodes = node.getChildNodes();
				int x = 0;
				String point = "", adviceClass = "";
				while (x < nodes.getLength()) {
					Node childNode = nodes.item(x);
					if ("point".equals(childNode.getNodeName()))
						point = childNode.getTextContent();
					else if ("class".equals(childNode.getNodeName()))
						adviceClass = childNode.getTextContent();
					x++;
				}
				log.debug("point: " + point + " class: " + adviceClass);
				
				// point and class are required
				if (point.length() > 0 && adviceClass.length() > 0) {
					advicePoints.add(new AdvicePoint(mod, point, adviceClass));
				}
				else
					log.warn("'point' and 'class' are required for advice. Given '" + point + "' and '" + adviceClass + "'");
	
				i++;
			}
		}
		
		return advicePoints;
	}
	
	/**
	 * load in extensions
	 * @param root
	 * @param configVersion
	 * @return
	 */
	private IdentityHashMap<String, String> getExtensions(Element root, String configVersion) {
		
		IdentityHashMap<String, String> extensions = new IdentityHashMap<String, String>();
		
		NodeList extensionNodes = root.getElementsByTagName("extension");
		if (extensionNodes.getLength() > 0) {
			log.debug("# extensions: " + extensionNodes.getLength());
			int i = 0;
			while (i < extensionNodes.getLength()) {
				Node node = extensionNodes.item(i);
				NodeList nodes = node.getChildNodes();
				int x = 0;
				String point = "", extClass = "";
				while (x < nodes.getLength()) {
					Node childNode = nodes.item(x);
					if ("point".equals(childNode.getNodeName()))
						point = childNode.getTextContent();
					else if ("class".equals(childNode.getNodeName()))
						extClass = childNode.getTextContent();
					x++;
				}
				log.debug("point: " + point + " class: " + extClass);
				
				// point and class are required
				if (point.length() > 0 && extClass.length() > 0) {
					if (point.indexOf("|") != -1)
						log.warn("Point id contains illegal character: '|'");
					else {
						extensions.put(point, extClass);
					}
				}
				else
					log.warn("'point' and 'class' are required for extensions. Given '" + point + "' and '" + extClass + "'");
				i++;
			}
		}
		
		return extensions;
		
	}
	
	/**
	 * load in messages 
	 * @param root
	 * @param configVersion
	 * @return
	 */
	private Map<String, Properties> getMessages(Element root, String configVersion, JarFile jarfile) {
		
		Map<String, Properties> messages = new HashMap<String, Properties>();
		
		NodeList messageNodes = root.getElementsByTagName("messages");
		if (messageNodes.getLength() > 0) {
			log.debug("# message nodes: " + messageNodes.getLength());
			int i = 0;
			while (i < messageNodes.getLength()) {
				Node node = messageNodes.item(i);
				NodeList nodes = node.getChildNodes();
				int x = 0;
				String lang = "", file = "";
				while (x < nodes.getLength()) {
					Node childNode = nodes.item(x);
					if ("lang".equals(childNode.getNodeName()))
						lang = childNode.getTextContent();
					else if ("file".equals(childNode.getNodeName()))
						file = childNode.getTextContent();
					x++;
				}
				log.debug("lang: " + lang + " file: " + file);
				
				// lang and file are required
				if (lang.length() > 0 && file.length() > 0) {
					InputStream inStream = null;
					try {
						ZipEntry entry = jarfile.getEntry(file);
						inStream = jarfile.getInputStream(entry);
						Properties props = new Properties();
						props.load(inStream);
						messages.put(lang, props);
					}
					catch (IOException e) {
						log.warn("Unable to load properties: " + file);
					}
					finally {
						if (inStream != null) {
							try {
								inStream.close();					
							}
							catch (IOException io) {
								log.error("Error while closing property input stream for module: " + moduleFile.getAbsolutePath(), io);
							}
						}
					}
				}
				else
					log.warn("'lang' and 'file' are required for extensions. Given '" + lang + "' and '" + file + "'");
				i++;
			}
		}
		
		return messages;
	}
	
	/**
	 * 
	 * @param root
	 * @param configVersion
	 * @return
	 */
	private List<ModelLibrary> getLibraries(Element root, String configVersion) {
		
		List<ModelLibrary> libraries = new Vector<ModelLibrary>();
		
		NodeList libNodes = root.getElementsByTagName("library");
		int i = 0;
		while (i < libNodes.getLength()) {
			Node node = libNodes.item(i++);
			NamedNodeMap attrs = node.getAttributes();
			ModelLibrary model = new ModelLibrary();
			Node attr = attrs.getNamedItem("id");
			if (attr != null)
				model.setId(attr.getNodeValue());
			attr = attrs.getNamedItem("path");
			if (attr != null)
				model.setPath(attr.getNodeValue());
			attr = attrs.getNamedItem("version");
			if (attr != null)
				model.setVersion(attr.getNodeValue());
			attr = attrs.getNamedItem("type");
			if (attr != null)
				model.setCodeLibrary(attr.getNodeValue());
			
			libraries.add(model);
		}
		
		return libraries;
	}
	
	/**
	 * load in required privileges
	 * @param root
	 * @param version
	 * @return
	 */
	private List<Privilege> getPrivileges(Element root, String version) {
		
		List<Privilege> privileges = new Vector<Privilege>();
		
		NodeList privNodes = root.getElementsByTagName("privilege");
		if (privNodes.getLength() > 0) {
			log.debug("# privileges: " + privNodes.getLength());
			int i = 0;
			while (i < privNodes.getLength()) {
				Node node = privNodes.item(i);
				NodeList nodes = node.getChildNodes();
				int x = 0;
				String name = "", description = "";
				while (x < nodes.getLength()) {
					Node childNode = nodes.item(x);
					if ("name".equals(childNode.getNodeName()))
						name = childNode.getTextContent();
					else if ("description".equals(childNode.getNodeName()))
						description = childNode.getTextContent();
					x++;
				}
				log.debug("name: " + name + " description: " + description);
				
				// name and desc are required
				if (name.length() > 0 && description.length() > 0)
					privileges.add(new Privilege(name, description));
				else
					log.warn("'name' and 'description' are required for privileges. Given '" + name + "' and '" + description + "'");
	
				i++;
			}
		}
		
		return privileges;
	}
	
	/**
	 * load in required global properties and defaults
	 * @param root
	 * @param version
	 * @return
	 */
	private List<GlobalProperty> getGlobalProperties(Element root, String version) {
		
		List<GlobalProperty> properties = new Vector<GlobalProperty>();
		
		NodeList propNodes = root.getElementsByTagName("globalProperty");
		if (propNodes.getLength() > 0) {
			log.debug("# global props: " + propNodes.getLength());
			int i = 0;
			while (i < propNodes.getLength()) {
				Node node = propNodes.item(i);
				NodeList nodes = node.getChildNodes();
				int x = 0;
				String property = "", defaultValue = "", description = "";
				while (x < nodes.getLength()) {
					Node childNode = nodes.item(x);
					if ("property".equals(childNode.getNodeName()))
						property = childNode.getTextContent();
					else if ("defaultValue".equals(childNode.getNodeName()))
						defaultValue = childNode.getTextContent();
					else if ("description".equals(childNode.getNodeName()))
						description = childNode.getTextContent();
					
					x++;
				}
				log.debug("property: " + property + " defaultValue: " + defaultValue + " description: " + description);
				
				// name and desc are required
				if (property.length() > 0)
					properties.add(new GlobalProperty(property, defaultValue /*, description*/ ));
				else
					log.warn("'property' is required for global properties. Given '" + property + "'");
	
				i++;
			}
		}
		
		return properties;
	}
	
}
