package org.lsposed.lspatch.manager;

import org.lsposed.lspd.models.Module;

interface IManagerService {
    List<Module> getModules();
}