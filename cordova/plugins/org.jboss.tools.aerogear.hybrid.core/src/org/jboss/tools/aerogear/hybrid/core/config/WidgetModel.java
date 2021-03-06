/*******************************************************************************
 * Copyright (c) 2013 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *       Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.jboss.tools.aerogear.hybrid.core.config;

import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.NS_PHONEGAP_1_0;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.NS_W3C_WIDGET;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_ACCESS;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_AUTHOR;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_CONTENT;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_FEATURE;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_ICON;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_LICENSE;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_PREFERENCE;
import static org.jboss.tools.aerogear.hybrid.core.config.WidgetModelConstants.WIDGET_TAG_SPLASH;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.model.ModelLifecycleEvent;
import org.eclipse.wst.sse.core.internal.provisional.IModelLifecycleListener;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.cleanup.CleanupProcessorXML;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.jboss.tools.aerogear.hybrid.core.HybridCore;
import org.jboss.tools.aerogear.hybrid.core.HybridProject;
import org.jboss.tools.aerogear.hybrid.core.platform.PlatformConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Manager for all access to the config.xml (Widget) model. 
 * Model is strictly tied with the {@link Document} that 
 * it was created for. Reading, persistence etc of the Document 
 * object used to create Widget model are responsibility of the 
 * caller.
 *  
 * @author Gorkem Ercan
 *
 */
@SuppressWarnings("restriction")
public class WidgetModel implements IModelLifecycleListener{
	
	private static final String PATH_CONFIG_XML = "/"+PlatformConstants.DIR_WWW+"/"+PlatformConstants.FILE_XML_CONFIG;
	
	private static Map<HybridProject, WidgetModel> widgetModels = new HashMap<HybridProject, WidgetModel>();
	public static final String[] ICON_EXTENSIONS = {"gif", "ico", "jpeg", "jpg", "png","svg" };
	
	private HybridProject project;
	private Widget editableWidget;
	private Widget readonlyWidget;
	private long readonlyTimestamp;

	private IStructuredModel underLyingModel;
	
	
	private WidgetModel(HybridProject project){
		this.project = project;
	}
	
	public static final WidgetModel getModel(HybridProject project){
		if( !widgetModels.containsKey(project) ){
			synchronized (WidgetModel.class) {
				WidgetModel wm = new WidgetModel(project);
				widgetModels.put(project,wm);
			}
		}
		return widgetModels.get(project);
	}
	
	public static final void shutdown(){
		Collection<WidgetModel> createdModels = widgetModels.values();
		for (WidgetModel widgetModel : createdModels) {
			widgetModel.dispose();
		}
	}
	
	
	
	/**
	 * Returns the {@link Widget} model for the config.xml
	 * 
	 * @return widget
	 * @throws CoreException
	 * 	<ul>
	 *   <li>if config.xml can not be parsed</li>
	 *   <li>its contents is not readable</li>
	 *   </ul>
	 *
	 */
	public Widget getWidgetForRead() throws CoreException{
		long enter = System.currentTimeMillis();
		IFile configXml = getConfigXml();
		if (!configXml.exists()) {
			return null;
		}
		if (readonlyWidget == null || readonlyTimestamp != configXml.getModificationStamp()) {
			synchronized (this) {
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				dbf.setNamespaceAware(true);
				DocumentBuilder db;
				try {
					db = dbf.newDocumentBuilder();
					Document configDocument = db.parse(configXml.getLocation().toFile());
					readonlyWidget = load(configDocument);
					readonlyTimestamp = configXml.getModificationStamp();
					
				} catch (ParserConfigurationException e) {
					throw new CoreException(new Status(IStatus.ERROR,
							HybridCore.PLUGIN_ID,
							"Parser error when parsing config.xml", e));
				} catch (SAXException e) {
					throw new CoreException(new Status(IStatus.ERROR,
							HybridCore.PLUGIN_ID, "Parsing error on config.xml", e));
				} catch (IOException e) {
					throw new CoreException(new Status(IStatus.ERROR,
							HybridCore.PLUGIN_ID,
							"IO error when parsing config.xml", e));
				}
			}
		}
		HybridCore.trace("Completed WidgetModel.getWidgetForRead it "+ Long.toString(System.currentTimeMillis() - enter)+ "ms");
		return readonlyWidget;
	}
	
	
	public Widget getWidgetForEdit() throws CoreException {
		long enter = System.currentTimeMillis();
		if (editableWidget == null){
			synchronized (this) {
				IFile configXml = getConfigXml();
				IModelManager manager = StructuredModelManager
						.getModelManager();
				try {
					underLyingModel = manager.getModelForEdit(configXml);
					if ((underLyingModel != null) && (underLyingModel instanceof IDOMModel)) {
						underLyingModel.addModelLifecycleListener(this);
						IDOMModel domModel = (IDOMModel) underLyingModel;
						editableWidget = load(domModel.getDocument());
					}
				} catch (IOException e) {
					throw new CoreException(new Status(IStatus.ERROR,
							HybridCore.PLUGIN_ID,
							"Error creating widget model", e));
				}
			}
		}
		HybridCore.trace("Completed WidgetModel.getWidgetForEdit it "+ Long.toString(System.currentTimeMillis() - enter)+ "ms");
		return editableWidget;
	}

	private IFile getConfigXml() {
		IProject prj = this.project.getProject();
		IFile configXml = prj.getFile(PATH_CONFIG_XML);
		return configXml;
	}
	
	
	private Widget load(Document document) {
		return new Widget(document.getDocumentElement());
	}
	
	private void reloadEditableWidget(Document document) {
		editableWidget.reload(document.getDocumentElement());
	}
	
	public void save() throws CoreException {
		if (this.editableWidget != null && underLyingModel != null) {
			synchronized (this) {
				CleanupProcessorXML cp = new CleanupProcessorXML();
				try {
					cp.cleanupModel(underLyingModel);
					underLyingModel.save();
				} catch (IOException e) {
					throw new CoreException(new Status(IStatus.ERROR,
							HybridCore.PLUGIN_ID,
							"Error saving changes to config.xml", e));
				}
			}
		}
	}
	
	/**
	 * Creates an {@link Author} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new Author
	 */
	public Author createAuthor(Widget widget){
		return createObject(widget, NS_W3C_WIDGET, WIDGET_TAG_AUTHOR, Author.class);
	}
	/**
	 * Creates a {@link Content} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return  new Content
	 */
	public Content createContent(Widget widget){
		return createObject(widget, NS_W3C_WIDGET, WIDGET_TAG_CONTENT, Content.class);
	}
	/**
	 * Creates a {@link Preference} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new Preference 
	 */
	public Preference createPreference(Widget widget){
		return createObject(widget, NS_W3C_WIDGET, WIDGET_TAG_PREFERENCE, Preference.class);
	}
	/**
	 * Creates a {@link Feature} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new Feature
	 */
	public Feature createFeature(Widget widget){
		return createObject(widget, NS_W3C_WIDGET, WIDGET_TAG_FEATURE, Feature.class);
	}
	/**
	 * Creates a {@link Access} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new Access 
	 */
	public Access createAccess(Widget widget){
		return createObject(widget, NS_W3C_WIDGET, WIDGET_TAG_ACCESS, Access.class);
	}
	
	/**
	 * Creates a {@link Plugin} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new Splash 
	 */	
	public Icon createIcon(Widget widget){
		return createObject(widget,NS_W3C_WIDGET, WIDGET_TAG_ICON,Icon.class);
	}
	
	/**
	 * Creates a {@link Plugin} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new Splash 
	 */
	public Splash createSplash(Widget widget){
		return createObject(widget, NS_PHONEGAP_1_0, WIDGET_TAG_SPLASH, Splash.class);
	}
	
	/**
	 * Creates a {@link License} instance. This also creates the necessary 
	 * DOM elements on the {@link Document} associated with the widget.
	 * The new DOM elements are not inserted to the tree until 
	 * {@link Widget}'s proper set/add method is called
	 * 
	 * @param widget - parent widget
	 * @return new License
	 */
	public License createLicense(Widget widget){
		return createObject(widget, NS_W3C_WIDGET, WIDGET_TAG_LICENSE, License.class);
	}
	
	
	private <T extends AbstractConfigObject> T createObject(Widget widget, String namespace, String tag, Class<T> clazz ){
		if(widget != editableWidget){
			throw new IllegalArgumentException("Widget model is not editable");
		}
		Document doc = widget.itemNode.getOwnerDocument();
		if (doc == null )
			throw new IllegalStateException("Widget is not properly constructed");
		Element el = doc.createElementNS(namespace, tag);
		
		try {
			return clazz.getDeclaredConstructor(Node.class).newInstance(el);
		} catch (Exception e){
			HybridCore.log(IStatus.ERROR, "Error invoking the Node constructor for config model object", e);
		}
		return null;
	}

	@Override
	public void processPostModelEvent(ModelLifecycleEvent event) {
		if(event.getType() == ModelLifecycleEvent.MODEL_DIRTY_STATE && !underLyingModel.isDirty()){
			synchronized (this) {
				IDOMModel dom = (IDOMModel)underLyingModel;
				reloadEditableWidget(dom.getDocument());
				//release the readOnly model to be reloaded
				this.readonlyWidget = null;
				this.readonlyTimestamp = -1;
			}
		}
	}

	@Override
	public void processPreModelEvent(ModelLifecycleEvent event) {
	}
	
	synchronized void dispose(){
		if(underLyingModel != null ){
			underLyingModel.releaseFromEdit();
		}
		this.editableWidget = null;
		this.readonlyWidget = null;
	}
	
}
