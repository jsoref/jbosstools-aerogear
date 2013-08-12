package org.jboss.tools.aerogear.hybrid.ui.plugins.internal;

import java.util.ArrayList;
import java.util.List;

import javax.xml.crypto.dsig.keyinfo.PGPData;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.jboss.tools.aerogear.hybrid.core.plugin.CordovaPlugin;
import org.jboss.tools.aerogear.hybrid.core.plugin.CordovaPluginInfo;
import org.jboss.tools.aerogear.hybrid.core.plugin.CordovaPluginRegistryClient;
import org.jboss.tools.aerogear.hybrid.ui.HybridUI;

public class RegistryConfirmPage extends WizardPage {

	private CordovaPluginViewer pluginViewer;

	protected RegistryConfirmPage(String pageName) {
		super(pageName);
		setImageDescriptor(HybridUI.getImageDescriptor(HybridUI.PLUGIN_ID, CordovaPluginWizard.IMAGE_WIZBAN));

	}

	@Override
	public void createControl(Composite parent) {
		pluginViewer = new CordovaPluginViewer();
		pluginViewer.createControl(parent);
		setControl(pluginViewer.getControl());
	}
	
	void setSelectedPlugins(List<CordovaPluginInfo> selected){
		CordovaPluginRegistryClient client = new CordovaPluginRegistryClient("http://registry.cordova.io");
		ArrayList<CordovaPlugin> plugins = new ArrayList<CordovaPlugin>();
		for (CordovaPluginInfo cordovaPluginInfo : selected) {
			plugins.add(client.getCordovaPlugin(cordovaPluginInfo.getName()));
		}
		pluginViewer.getViewer().setInput(plugins);
	}

}