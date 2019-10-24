/*
 * Copyright (c) 1998-2018 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Sam
 */

package com.caucho.rewrite;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import com.caucho.config.Configurable;
import com.caucho.config.types.Period;
import com.caucho.util.CurrentTime;

@Configurable
public class IfThrottle implements RequestPredicate
{
  private static final long DAY = 24 * 3600 * 1000L;
  
  private Map<String,Long> _map = new ConcurrentHashMap<String,Long>();
  
  private long _period = 60 * 1000;
  
  private long _lastClearTime;

  public void setPeriod(Period period)
  {
    _period = period.getPeriod();
  }

  @Override
  public boolean isMatch(HttpServletRequest request)
  {
    String remoteAddr = request.getRemoteAddr();

    if (remoteAddr == null) {
      return true;
    }
    
    long now = CurrentTime.getCurrentTime();
    
    Long oldTime = _map.get(remoteAddr);
    
    boolean isThrottle = false;
    
    if (oldTime != null && now - oldTime < _period) {
      isThrottle = true;
    }
    
    if (now - _lastClearTime < DAY) {
      _map.clear();
      _lastClearTime = now;
    }
    
    _map.put(remoteAddr, now);
    
    return isThrottle;
  }
}
