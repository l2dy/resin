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

package com.caucho.boot;

import com.caucho.config.annotation.NoAspect;
import com.caucho.config.program.ConfigProgram;
import com.caucho.config.program.ContainerProgram;

@NoAspect
public class BootClusterProxy {
  private String _id = "";
  
  private ContainerProgram _program = new ContainerProgram();

  public void setId(String id)
  {
    _id = id;
  }

  public String getId()
  {
    return _id;
  }
  
  /**
   * Ignore items we can't understand.
   */
  public void addContentProgram(ConfigProgram program)
  {
    _program.addProgram(program);
  }
  
  public ConfigProgram getProgram()
  {
    return _program;
  }
}
