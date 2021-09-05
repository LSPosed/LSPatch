package org.lsposed.lspatch.loader;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

import org.lsposed.lspatch.manager.IManagerService;
import org.lsposed.lspd.models.Module;

import java.util.List;

public class ManagerResolver extends ContentResolver {
    private static final String MANAGER_PACKAGE_NAME = "org.lsposed.lspatch";
    private static final Uri BINDER_URI = Uri.parse("content://" + MANAGER_PACKAGE_NAME + "/binder");

    private final IManagerService service;

    public ManagerResolver(Context context) throws RemoteException {
        super(context);
        try {
            Bundle back = call(BINDER_URI, "getBinder", null, null);
            service = IManagerService.Stub.asInterface(back.getBinder("binder"));
        } catch (Throwable t) {
            var e = new RemoteException("Failed to get manager binder");
            e.addSuppressed(t);
            throw e;
        }
    }

    public List<Module> getModules() throws RemoteException {
        return service.getModules();
    }
}
