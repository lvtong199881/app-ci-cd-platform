package com.mohanlv.cicd.github

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

@Service
class GitHubAppService(
    @Value("\${github.app.id}") private val appId: String,
    @Value("\${github.app.private-key}") private val privateKeyPem: String
) {
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    init {
        try {
            val trustAll = arrayOf<TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, java.security.SecureRandom())
            SSLContext.setDefault(sslContext)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun generateJwtToken(): String {
        val now = System.currentTimeMillis() / 1000
        val header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
        val payload = "{\"iat\":$now,\"exp\":${now + 600},\"iss\":$appId}"

        val headerEncoded = Base64.getEncoder().withoutPadding().encodeToString(header.toByteArray())
        val payloadEncoded = Base64.getEncoder().withoutPadding().encodeToString(payload.toByteArray())
        val dataToSign = "$headerEncoded.$payloadEncoded"

        val signature = sign(dataToSign)
        return "$headerEncoded.$payloadEncoded.$signature"
    }

    private fun sign(data: String): String {
        val privateKey = parsePrivateKey(privateKeyPem)
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(privateKey)
        sig.update(data.toByteArray())
        val signatureBytes = sig.sign()
        return Base64.getEncoder().withoutPadding().encodeToString(signatureBytes)
    }

    private fun parsePrivateKey(pem: String): PrivateKey {
        val cleaned = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s+".toRegex(), "")
            .trim()
        val decoded = Base64.getDecoder().decode(cleaned)
        val keySpec = PKCS8EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    fun getInstallationToken(installationId: String): String {
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

        cachedToken = token
        tokenExpiry = java.time.Instant.parse(expiresAt).epochSecond

        return token
    }

    fun getInstallationIdByRepo(repoUrl: String): String {
        val jwt = generateJwtToken()
        val url = URL("https://api.github.com/app/installations")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $jwt")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("获取安装列表失败: $responseCode")
        }

        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()

        val json = objectMapper.readTree(response)
        val repoRegex = Regex("github\\.com[/:]([^/]+)/([^/]+?)(?:\\.git)?$")
        val match = repoRegex.find(repoUrl) ?: throw IllegalArgumentException("无效的仓库地址")
        val owner = match.groupValues[1]

        json.forEach { node ->
            val account = node.get("account")
            val login = account?.get("login")?.asText()
            if (login == owner) {
                return node.get("id").asText()
            }
        }
        throw IllegalStateException("未找到该仓库的 GitHub App 安装: $repoUrl")
    }
}