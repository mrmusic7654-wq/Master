package com.mastercontrol.app.telegram.repository

import com.mastercontrol.app.domain.model.AuthorizationState
import com.mastercontrol.app.domain.model.ConnectionState
import com.mastercontrol.app.domain.model.TelegramAccountInfo
import com.mastercontrol.app.domain.repository.TelegramAccountRepository
import com.mastercontrol.app.telegram.tdlib.json.TdRequests
import com.mastercontrol.app.telegram.tdlib.manager.TdLibManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class TelegramAccountRepositoryImpl @Inject constructor(
    private val manager: TdLibManager,
) : TelegramAccountRepository {

    override fun observeAuthorizationState(): Flow<AuthorizationState> =
        manager.authManager.authorizationState

    override fun observeConnectionState(): Flow<ConnectionState> =
        manager.authManager.connectionState

    override suspend fun start() {
        manager.start()
    }

    override suspend fun restart() {
        manager.restart()
    }

    override suspend fun submitPhoneNumber(phoneNumber: String) =
        manager.authManager.submitPhoneNumber(phoneNumber)

    override suspend fun submitAuthenticationCode(code: String) =
        manager.authManager.submitCode(code)

    override suspend fun submitTwoFactorPassword(password: String) =
        manager.authManager.submitPassword(password)

    override suspend fun resendCode() = manager.authManager.resendCode()

    override suspend fun logout() = manager.authManager.logout()

    override suspend fun getAccountInfo(): TelegramAccountInfo? =
        manager.authManager.accountInfo()

    override suspend fun getTdlibVersion(): String {
        val option = manager.client.call(TdRequests.getOption("version"))
        val value = option["value"]?.jsonPrimitive?.contentOrNull ?: return "unknown"
        return value
    }
}
