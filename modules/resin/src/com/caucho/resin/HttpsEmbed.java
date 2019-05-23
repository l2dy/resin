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

package com.caucho.resin;

import com.caucho.cloud.network.NetworkListenSystem;
import com.caucho.config.ConfigException;
import com.caucho.env.service.ResinSystem;
import com.caucho.network.listen.TcpPort;
import com.caucho.server.cluster.ServletService;
import com.caucho.server.http.HttpProtocol;
import com.caucho.vfs.JsseSSLFactory;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;

/**
 * Embeddable version of a HTTP port
 */
public class HttpsEmbed extends PortEmbed
{
  private TcpPort _port;
  private String _keyStoreType;
  private Path _keyStoreFile;
  private String _alias;
  private String _password;
  
  /**
   * Creates a new HttpEmbed configuration.
   */
  public HttpsEmbed()
  {
  }
  
  /**
   * Creates a new HttpEmbed configuration with a specified port.
   *
   * @param port the TCP port of the embedded HTTP port.
   */
  public HttpsEmbed(int port)
  {
    setPort(port);
  }
  
  /**
   * Creates a new HttpEmbed configuration with a specified port.
   *
   * @param port the TCP port of the embedded HTTP port.
   * @param address the TCP IP address of the embedded HTTP port.
   */
  public HttpsEmbed(int port, String ipAddress)
  {
    setPort(port);
    setAddress(ipAddress);
  }

  /**
   * Returns the local, bound port
   */
  @Override
  public int getLocalPort()
  {
    if (_port != null)
      return _port.getLocalPort();
    else
      return getPort();
  }
  
  public HttpsEmbed setKeyStoreFile(String path)
  {
    _keyStoreFile = Vfs.lookup(path);
    
    return this;
  }
  
  public HttpsEmbed setAlias(String alias)
  {
    _alias = alias;
    
    return this;
  }
  
  public HttpsEmbed setPassword(String password)
  {
    _password = password;
    
    return this;
  }
  
  public HttpsEmbed setKeyStoreType(String keyStoreType)
  {
    _keyStoreType = keyStoreType;
    
    return this;
  }
  
  /**
   * Binds the port to the server
   */
  @Override
  public void bindTo(ServletService server)
  {
    try {
      _port = new TcpPort();
      
      _port.setProtocol(new HttpProtocol());

      _port.setPort(getPort());
      _port.setAddress(getAddress());
      
      JsseSSLFactory jsse = _port.createJsse();
      
      jsse.setKeyStoreFile(_keyStoreFile);
      
      if (_keyStoreType != null) {
        jsse.setKeyStoreType(_keyStoreType);
      }
      
      jsse.setAlias(_alias);
      jsse.setPassword(_password);
      
      _port.setJsseSsl(jsse);
       
      _port.init();
      
      ResinSystem system = server.getResinSystem();
      NetworkListenSystem listenService 
        = system.getService(NetworkListenSystem.class);
      
      listenService.addListener(_port);
      
      // server.addPort(_port);
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }
}
