/*
 * Copyright (c) 2017, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent.plugin.httpservlet.interceptor;

import com.megaease.easeagent.plugin.Interceptor;
import com.megaease.easeagent.plugin.MethodInfo;
import com.megaease.easeagent.plugin.annotation.AdviceTo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.config.Config;
import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.api.metric.MetricRegistry;
import com.megaease.easeagent.plugin.api.metric.name.NameFactory;
import com.megaease.easeagent.plugin.api.metric.name.Tags;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.httpservlet.advice.DoFilterAdvice;
import com.megaease.easeagent.plugin.httpservlet.utils.InternalAsyncListener;
import com.megaease.easeagent.plugin.httpservlet.utils.ServletUtils;
import com.megaease.easeagent.plugin.utils.metrics.ServerMetric;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@AdviceTo(value = DoFilterAdvice.class, qualifier = "default")
public class DoFilterMetricInterceptor implements Interceptor {
    private static final Logger LOGGER = EaseAgent.getLogger(DoFilterMetricInterceptor.class);
    private static final String AFTER_MARK = DoFilterMetricInterceptor.class.getName() + "$AfterMark";
    private static final Object ENTER = new Object();
    private static final Object START = new Object();
    private static final NameFactory NAME_FACTORY = ServerMetric.buildNameFactory();
    private final ServerMetric serverMetric = null;

    public DoFilterMetricInterceptor() {
        Config config = EaseAgent.configFactory.getConfig("observability", "httpservlet", Order.METRIC.getName());
        Tags tags = new Tags("application", "http-request", "url");

        //TODO new metric registry: can not load from class loader
//        MetricRegistry metricRegistry = EaseAgent.metricRegistrySupplier.newMetricRegistry(config, NAME_FACTORY, tags);
//        this.serverMetric = new ServerMetric(metricRegistry, NAME_FACTORY);
    }

    @Override
    public void before(MethodInfo methodInfo, Context context) {
        if (!context.enter(ENTER, 1)) {
            return;
        }
        context.put(START, System.currentTimeMillis());
    }

    @Override
    public void after(MethodInfo methodInfo, Context context) {
        if (!context.out(ENTER, 1)) {
            return;
        }
        final long start = context.remove(START);
        HttpServletRequest httpServletRequest = (HttpServletRequest) methodInfo.getArgs()[0];
        if (ServletUtils.markProcessedAfter(httpServletRequest, AFTER_MARK)) {
            return;
        }
        HttpServletResponse httpServletResponse = (HttpServletResponse) methodInfo.getArgs()[1];
        if (httpServletRequest.isAsyncStarted()) {
            httpServletRequest.getAsyncContext().addListener(new InternalAsyncListener(
                    asyncEvent -> {
                        HttpServletRequest suppliedRequest = (HttpServletRequest) asyncEvent.getSuppliedRequest();
                        HttpServletResponse suppliedResponse = (HttpServletResponse) asyncEvent.getSuppliedResponse();
                        internalAfter(methodInfo, suppliedRequest, suppliedResponse, start);
                    }
                )
            );
        } else {
            internalAfter(methodInfo, httpServletRequest, httpServletResponse, start);
        }
    }

    private void internalAfter(MethodInfo methodInfo, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, long start) {
        long end = System.currentTimeMillis();
        String httpRoute = ServletUtils.getHttpRouteAttributeFromRequest(httpServletRequest);
        String key = httpServletRequest.getMethod() + " " + httpRoute;
//        this.serverMetric.collectMetric(key, httpServletResponse.getStatus(), methodInfo.getThrowable(), start, end);
    }

    @Override
    public String getName() {
        return Order.METRIC.getName();
    }
}
