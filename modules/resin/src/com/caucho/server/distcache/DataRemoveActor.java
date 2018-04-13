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
 * @author Scott Ferguson
 */

package com.caucho.server.distcache;

import java.util.concurrent.LinkedBlockingQueue;

import com.caucho.env.service.ResinSystem;
import com.caucho.env.thread.AbstractTaskWorker;
import com.caucho.server.distcache.DataStore.DataItem;
import com.caucho.util.CurrentTime;

/**
 * Manages the distributed cache
 */
public class DataRemoveActor extends AbstractTaskWorker {
  private final DataStore _dataStore;
  private final String _serverId;
  
  private final int _queueMax = 64 * 1024;
  
  private final LinkedBlockingQueue<RemoveItem> _queue
    = new LinkedBlockingQueue<RemoveItem>();
  
  //private final LinkedBlockingQueue<DataItem> _queue
  //  = new LinkedBlockingQueue<DataItem>();
  
  DataRemoveActor(DataStore dataStore)
  {
    _serverId = ResinSystem.getCurrentId();
    _dataStore = dataStore;
  }
  
  public void offer(DataItem dataItem)
  {
    RemoveItem item = new RemoveItem(dataItem);
    
    _queue.offer(item);
    
    wake();
  }

  @Override
  public long runTask()
  {
    RemoveItem item;
    DataStore dataStore = _dataStore;
    
    long now = CurrentTime.getCurrentTime();
    
    while ((item = _queue.peek()) != null) {
      // wait for a short timeout in case data is being serialized
      // across the cluster
      if (now < item.getExpireTime() && _queue.size() < _queueMax) {
        return item.getExpireTime() - now;
      }
      else if (now < item.getShortExpireTime()) {
        return item.getShortExpireTime() - now;
      }
      
      item = _queue.poll();

      if (dataStore.isClosed()) {
        return 0;
      }

      if (item != null) {
        _dataStore.remove(item.getId(), item.getTime());
      }
    }
    
    return 0;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _serverId + "]";
  }
  
  static final class RemoveItem {
    private final DataItem _dataItem;
    private final long _shortExpireTime;
    private final long _expireTime;
    
    RemoveItem(DataItem dataItem)
    {
      long delta = 10 * 1000L;
      
      if (CurrentTime.isTest()) {
        delta = 500L;
      }
      
      _dataItem = dataItem;
      long now = CurrentTime.getCurrentTime();
      
      _expireTime = now + delta;
      _shortExpireTime = now + 1000;
    }
    
    public long getId()
    {
      return _dataItem.getId();
    }
    
    public long getTime()
    {
      return _dataItem.getTime();
    }
    
    public long getExpireTime()
    {
      return _expireTime;
    }
    
    public long getShortExpireTime()
    {
      return _shortExpireTime;
    }
  }
}
