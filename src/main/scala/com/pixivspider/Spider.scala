package com.pixivspider

import java.text.SimpleDateFormat

import com.pixivspider.utiils.{CookieUtils, JsoupUtils, OkHttpUtils}
import okhttp3.Response
import org.jsoup.Jsoup

import scala.util.control.Breaks
import scala.util.matching.Regex

object Spider {

  private val index = "https://www.pixiv.net"
  private val loginPageUrl = "https://accounts.pixiv.net/login?lang=zh&source=pc&view_type=page&ref=wwwtop_accounts_index"
  private val loginUrl = "https://accounts.pixiv.net/api/login?lang=zh"
  private val userUrl = "https://www.pixiv.net/setting_profile.php"

  private val searchUrl = "https://www.pixiv.net/search.php?word="
  private val searchUrlSuffix = "&p="

  private val search = "https://www.pixiv.net/member_illust.php"

  private val artistMainUrl = search + "?id="

  private val medium = search +"?mode=medium&illust_id="
  private val manage = search + "?mode=manga&illust_id="
  private val manageBig = search + "?mode=manga_big&illust_id="
  private val memberPicUrlSuffix = "&page="

  private val rankUrl = "https://www.pixiv.net/ranking_area.php"
  private val detail = "?type=detail&no="

  private val dailyRankingUrl = "https://www.pixiv.net/ranking.php"
  private val daily = "?mode=daily"
  private val content = "&content="

  private val today = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date())

  private var isLogin: Boolean = false

  /**
    * 测试是否登录，未登录执行登录操作
    */
  private[this] def login(): Unit = {
    if(isLogin == false && !testIsLogin()){
      toLogin()
    }
  }

  /**
    * 访问个人主页测试是否登录
    * @return
    */
  private[this] def testIsLogin(): Boolean = {
    // 访问个人页面，测试是否登录状态
    val response = OkHttpUtils.getResponse(userUrl)
    val successful = response.isSuccessful
    isLogin = successful
    response.close()
    successful
  }

  /**
    * 执行登录
    */
  private[this] def toLogin(): Unit = {
    val regex = new Regex("PHPSESSID=[\\w]*;")
    val pageResponse = OkHttpUtils.loginPage(loginPageUrl)
    var cookieLogin = ""
    pageResponse.headers("Set-Cookie").forEach(cookie => {
      val session = regex.findFirstIn(cookie).getOrElse("")
      if(!session.isEmpty){
        cookieLogin = session
      }
    })
    val params = JsoupUtils.getFormParam(pageResponse.body().string())
    val response = OkHttpUtils.login(loginUrl, cookieLogin, params)
    response.headers("Set-Cookie").forEach(cookie => {
      val sesssid = regex.findFirstIn(cookie).getOrElse("")
      if(!sesssid.isEmpty){
        CookieUtils.saveCookie(sesssid)
        if(!testIsLogin()){
          println("登录失败，10s后重试")
          Thread.sleep(10 * 1000)
          toLogin()
        }
      }
    })
    response.close()
  }

  /**
    * 获取代理
    * @return
    */
  def searchProxy(): Map[String, Int] = {
    val url = "http://www.66ip.cn/nmtq.php?getnum=100&isp=0&anonymoustype=4&start=&ports=&export=&ipaddress=&area=0&proxytype=0&api=66ip"
    val html = OkHttpUtils.get(url)
    val regex = new Regex("[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}.[0-9]{1,3}:[0-9]{0,5}")
    var map: Map[String, Int] = Map.empty
    regex.findAllIn(html).foreach(m => {
      val keyValue = m.split(":")
      map += (keyValue(0) -> keyValue(1).toInt)
    })
    map
  }

  /**
    * 通过搜索关键词进行下载
    * @param key
    * @param start
    * @param end
    */
  def searchByKey(key: String, start: Int, end: Int): Unit = {
    login()
    if(start > end){
      throw new IllegalArgumentException("起始页大于尾页")
    }
    val namespace = key
    val url = searchUrl + key
    val loop = new Breaks
    loop.breakable(
      for (i <- start to end) {
        val urlPage = url + searchUrlSuffix + i
        println("开始搜索第 " + i + " 页")
        searchByKeyUrl(urlPage, namespace)
        val document = Jsoup.parse(OkHttpUtils.get(urlPage))
        if((document.select("span.next").size() == 0 || document.select("span.next").html().isEmpty) && i < end){
          println("总页数小于需求搜索页数，停止搜索")
          loop.break()
        }
        Thread.sleep(10 * 1000)
      }
    )
    println("搜索完毕")
  }

  /**
    * 通过画师id进行下载
    * @param id
    * @param start
    * @param end
    */
  def searchByArtistId(id: String, start: Int, end: Int): Unit = {
    login()
    if(start > end){
      throw new IllegalArgumentException("起始页大于尾页")
    }
    val url = artistMainUrl + id
    val regex = new Regex("「[\\s\\S]*」")
    val title = Jsoup.parse(OkHttpUtils.get(url)).select("title").html()
    val artistName = regex.findFirstIn(title).getOrElse("").replace("「", "").replace("」", "")
    val namespace = artistName
    val loop = new Breaks
    loop.breakable(
      for (i <- start to end) {
        val urlPage = url + searchUrlSuffix + i
        println("开始搜索第 " + i + " 页")
        searchByArtistIdUrl(urlPage, namespace)
        val document = Jsoup.parse(OkHttpUtils.get(urlPage))
        if((document.select("a[rel=next]").size() == 0 || document.select("a[rel=next]").html().isEmpty) && i < end){
          println("总页数小于需求搜索页数，停止搜索")
          loop.break()
        }
        Thread.sleep(10 * 1000)
      }
    )
    println("搜索完毕")
  }

  /**
    * 通过画师主页
    * @param url
    * @param namespace
    */
  private[this] def searchByArtistIdUrl(url: String, namespace: String): Unit = {
    var illustIdList: List[String] = Nil
    OkHttpUtils.get(url, (response: Response) => {
      val html = response.body().string()
      Jsoup.parse(html).select("a.work").eachAttr("href").forEach(url => {
        val id = url.substring(url.lastIndexOf("=") + 1)
        searchByIllustIdUrl(index + url, namespace)
      })
    })
  }

  /**
    * 通过搜索页面
    * @param url
    * @param namespace
    */
  private[this] def searchByKeyUrl(url: String, namespace: String): Unit = {
    var illustIdList: List[String] = Nil
    OkHttpUtils.get(url, (response: Response) => {
      val html = response.body().string()
      val document = Jsoup.parse(html)
      val json = document.select("section.column-search-result > div#js-mount-point-search-result-list").attr("data-items")
      if (json == null || json.length <= 0) {
        searchByKeyUrl(url, namespace)
      }
      val patten = new Regex("\"illustId\":\"[0-9]{0,10}\"")
      patten.findAllIn(json).foreach(m => {
        val illustId = m.split("\":\"")(1).replaceAll("\\\\", "")
        illustIdList = illustIdList ::: List(illustId.substring(0, illustId.length - 1))
      })
      searchByIllustId(illustIdList, namespace)
    })
  }

  /**
    * 排行榜
    * @param no
    */
  def searchByRankNo(no: String): Unit = {
    login()
    val url = rankUrl + detail + no
    val namespace = "排行榜" + no + "\\" + today
    searchByRankUrl(url, namespace)
  }

  /**
    * 每日排行榜，默认页面
    */
  def searchByRankDaily(): Unit = {
    login()
    searchByRankDaily("")
  }

  /**
    * 每日排行榜，带传参contents
    * @param contents
    */
  def searchByRankDaily(contents: String): Unit ={
    login()
    var url = dailyRankingUrl + daily
    if(contents != null && !contents.isEmpty){
      url += content + contents
    }
    val namespace = "每日排行榜" + contents + "\\" + today
    searchByRankUrl(url, namespace)
  }

  /**
    * 通过排行榜页面
    * @param url
    * @param namespace
    */
  private[this] def searchByRankUrl(url: String, namespace: String): Unit = {
    OkHttpUtils.get(url, (response: Response) => {
      val html = response.body().string()
      Jsoup.parse(html).select("a.work").eachAttr("href").forEach(url => {
        val id = url.substring(url.lastIndexOf("=") + 1)
        searchByIllustIdUrl(index + url, namespace)
      })
    })
    println("搜索完毕")
  }

  /**
    * 通过作品id
    * @param id
    * @param namespace
    */
  def searchByIllustId(id: String, namespace: String): Unit = {
    login()
    val url = medium + id
    searchByIllustIdUrl(url, namespace)
  }

  /**
    * 批量作品id
    * @param list
    * @param namespace
    */
  def searchByIllustId(list: List[String], namespace: String): Unit = {
    login()
    list.foreach(id => searchByIllustId(id, namespace))
  }

  /**
    * 作品页面
    * @param url
    * @param namespace
    */
  private[this] def searchByIllustIdUrl(url: String, namespace: String): Unit = {
    OkHttpUtils.get(url, (response: Response) => {
      val id = url.substring(url.lastIndexOf("=") + 1)
      val html = response.body().string()
      val document = Jsoup.parse(html)
      val title = document.select("section.work-info > h1.title").html()
      val img = document.select("img.original-image").attr("data-src")
      if (img == null || img.isEmpty) { //多张图片，不超过3张仅下载第一张，超过不下载
        val menUrl = manage + id
        OkHttpUtils.get(menUrl, (response: Response) => {
          val html = response.body().string()
          val document = Jsoup.parse(html)
          val last = document.select("img[data-filter=manga-image]").last()
          var size = 0
          if (last != null){
            size = last.attr("data-index").toInt + 1
          }
          if(size > 0 && size < 3){
            size = 1
            for (i <- 0 until size) {
              val memPicUrl = manageBig + id + memberPicUrlSuffix + i
              OkHttpUtils.get(memPicUrl, (response: Response) => {
                val html = response.body().string()
                val document = Jsoup.parse(html)
                val memImg = document.select("img").attr("src")
                //OkHttpUtils.downloadToLocal(id , title + "_" + (i+1) + "(" + id + ")", memImg)
                OkHttpUtils.downloadToLocal(id , title + "(" + id + ")", memImg, namespace)
              })
            }
          }
        })
      } else {
        OkHttpUtils.downloadToLocal(id, title + "(" + id + ")", img, namespace)
      }
    })
  }

}
