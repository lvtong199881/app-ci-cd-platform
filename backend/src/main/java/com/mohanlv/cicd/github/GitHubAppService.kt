package com.mohanlv.cicd.github

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

@Service
class GitHubAppService(
    @Value("\${github.app.id}") private val appId: String,
    @Value("\${github.app.private-key}") private val privateKeyPem: String
) {
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    fun generateJwtToken(): String {
        val now = System.currentTimeMillis() / 1000
        val header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
        val payload = "{\"iat\":$now,\"exp\":${now + 3600},\"iss\":$appId}"

        val headerEncoded = Base64.getEncoder().withoutPadding().encodeToString(header.toByteArray())
        val payloadEncoded = Base64.getEncoder().withoutPadding().encodeToString(payload.toByteArray())
        val dataToSign = "$headerEncoded.$payloadEncoded"

        val signature = sign(dataToSign)
        return "$headerEncoded.$payloadEncoded.$signature"
    }

    private fun sign(data: String): String {
        val privateKey = parsePrivateKey(privateKeyPem)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        val signatureBytes = cipher.doFinal(data.toByteArray())
        return Base64.getEncoder().withoutPadding().encodeToString(signatureBytes)
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val keyBytes = pem
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
        val decoded = Base64.getDecoder().decode(keyBytes)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    fun getInstallationToken(installationId: String): String {
        // 如果缓存的 token 还有效，直接返回
        if (cachedToken != null && System.currentTimeMillis() / 1000 < tokenExpiry - 60) {
            return cachedToken!!
        }

        val jwt = generateJwtToken()
        val url = URL("https://api.github.com/app/installations/$installationId/access_tokens")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $jwt")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw IllegalStateException("获取安装 Token 失败: $responseCode, $error")
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = objectMapper.readTree(response)
        val token = json.get("token").asText()
        val expiresAt = json.get("expires_at").asText()

        // 缓存 token，提前 60 秒过期
        cachedToken = token
        tokenExpiry = java.time.Instant.parse(expiresAt).epochSecond

        return token
    }
}