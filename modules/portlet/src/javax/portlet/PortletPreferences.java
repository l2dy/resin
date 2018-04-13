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
 * @author Sam 
 */


package javax.portlet;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

public interface PortletPreferences
{
  public boolean isReadOnly(String key);

  public String getValue(String key, String def);

  public String[] getValues(String key, String[] def);

  public void setValue(String key, String value)  
    throws ReadOnlyException;

  public void setValues(String key, String[] values) 
    throws ReadOnlyException;

  public Enumeration getNames();

  public Map getMap();

  public void reset(String key) 
    throws ReadOnlyException;

  public void store() 
    throws IOException, ValidatorException;
}
