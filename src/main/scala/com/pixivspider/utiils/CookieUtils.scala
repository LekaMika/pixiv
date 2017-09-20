package com.pixivspider.utiils

import java.io.PrintWriter

import scala.io.Source

object CookieUtils {

  private lazy val cookiePath = this.getClass.getResource("/").toString.substring(6).concat("cookie")

  private var cookie = ""

  def getCookie(): String ={
    if(cookie.isEmpty){
      cookie = Source.fromFile(cookiePath).mkString
    }
    cookie
  }

  def saveCookie(cookies: String): Unit = {
    val cookieWriter = new PrintWriter(CookieUtils.cookiePath)
    cookie = cookies
    cookieWriter.write(cookies)
    cookieWriter.close()
  }
}
