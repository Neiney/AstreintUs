package com.example.presentation.navigation

sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object MailList : Screen("mail_list")
    data object MailDetail : Screen("mail_detail/{mailId}") {
        fun createRoute(mailId: Long) = "mail_detail/$mailId"
    }
    data object AlertHistory : Screen("alert_history")
    data object Settings : Screen("settings")
    data object AccountConfig : Screen("account_config")
}
