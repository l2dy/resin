/*
 * Copyright (c) 1998-2018 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.admin;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.ejb.Startup;
import javax.inject.Singleton;

import com.caucho.lifecycle.Lifecycle;
import com.caucho.loader.EnvironmentLocal;
import com.caucho.server.admin.AdminService;
import com.caucho.server.admin.DeployService;
import com.caucho.server.admin.ManagerService;

/**
 * Convenience collection of the standard administration services.
 */
@Startup
@Singleton
public class AdminServices
{
  private static final Logger log
    = Logger.getLogger(AdminServices.class.getName());
  
  private static final EnvironmentLocal<AdminServices> _localManager
    = new EnvironmentLocal<AdminServices>(AdminService.class.getName());
  
  private Lifecycle _lifecycle = new Lifecycle();
  
  @PostConstruct
  public void init()
  {
    if (_localManager.get() == null) {
      _localManager.set(this);
    }
    
    AdminServices services = _localManager.get();
    
    if (services != this) {
      return;
    }
    
    if (! _lifecycle.toInit()) {
      return;
    }
    
    try {
      Class<?> proCl = Class.forName("com.caucho.admin.ProAdminServices");
      
      services = (AdminServices) proCl.newInstance();
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    services.initImpl();
  }
  
  protected void initImpl()
  {
    DeployService deployService = new DeployService();
    deployService.init();

    ManagerService managerService = new ManagerService();
    managerService.init();
  }
}
