# Room 通过生成代码访问实体构造，release 混淆时保留
-keep class com.cyberscale.app.data.** { *; }
# BLE 广播回调由系统跨进程回调
-keep class com.cyberscale.app.ble.** { *; }
