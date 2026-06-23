package com.example.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {
    private val cookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            cookieManager.setCookie(urlString, cookie.toString())
        }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val urlString = url.toString()
        val cookiesString = cookieManager.getCookie(urlString)
        
        if (cookiesString != null && cookiesString.isNotEmpty()) {
            val cookieHeaders = cookiesString.split(";")
            val cookies = mutableListOf<Cookie>()
            for (header in cookieHeaders) {
                val parsedCookie = Cookie.parse(url, header.trim())
                if (parsedCookie != null) {
                    cookies.add(parsedCookie)
                }
            }
            return cookies
        }
        
        return emptyList()
    }
}
