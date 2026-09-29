package com.linxin.core.auth

interface TokenRefresher {
    suspend fun refreshToken(): Boolean
}
