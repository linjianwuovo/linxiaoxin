package com.linxin.navigation

enum class ShortcutTarget(val action: String) {
    SCAN_CHECKIN("com.linxin.action.SCAN_CHECKIN"),
    DORM_CHECKIN("com.linxin.action.DORM_CHECKIN"),
    ;

    companion object {
        fun fromAction(action: String?): ShortcutTarget? = entries.firstOrNull { it.action == action }
    }
}
