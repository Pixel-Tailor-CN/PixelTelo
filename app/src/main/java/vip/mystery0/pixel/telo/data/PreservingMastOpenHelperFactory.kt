package vip.mystery0.pixel.telo.data

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * 所有号码库的 Room 打开入口都保留原文件，损坏时交由仓库显式修复。
 *
 * onCorruption 默认删库与 allowDataLossOnRecovery 的重建恢复是两条独立路径，必须同时关闭。
 * 原 Room 回调继续负责配置、建表、升级、降级和 schema 校验，不能用空回调替换。
 * 候选库也使用此边界，失败后仅由安装器清理它自己的临时文件。
 */
internal object PreservingMastOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    private val delegate = FrameworkSQLiteOpenHelperFactory()

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val original = configuration.callback
        val preservingCallback = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = original.onConfigure(db)

            override fun onCreate(db: SupportSQLiteDatabase) = original.onCreate(db)

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onUpgrade(db, oldVersion, newVersion)

            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                original.onDowngrade(db, oldVersion, newVersion)

            override fun onOpen(db: SupportSQLiteDatabase) = original.onOpen(db)

            override fun onCorruption(db: SupportSQLiteDatabase) {
                // 不调用默认实现，不删除主文件、附加数据库或 WAL，查询异常仍由仓库处理。
                Log.w("PreservingMastOpenHelper", "Database corruption detected; preserving files")
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(preservingCallback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build(),
        )
    }
}
