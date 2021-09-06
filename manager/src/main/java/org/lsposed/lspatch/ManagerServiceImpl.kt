package org.lsposed.lspatch

import org.lsposed.lspatch.manager.IManagerService
import org.lsposed.lspd.models.Module

class ManagerServiceImpl : IManagerService.Stub() {
    override fun getModules(): List<Module> {
        return ModuleProvider.allModules
    }
}