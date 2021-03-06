/*
 * Copyright (c) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.flipkart.gjex.runtime.boot;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.codahale.metrics.MetricRegistry;
import com.flipkart.gjex.Constants;
import com.flipkart.gjex.core.config.FileLocator;
import com.flipkart.gjex.core.config.YamlConfiguration;
import com.flipkart.gjex.core.logging.Logging;
import com.flipkart.gjex.core.service.Service;
import com.flipkart.gjex.grpc.service.GrpcServer;
import com.flipkart.gjex.guice.module.ConfigModule;
import com.flipkart.gjex.guice.module.DashboardModule;
import com.flipkart.gjex.guice.module.ServerModule;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.palominolabs.metrics.guice.MetricsInstrumentationModule;

import io.grpc.BindableService;

/**
 * <code>GJEXInitializer</code> initializes the grpc-jexpress (GJEX) runtime using the various Guice modules
 *
 * @author regunath.balasubramanian
 */
public class GJEXInitializer implements Logging {
	
	/** The GJEX startup display contents*/
	private static final MessageFormat STARTUP_DISPLAY = new MessageFormat(
            "\n*************************************************************************\n" +	
					" ╔═╗ ╦╔═╗═╗ ╦  \n" +
					" ║ ╦ ║║╣ ╔╩╦╝  " + "    Startup Time : {0}" + " ms\n" + 
					" ╚═╝╚╝╚═╝╩ ╚═  " + "    Host Name: {1} \n " +
             "*************************************************************************"
    );
    
	/** The machine name where this GJEX instance is running */
	private String hostName;
	
	/** List of initialized Service instances*/
	List<Service> services;

    /**
     * Constructor for this class
     */
    public GJEXInitializer() {
	    	try {
	    		this.hostName = InetAddress.getLocalHost().getHostName();
	    	} catch (UnknownHostException e) {
	    		//ignore the exception, not critical information
	    	}        
    }
	
    /**
     * Startup entry method 
     */
    public static void main(String[] args) throws Exception {
    		final GJEXInitializer gjexInitializer = new GJEXInitializer();
    		gjexInitializer.start();
    		
    }
    
    public void start() throws Exception {
    		info("** GJEX starting up... **");
    		long start = System.currentTimeMillis();
        //load GJEX runtime container
    		loadGJEXRuntimeContainer();
        final Object[] displayArgs = {
				(System.currentTimeMillis() - start),
				this.hostName,
        };
		info(STARTUP_DISPLAY.format(displayArgs));
        info("** GJEX startup complete **");
    }
    
    @SuppressWarnings("unchecked")
	private void loadGJEXRuntimeContainer() {
		List<AbstractModule> grpcModules = new LinkedList<AbstractModule>();
		// add the Config and Metrics MetricsInstrumentationModule
		grpcModules.add( new ConfigModule());
		grpcModules.add(MetricsInstrumentationModule.builder().withMetricRegistry(new MetricRegistry()).build());
		// add the Dashboard module
		grpcModules.add(new DashboardModule());
		// add the Grpc Server module
		grpcModules.add(new ServerModule());
		// locate and load all GRPC modules
        try {
	    		for (File grpcModuleConfig : FileLocator.findFiles(Constants.GRPC_MODULE_NAMES_CONFIG)) {
	    			YamlConfiguration yamlConfiguration = new YamlConfiguration(grpcModuleConfig.toURI().toURL());
	    			Iterator<String> propertyKeys = yamlConfiguration.getKeys();
	    	        while (propertyKeys.hasNext()) {
	    	            String propertyKey = propertyKeys.next();
	    	            if (propertyKey.equalsIgnoreCase(Constants.GRPC_MODULE_NAMES)) {
	    	            		for (String moduleName : (List<String>)yamlConfiguration.getProperty(propertyKey)) {
	    	            			grpcModules.add((AbstractModule)Class.forName(moduleName).newInstance());
	    	            		}
	    	            }
	    	        }
	    		}
        } catch (Exception e) {
        		error("Error loading GJEX Runtime Container", e);
            throw new RuntimeException(e);
        }
		Injector injector = Guice.createInjector(grpcModules);
		// Add all Grpc Services to the Grpc Server
		injector.getInstance(GrpcServer.class).registerServices(this.getInstances(injector, BindableService.class));
		// Identify all Service implementations, start them and register for Runtime shutdown hook
		this.services = this.getInstances(injector, Service.class);
		this.services.forEach(service -> {
			try {
				service.start();
			} catch (Exception e) {
				error("Error starting a Service : " + service.getClass().getName(), e);
                throw new RuntimeException(e);
			}
		});
		this.registerServicesForShutdown();
    }
    
    private <T> List<T> getInstances(Injector injector, Class<T> type) {
        List<T> instances = new ArrayList<T>();
        List<Binding<T>> bindings = injector.findBindingsByType(TypeLiteral.get(type));
        for(Binding<T> binding : bindings) {
            Key<T> key = binding.getKey();
            instances.add(injector.getInstance(key));
        }
        return instances;
    } 
    
    private void registerServicesForShutdown() {
    		Runtime.getRuntime().addShutdownHook(new Thread() {
    			@Override
    			public void run() {
	    	        // Use stderr here since the logger may have been reset by its JVM shutdown hook.
	    	        System.err.println("*** Shutting down gRPC server since JVM is shutting down");
	    	        services.forEach(Service::stop);
	    	        System.err.println("*** Server shut down");
    			}
    	    });    		
    }
    
}
