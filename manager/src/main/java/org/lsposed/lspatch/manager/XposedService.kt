package org.lsposed.lspatch.manager

import io.github.xposed.xposedservice.IXposedService

class XposedService : IXposedService.Stub() {

    override fun getVersion() = 100
}
