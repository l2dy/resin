/*
 * Copyright (c) 2023 Zero -- all rights reserved
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Zero
 */

package com.caucho.server.admin;

import com.caucho.config.ConfigException;
import com.caucho.config.Service;
import com.caucho.config.types.Period;
import com.caucho.env.meter.AbstractMeter;
import com.caucho.env.meter.CountMeter;
import com.caucho.env.meter.MeterService;
import com.caucho.env.meter.TotalMeter;
import com.caucho.env.service.AbstractResinSubSystem;
import com.caucho.env.service.ResinSystem;
import com.caucho.lifecycle.Lifecycle;
import com.caucho.server.cluster.ServletService;
import com.caucho.util.Alarm;
import com.caucho.util.AlarmListener;
import com.caucho.util.L10N;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

@Service
public class PrometheusStatSystem extends AbstractResinSubSystem implements AlarmListener {
    private static final L10N L = new L10N(PrometheusStatSystem.class);
    protected final Logger log = Logger.getLogger(getClass().getName());
    private long _samplePeriod = 30000L;
    private final Lifecycle _lifecycle = new Lifecycle(log);
    private final ServletService _servletService;
    private boolean _isResinServer = false;
    private final AtomicReference<Sample[]> _sampleRef = new AtomicReference<>(new Sample[0]);
    private final Alarm _alarm = new Alarm(this);
    private final SortedMap<Sample, Double> _sampleData = new TreeMap<>();

    protected PrometheusStatSystem() {
        _servletService = ServletService.getCurrent();
        if (_servletService != null) {
            _isResinServer = _servletService.isResinServer();
        }
    }

    public static PrometheusStatSystem createAndAddService() {
        PrometheusStatSystem service = new PrometheusStatSystem();

        ResinSystem system = preCreate(PrometheusStatSystem.class);

        system.addService(PrometheusStatSystem.class, service);

        return service;
    }

    public static PrometheusStatSystem getCurrent() {
        return ResinSystem.getCurrentService(PrometheusStatSystem.class);
    }

    @PostConstruct
    public void init() {
    }

    @Override
    public void start() throws Exception {
        super.start();
        init();

        if (!_lifecycle.toActive() || !_isResinServer) {
            return;
        }

        MeterService oldMeterService = PrometheusMeterService.getCurrent();
        if (oldMeterService instanceof PrometheusMeterService && ((PrometheusMeterService) oldMeterService).isConfigured()) {
            throw new ConfigException(L.l("PrometheusMeterService is already configured."));
        }

        PrometheusMeterService meterService;
        if (oldMeterService instanceof PrometheusMeterService) {
            meterService = (PrometheusMeterService) oldMeterService;
        } else {
            meterService = new PrometheusMeterService();
        }
        meterService.setService(this);
        this._alarm.queue(_samplePeriod);
    }

    public void setSamplePeriod(Period period) {
        _samplePeriod = period.getPeriod();
    }

    public long getSamplePeriod() {
        return _samplePeriod;
    }

    public void addMeter(AbstractMeter probe) {
        String name = probe.getName();
        Sample sample = new Sample(name, probe);
        addSample(sample);
    }

    /**
     * Adds a sample to the statistics. Safe to call concurrently.
     *
     * @param sample the sample to add
     */
    public void addSample(Sample sample) {
        while (true) {
            Sample[] oldSamples = _sampleRef.get();

            // Check for duplicates
            for (Sample oldSample : oldSamples) {
                if (oldSample.getId() == sample.getId()) {
                    return;
                }
            }

            Sample[] newSamples = new Sample[oldSamples.length + 1];
            System.arraycopy(oldSamples, 0, newSamples, 0, oldSamples.length);
            newSamples[oldSamples.length] = sample;

            if (_sampleRef.compareAndSet(oldSamples, newSamples)) {
                break;
            }
        }
    }

    private void sample() {
        if (_isResinServer) {
            Sample[] samples = _sampleRef.get();
            for (Sample sample : samples) {
                sample.sample();
            }

            double[] values = new double[samples.length];
            for (int i = 0; i < samples.length; i++) {
                values[i] = samples[i].calculate();
            }

            synchronized (_sampleData) {
                for (int i = 0; i < samples.length; i++) {
                    _sampleData.put(samples[i], values[i]);
                }
            }
        }
    }

    @Override
    public void handleAlarm(Alarm alarm) {
        if (_lifecycle.isActive()) {
            if (_servletService.isActive()) {
                sample();
            }
            alarm.queue(_samplePeriod);
        }
    }

    private static class Sample extends StatSystem.Sample implements Comparable<Sample> {
        private final String _exportName;

        Sample(String name, AbstractMeter probe) {
            super(name, probe);
            String metricSuffix;
            if (probe instanceof CountMeter || probe instanceof TotalMeter) {
                metricSuffix = "_total";
            } else {
                metricSuffix = "";
            }
            _exportName = safeMetricName(name) + metricSuffix;
        }

        public String getExportName() {
            return _exportName;
        }

        @Override
        public int compareTo(Sample o) {
            return _exportName.compareTo(o._exportName);
        }
    }

    public String exportMetrics() {
        StringBuilder sb = new StringBuilder();
        synchronized (_sampleData) {
            for (Map.Entry<Sample, Double> entry : _sampleData.entrySet()) {
                Sample sample = entry.getKey();
                Double value = entry.getValue();
                sb.append(sample.getExportName()).append(" ").append(doubleToGoString(value)).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Convert a double to its string representation in Go.
     */
    private static String doubleToGoString(double d) {
        /* SPDX-License-Identifier: Apache-2.0 */
        if (d == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        return Double.toString(d);
    }

    /**
     * Change invalid chars to underscore, and merge underscores.
     *
     * @param name Input string
     */
    private static String safeMetricName(String name) {
        /* SPDX-License-Identifier: Apache-2.0 */
        if (name == null) {
            return null;
        }
        boolean prevCharIsUnderscore = false;
        StringBuilder safeNameBuilder = new StringBuilder(name.length());
        if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
            // prevent a numeric prefix.
            safeNameBuilder.append("_");
        }
        for (char nameChar : name.toCharArray()) {
            boolean isUnsafeChar = !isLegalCharacter(nameChar);
            if ((isUnsafeChar || nameChar == '_')) {
                if (prevCharIsUnderscore) {
                    continue;
                } else {
                    safeNameBuilder.append("_");
                    prevCharIsUnderscore = true;
                }
            } else if (nameChar >= 'A' && nameChar <= 'Z') {
                safeNameBuilder.append(Character.toLowerCase(nameChar));
                prevCharIsUnderscore = false;
            } else {
                safeNameBuilder.append(nameChar);
                prevCharIsUnderscore = false;
            }
        }

        return safeNameBuilder.toString();
    }

    private static boolean isLegalCharacter(char input) {
        /* SPDX-License-Identifier: Apache-2.0 */
        return ((input == ':') ||
                (input == '_') ||
                (input >= 'a' && input <= 'z') ||
                (input >= 'A' && input <= 'Z') ||
                (input >= '0' && input <= '9'));
    }
}
