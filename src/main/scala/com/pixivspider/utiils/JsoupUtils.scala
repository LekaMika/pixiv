package com.pixivspider.utiils

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.collection.immutable.HashMap

object JsoupUtils {

  private val username = "*************"
  private val password = "*************"

  /**
    * 根据document获取form表单传参，并植入账号密码
    * @param document pixiv登录页面dom
    * @return
    */
  def getFormParam(document: Document): Map[String, String] = {
    val form = document.select("div#old-login > form")
    var params = new HashMap[String, String]
    form.select("input").forEach(e => {
      if(e.attr("name").equals("pixiv_id")){
        e.attr("value", username)
      }
      if(e.attr("name").equals("password")){
        e.attr("value", password)
      }
      params += (e.attr("name") -> e.attr("value"))
    })
    params += ("g_recaptcha_response" -> "")
    params += ("captcha" -> "")
    params
  }

  /**
    * 根据html，并植入账号密码
    * @param html pixiv登录页面html
    * @return
    */
  def getFormParam(html: String): Map[String, String] = {
    val document = Jsoup.parse(html)
    getFormParam(document)
  }

}
