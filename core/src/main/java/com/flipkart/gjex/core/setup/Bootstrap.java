/*
 * Copyright 2012-2016, the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.flipkart.gjex.core.setup;

import java.lang.management.ManagementFactory;
import java.util.List;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.flipkart.gjex.core.Application;
import com.flipkart.gjex.core.Bundle;
import com.google.common.collect.Lists;

/**
 * The pre-start application container, containing services required to bootstrap a GJEX application
 * 
 * @author regu.b
 *
 */
public class Bootstrap {

	private final Application application;
	private final MetricRegistry metricRegistry;
	private final List<Bundle> bundles;
	private ClassLoader classLoader;
	
	public Bootstrap(Application application) {
		this.application = application;
		this.metricRegistry = new MetricRegistry();
		this.bundles = Lists.newArrayList();
		getMetricRegistry().register("jvm.buffers", new BufferPoolMetricSet(ManagementFactory
                .getPlatformMBeanServer()));
		getMetricRegistry().register("jvm.gc", new GarbageCollectorMetricSet());
		getMetricRegistry().register("jvm.memory", new MemoryUsageGaugeSet());
		getMetricRegistry().register("jvm.threads", new ThreadStatesGaugeSet());

		JmxReporter.forRegistry(getMetricRegistry()).build().start();
	}
	
	
	/**
	 * Gets the bootstrap's Application
	 */
	public Application getApplication() {
		return application;
	}

	/**
     * Returns the bootstrap's class loader.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Sets the bootstrap's class loader.
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
    /**
     * Adds the given bundle to the bootstrap.
     *
     * @param bundle a {@link Bundle}
     */
    public void addBundle(Bundle bundle) {
        bundle.initialize(this);
        bundles.add(bundle);
    }    
	
    /**
     * Returns the application's metrics.
     */
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
    
    /**
     * Rns this Bootstrap's bundles in the specified Environment
     * @param environment the Application Environment
     * @throws Exception in case of errors during run
     */
    public void run(Environment environment) throws Exception {
        for (Bundle bundle : bundles) {
            bundle.run(environment);
        }
    }
}
