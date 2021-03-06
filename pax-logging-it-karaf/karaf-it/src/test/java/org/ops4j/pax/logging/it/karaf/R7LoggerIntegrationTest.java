/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ops4j.pax.logging.it.karaf;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.logging.it.karaf.support.Helpers;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;

import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.OptionUtils.combine;

@RunWith(PaxExam.class)
public class R7LoggerIntegrationTest extends AbstractControlledIntegrationTestBase {

    @Inject
    protected ConfigurationAdmin cm;

    @Configuration
    public Option[] configure() {
        return combine(
                baseConfigure()
        );
    }

    @Test
    public void usageThroughOsgiR7() throws BundleException, IOException {
        Optional<Bundle> paxLoggingLog4j2 = Arrays.stream(context.getBundles())
                .filter(b -> "org.ops4j.pax.logging.pax-logging-log4j2".equals(b.getSymbolicName()))
                .findFirst();
        Bundle log4j2 = paxLoggingLog4j2.orElse(null);

        File logFile = new File(System.getProperty("karaf.data"), "log/R7LoggerIntegrationTest.log");

        // for now, we have default configuration = no org.ops4j.pax.logging PID
        Helpers.updateLoggingConfig(context, cm, c -> {
            c.put("log4j2.appender.file.type", "File");
            c.put("log4j2.appender.file.name", "file");
            c.put("log4j2.appender.file.fileName", logFile.getAbsolutePath());
            c.put("log4j2.appender.file.layout.type", "PatternLayout");
            c.put("log4j2.appender.file.layout.pattern", "%c [%p] %m%n");
            c.put("log4j2.rootLogger.level", "debug");
            c.put("log4j2.rootLogger.appenderRef.file.ref", "file");
        });

        ServiceReference<LoggerFactory> sr = context.getServiceReference(org.osgi.service.log.LoggerFactory.class);
        LoggerFactory loggerFactory = context.getService(sr);

        org.osgi.service.log.Logger log = loggerFactory.getLogger("my.logger");
        log.info("INFO1 through org.osgi.service.log.Logger");
        log.debug("DEBUG1 through org.osgi.service.log.Logger");
        log.warn("WARN1 through org.osgi.service.log.Logger {}", "arg1");

        log = loggerFactory.getLogger("my.logger", FormatterLogger.class);
        log.info("INFO2 through org.osgi.service.log.Logger");
        log.debug("DEBUG2 through org.osgi.service.log.Logger");
        log.warn("WARN2 through org.osgi.service.log.Logger %s", "arg1");

        log.error("ERROR1 through org.osgi.service.log.Logger", new Exception("Hello"));

        if (log4j2 != null) {
            log4j2.stop(Bundle.STOP_TRANSIENT);
        }

        // file should be flushed
        List<String> lines = Files.readAllLines(logFile.toPath());

        assertTrue(lines.contains("my.logger [INFO] INFO1 through org.osgi.service.log.Logger"));
        assertTrue(lines.contains("my.logger [DEBUG] DEBUG1 through org.osgi.service.log.Logger"));
        assertTrue(lines.contains("my.logger [WARN] WARN1 through org.osgi.service.log.Logger arg1"));
        assertTrue(lines.contains("my.logger [INFO] INFO2 through org.osgi.service.log.Logger"));
        assertTrue(lines.contains("my.logger [DEBUG] DEBUG2 through org.osgi.service.log.Logger"));
        assertTrue(lines.contains("my.logger [WARN] WARN2 through org.osgi.service.log.Logger arg1"));
        assertTrue(lines.contains("my.logger [ERROR] ERROR1 through org.osgi.service.log.Logger"));
        assertTrue(lines.contains("java.lang.Exception: Hello"));
    }

}
