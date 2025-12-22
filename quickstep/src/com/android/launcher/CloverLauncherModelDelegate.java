package com.android.launcher;

import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceManager;
import android.app.smartspace.SmartspaceSession;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.QuickstepModelDelegate;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.PackageManagerHelper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

public class CloverLauncherModelDelegate extends QuickstepModelDelegate
        implements SmartspaceSession.OnTargetsAvailableListener {

    public static final String TAG = "CloverLauncherModelDelegate";

    public static final int SMARTSPACE_CONTAINER_ID = -110; 

    private final Context mContext;
    
    private final Deque<List<SmartspaceTarget>> mSmartspaceTargets = new LinkedList<>();

    private SmartspaceSession mSmartspaceSession;

    @Inject
    public CloverLauncherModelDelegate(@ApplicationContext Context context,
            InvariantDeviceProfile idp,
            PackageManagerHelper pmHelper,
            @Nullable @Named("ICONS_DB") String dbFileName) {
        super(context, idp, pmHelper, dbFileName);
        mContext = context;
    }

    @Override
    public void destroy() {
        super.destroy();
        destroySmartspaceSession();
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println(prefix + "Recent BC Smartspace Targets (most recent first)");
        synchronized (mSmartspaceTargets) {
            if (mSmartspaceTargets.isEmpty()) {
                writer.println(prefix + "   No data\n");
                return;
            }
            mSmartspaceTargets.descendingIterator().forEachRemaining((targets) -> {
                writer.println(prefix + "   Number of targets: " + targets.size());
                for (SmartspaceTarget target : targets) {
                    writer.println(prefix + "      " + target);
                }
                writer.println();
            });
        }
    }

    private void destroySmartspaceSession() {
        if (mSmartspaceSession != null) {
            mSmartspaceSession.close();
            mSmartspaceSession = null;
        }
    }

    @Override
    public void onTargetsAvailable(List<SmartspaceTarget> targets) {
        List<SmartspaceTarget> list = targets.stream()
                .filter(t -> t.getFeatureType() != 34)
                .collect(Collectors.toList());

        synchronized (mSmartspaceTargets) {
            mSmartspaceTargets.offerLast(list);
            if (mSmartspaceTargets.size() > 5) {
                mSmartspaceTargets.pollFirst();
            }
        }

        mModel.enqueueModelUpdateTask((taskController, dataModel, apps) -> {
            List<ItemInfo> items = new ArrayList<>(list.size());
            
            for (SmartspaceTarget target : list) {
                SmartspaceItem item = new SmartspaceItem();
                item.setSmartspaceTarget(target);
                item.container = SMARTSPACE_CONTAINER_ID;
                item.itemType = 8;
                items.add(item);
            }
            
            BgDataModel.FixedContainerItems container = 
                new BgDataModel.FixedContainerItems(SMARTSPACE_CONTAINER_ID, items);
            taskController.bindExtraContainerItems(container);
        });
    }

    public void notifySmartspaceEvent(SmartspaceTargetEvent event) {
        mModel.enqueueModelUpdateTask((taskController, dataModel, apps) -> {
            if (mSmartspaceSession != null) {
                mSmartspaceSession.notifySmartspaceEvent(event);
            }
        });
    }

    @Override
    public void validateData() {
        super.validateData();
        if (mSmartspaceSession != null) {
            mSmartspaceSession.requestSmartspaceUpdate();
        }
    }

    @Override
    public void workspaceLoadComplete() {
        super.workspaceLoadComplete();
        destroySmartspaceSession();
        if (!mActive) {
            return;
        }
        Log.d(TAG, "Starting smartspace session for home");

        SmartspaceManager smartspaceManager = mContext.getSystemService(SmartspaceManager.class);
        if (smartspaceManager != null) {
            mSmartspaceSession = smartspaceManager.createSmartspaceSession(
                    new SmartspaceConfig.Builder(mContext, "home").build());
            mSmartspaceSession.addOnTargetsAvailableListener(Executors.MODEL_EXECUTOR, this);
            mSmartspaceSession.requestSmartspaceUpdate();
        } else {
            Log.e(TAG, "SmartspaceManager is null, cannot create SmartspaceSession");
        }
    }

    public static class SmartspaceItem extends ItemInfo {
        public SmartspaceTarget mSmartspaceTarget;

        public SmartspaceTarget getSmartspaceTarget() {
            return mSmartspaceTarget;
        }

        public void setSmartspaceTarget(SmartspaceTarget target) {
            mSmartspaceTarget = target;
        }
    }
}
