package com.linxin.core.auth

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 登录口令的 RSA。
 *
 * 出处：2026-10-19 从安小信自己的 base.apk（4 个 dex）里扫出来的两把公钥之一，
 * 与它登录时实际发出去的 `password` 字段（128 字节块的 base64）对得上。
 * 上面那两条就是全部依据，别在没有新报文的情况下换钥匙。
 */
object Rsa {

    private const val LOGIN_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC8Zt/Ngbil9NG1Y0/8uZDIL5eR" +
            "WWn3O9zlPBsVwDcgS2lQXNnZ5hl2CryGe9SbG4gUb1EO7cgizzQl8N1yYmVzwO4kc" +
            "yOEtA1HHdBLjC8xvTOGL1G62j6nyOjJE3CMNyCDxEPHSYwuQVMzNko/kg5KyTrNSQ" +
            "t62hwRYbIK+BRwEQIDAQAB"

    /** RSA-1024：单块最多 128-11=117 字节明文 */
    private const val BLOCK = 117

    fun encryptPassword(plain: String): String {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(LOGIN_KEY, Base64.DEFAULT)))
        val cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val data = plain.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            val size = minOf(BLOCK, data.size - offset)
            out.write(cipher.doFinal(data, offset, size))
            offset += size
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
