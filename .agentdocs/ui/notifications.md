# 数据同步与通知策略

## 1. 数据库更新机制

APP 支持从云端下载离线数据库以更新本地 Room 数据库。

## 2. 通知交互规范

### 下载过程中

* **Android 16+**: 使用 `LiveUpdateNotification` 形式展示下载进度（需调研具体 API 或使用最接近的系统特性）。
* **Android <16**: 使用标准的前台服务通知 (Foreground Service Notification) 展示下载进度条。

### 更新完成后

* 发送一条普通通知告知用户更新完成。
* **内容包含**:
    * 新数据库版本号
    * 数据库文件大小

## 3. 实现细节

* 使用 `WorkManager` (推荐 `Expedited Work`) 或前台服务来执行下载任务，确保在后台不被杀掉。
