package org.lsposed.lspatch.service;

import static org.lsposed.lspatch.share.Constants.MANAGER_PACKAGE_NAME;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoteApplicationService implements ILSPApplicationService {

    private static final Uri PROVIDER = Uri.parse("content://" + MANAGER_PACKAGE_NAME + ".provider");

    private ILSPApplicationService service;

    public RemoteApplicationService(Context context) {
        try {
            Bundle back = context.getContentResolver().call(PROVIDER, "getBinder", null, null);
            service = ILSPApplicationService.Stub.asInterface(back.getBinder("binder"));
            if (service == null) throw new RemoteException("Binder is null");
        } catch (Throwable e) {
            Log.e("LSPatch", "Error when initializing RemoteApplicationServiceClient", e);
        }
    }

    @Override
    public IBinder requestModuleBinder(String name) {
        return service == null ? null : service.asBinder();
    }

    @Override
    public List<Module> getModulesList() throws RemoteException {
        return service == null ? new ArrayList<>() : service.getModulesList();
    }

    @Override
    public String getPrefsPath(String packageName) {
        return new File(Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/").getAbsolutePath();
    }

    @Override
    public Bundle requestRemotePreference(String packageName, int userId, IBinder callback) throws RemoteException {
        return service == null ? null : service.requestRemotePreference(packageName, userId, callback);
    }

    @Override
    public IBinder asBinder() {
        return service == null ? null : service.asBinder();
    }

    @Override
    public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) {
        return null;
    }
}
