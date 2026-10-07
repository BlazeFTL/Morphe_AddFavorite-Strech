/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/service/RootService.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.service

import android.content.Intent
import android.os.IBinder
import app.morphe.manager.IRootSystemService
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager

class ManagerRootService : RootService() {
    class RootSystemService : IRootSystemService.Stub() {
        override fun getFileSystemService() =
            FileSystemManager.getService()
    }

    override fun onBind(intent: Intent): IBinder = RootSystemService()
}
