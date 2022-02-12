package org.lsposed.lspatch.manager

import org.lsposed.lspd.models.Module

class ManagerServiceImpl : IManagerService.Stub() {
    override fun getModules(): List<Module> {
        return ModuleProvider.allModules
    }
}
