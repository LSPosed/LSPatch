package org.lsposed.lspatch.service;

import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.util.ArrayList;
import java.util.List;

public class NullApplicationService extends ILSPApplicationService.Stub {
    @Override
    public IBinder requestModuleBinder(String name) {
        return null;
    }

    @Override
    public List<Module> getModulesList() {
        return new ArrayList<>();
    }

    @Override
    public String getPrefsPath(String packageName) {
        return null;
    }

    @Override
    public Bundle requestRemotePreference(String packageName, int userId, IBinder callback) {
        return null;
    }

    @Override
    public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) {
        return null;
    }
}
