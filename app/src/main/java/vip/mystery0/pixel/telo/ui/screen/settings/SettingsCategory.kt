package vip.mystery0.pixel.telo.ui.screen.settings

import vip.mystery0.pixel.telo.R

/** 设置首页提供的七个分类入口。 */
enum class SettingsCategory(val titleResId: Int) {
    CALL_RECOGNITION(R.string.category_call_recognition),
    ONLINE_QUERY(R.string.category_online_query),
    OFFLINE_DATABASE(R.string.category_offline_database),
    DISPLAY_ALERTS(R.string.category_display_alerts),
    RECORDS_BACKUP(R.string.category_records_backup),
    PERMISSIONS(R.string.category_permissions),
    ABOUT(R.string.category_about),
}
