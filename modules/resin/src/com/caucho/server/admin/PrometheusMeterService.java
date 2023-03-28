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

import com.caucho.env.meter.AbstractMeter;
import com.caucho.env.meter.MeterService;
import com.caucho.loader.Environment;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrometheusMeterService extends MeterService implements Closeable {
    private PrometheusStatSystem _service;
    private final List<AbstractMeter> _pendingMeters = new ArrayList<>();

    public PrometheusMeterService() {
        MeterService oldService = getCurrent();
        if (oldService instanceof PrometheusMeterService) {
            _pendingMeters.addAll(((PrometheusMeterService) oldService)._pendingMeters);
        }

        setManager(this);
        Environment.addCloseListener(this);
    }

    public void setService(PrometheusStatSystem service) {
        synchronized (this) {
            if (this._service == null) {
                this._service = service;
                for (AbstractMeter meter : this._pendingMeters) {
                    this._service.addMeter(meter);
                }
                this._pendingMeters.clear();
            }
        }
    }

    public boolean isConfigured() {
        synchronized (this) {
            return this._service != null;
        }
    }

    @Override
    protected void registerMeter(AbstractMeter meter) {
        super.registerMeter(meter);

        synchronized (this) {
            if (this._service != null) {
                this._service.addMeter(meter);
            } else {
                this._pendingMeters.add(meter);
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.setManager(null);
    }

}
