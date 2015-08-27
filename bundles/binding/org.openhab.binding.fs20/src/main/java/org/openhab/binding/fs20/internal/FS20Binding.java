/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.fs20.internal;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.fs20.FS20BindingConfig;
import org.openhab.binding.fs20.FS20BindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.io.transport.cul.CULCommunicationException;
import org.openhab.io.transport.cul.CULDeviceException;
import org.openhab.io.transport.cul.CULHandler;
import org.openhab.io.transport.cul.CULListener;
import org.openhab.io.transport.cul.CULManager;
import org.openhab.io.transport.cul.CULMode;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class implements the communcation between openHAB and FS20 devices. Via
 * RF received updates are received directly, there is no polling.
 * 
 * @author Till Klocke
 * @since 1.4.0
 */
public class FS20Binding extends AbstractActiveBinding<FS20BindingProvider>
		implements ManagedService, CULListener {

	private static final Logger logger = LoggerFactory
			.getLogger(FS20Binding.class);
	/**
	 * Controls the way, {@link FS20Binding} interprets the 
	 * {@link FS20Command#DIM_UP} and {@link FS20Command#DIM_DOWN}.
	 * In {@link DimMode#UP_DOWN} the binding publishes {@link UpDownType} 
	 * events.
	 * In {@link DimMode#INC_DEC} the binding publishes {@link IncreaseDecreaseType} 
	 * events.   
	 */	
	static enum DimMode{
		UP_DOWN, INC_DEC
	}
	private final static String KEY_DEVICE_NAME = "device";
	private final static String KEY_BAUD_RATE = "baudrate";
	private final static String KEY_PARITY = "parity";
	private final static String KEY_DIM_MODE = "dimmode";
	
	private String deviceName;
		
	private Map<String, Object> properties = new HashMap<String, Object>();

	private CULHandler cul;

	/**
	 * the refresh interval which is used to poll values from the FS20 server
	 * (optional, defaults to 60000ms)
	 */
	private long refreshInterval = 60000;
	
	/**
	 * Reaction to dim_up/dim_down commands
	 */
	private DimMode dimMode = DimMode.UP_DOWN;

	public FS20Binding() {
	}

	public void activate() {
		logger.debug("Activating FS20 binding");
	}
	
	private void updateDeviceSettings(){
		if (cul != null) {
			CULManager.close(cul);
		}
		getCULHandler();
	}

	private void getCULHandler() {
		try {
			logger.debug("Opening CUL device on " + deviceName);
			cul = CULManager.getOpenCULHandler(deviceName, CULMode.SLOW_RF, properties);
			cul.registerListener(this);
		} catch (CULDeviceException e) {
			logger.error("Can't open cul device", e);
			cul = null;
		}
	}

	public void deactivate() {
		logger.debug("Deactivating FS20 binding");
		cul.unregisterListener(this);
		CULManager.close(cul);
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected String getName() {
		return "FS20 Refresh Service";
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected void execute() {
		// Nothing to do here
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		FS20BindingConfig bindingConfig = null;
		for (FS20BindingProvider provider : super.providers) {
			bindingConfig = provider.getConfigForItemName(itemName);
			if (bindingConfig != null) {
				break;
			}
		}
		if (bindingConfig != null) {
			logger.debug("Received command " + command.toString()
					+ " for item " + itemName);
			try {
				FS20Command fs20Command = FS20CommandHelper
						.convertHABCommandToFS20Command(command);
				cul.send("F" + bindingConfig.getAddress()
						+ fs20Command.getHexValue());
			} catch (CULCommunicationException e) {
				logger.error("An exception occured while sending a command", e);
			}
		}
	}

	/**
	 * @{inheritDoc
	 */
	@Override
	public void updated(Dictionary<String, ?> config)
			throws ConfigurationException {
		logger.debug("Received new config");
		if (config != null) {

			Boolean configChanged = false;
			/**
			 * Valid values of the baudRateString
			 * "75", "110", "300", "1200", "2400", "4800", 
			 * "9600", "19200", "38400", "57600", "115200"
			 * @see org.openhab.io.transport.cul.internal.CULSerialHandlerImpl
			 */
			String baudRateString = (String) config.get(KEY_BAUD_RATE);
			if(StringUtils.isNotBlank(baudRateString)){
				properties.put(KEY_BAUD_RATE, baudRateString);
				configChanged = true;
			}
			
			/**
			 * Valid values of the parityString
			 * "NONE"
			 * "ODD"
			 * "EVEN"
			 * "MARK"
			 * "SPACE"
			 * @see org.openhab.io.transport.cul.internal.CULSerialHandlerImpl
			 */
			String parityString = (String) config.get(KEY_PARITY);
			if(StringUtils.isNotBlank(parityString)){
				properties.put(KEY_PARITY, parityString);
				configChanged = true;
			}
			
			// to override the default refresh interval one has to add a
			// parameter to openhab.cfg like
			// <bindingName>:refresh=<intervalInMs>
			String refreshIntervalString = (String) config.get("refresh");
			if (StringUtils.isNotBlank(refreshIntervalString)) {
				refreshInterval = Long.parseLong(refreshIntervalString);
			}
			String deviceName = (String) config.get(KEY_DEVICE_NAME);
			if (StringUtils.isEmpty(deviceName)) {
				logger.error("No device name configured");
				setProperlyConfigured(false);
				throw new ConfigurationException(KEY_DEVICE_NAME,
						"The device name can't be empty");
			} else {
				this.deviceName = deviceName;
				configChanged = true;
			}
			
			if(configChanged){
				updateDeviceSettings();
			}
			setProperlyConfigured(true);
			// read further config parameters here ...
			String dimModeString = (String) config.get(KEY_DIM_MODE);
			if (StringUtils.isNotEmpty(dimModeString)) {
				try{
					dimMode= DimMode.valueOf(dimModeString);	
				}catch(IllegalArgumentException e){
					logger.warn("Wrong dimmode value. Only " + DimMode.values() +" are supported");
				}
			}
			logger.debug("Dim mode " + dimMode);
		}
	}

	@Override
	public void dataReceived(String data) {
		// It is possible that we see here messages of other protocols
		if (data.startsWith("F")) {
			logger.debug("Received FS20 message: " + data);
			handleReceivedMessage(data);
		}

	}

	private void handleReceivedMessage(String message) {
		String houseCode = (message.substring(1, 5));
		String address = (message.substring(5, 7));
		String command = message.substring(7, 9);
		String fullAddress = houseCode + address;
		FS20BindingConfig config = null;
		for (FS20BindingProvider provider : providers) {
			config = provider.getConfigForAddress(fullAddress);
			if (config != null) {
				break;
			}
		}
		if (config != null) {
			FS20Command fs20Command = FS20Command.getFromHexValue(command);
			logger.debug("Received command " + fs20Command.toString()
					+ " for device " + config.getAddress());
			String itemName = config.getItem().getName();
			Type typeFromFS20Command = FS20CommandHelper.getTypeFromFS20Command(fs20Command, dimMode);
			publishEvent(itemName, typeFromFS20Command);

		} else {
			logger.debug("Received message for unknown device " + fullAddress);
		}
	}

	private void publishEvent(String itemName, Type typeFromFS20Command) {
		if (typeFromFS20Command instanceof State){
			eventPublisher.postUpdate(itemName,(State) typeFromFS20Command);
		} else
		if (typeFromFS20Command instanceof Command){
			eventPublisher.postCommand(itemName,(Command) typeFromFS20Command);
		} else {
			logger.warn("Wrong event type created from FS20 command");
		}
	}

	@Override
	public void error(Exception e) {
		logger.error("Error while communicating with CUL", e);

	}

}
