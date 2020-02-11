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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.server.session;

import javax.servlet.http.Cookie;

import com.caucho.config.ConfigException;
import com.caucho.util.L10N;

/**
 * Extends the cookie.
 */
public class CookieImpl extends Cookie {
  private static final L10N L = new L10N(CookieImpl.class);
  
  // the allowed cookie port
  private String _port;
  
  private SameSite _sameSite = SameSite.UNSET;

  /**
   * Create a new cookie object.
   */
  public CookieImpl(String name, String value)
  {
    super(name, value);
  }

  /**
   * Returns the allowed ports.
   */
  public String getPort()
  {
    return _port;
  }

  /**
   * Sets the allowed ports.
   */
  public void setPort(String port)
  {
    _port = port;
  }
  
  /**
   * Sets the same-site attribute
   */
  public void setSameSite(String value)
  {
    setSameSite(SameSite.parseValue(value));
  }
  
  /**
   * Sets the same-site attribute
   */
  public void setSameSite(SameSite value)
  {
    if (value == null) {
      _sameSite = SameSite.UNSET;
    }
    else {
      _sameSite = value;
    }
  }
  
  public SameSite getSameSite()
  {
    return _sameSite;
  }
  
  
  public enum SameSite {
    UNSET,
    NONE,
    LAX,
    STRICT;
    
    public static SameSite parseValue(String value)
    {
      if (value == null || value.equals("")) {
        return SameSite.UNSET;
      }
      else if (value.equals("Lax")) {
        return SameSite.LAX;
      }
      else if (value.equals("Strict")) {
        return SameSite.STRICT;
      }
      else if (value.equals("None")) {
        return SameSite.NONE;
      }
      else {
        throw new ConfigException(L.l("cookie sameSite requires Lax, Strict, or None."));
      }
    }
  }
}
