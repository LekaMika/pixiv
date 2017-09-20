package com.pixivspider.utiils

import java.io.{File, FileOutputStream, IOException}
import java.util.concurrent.TimeUnit

import okhttp3._

import scala.util.matching.Regex

object OkHttpUtils {

  private lazy val client: OkHttpClient = (new OkHttpClient.Builder)
      .followRedirects(false).followSslRedirects(false)
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .build()

  private lazy val dispatcher = client.dispatcher()
  dispatcher.setMaxRequests(100)
  dispatcher.setMaxRequestsPerHost(100)

  private val filePath = "D:\\User\\Pictures\\"
  private val downFileRegex = new Regex("[\\s\\\\/:\\*\\?\\\"<>\\|]")
  private val userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Safari/537.36"
  private val referer = "https://accounts.pixiv.net/login?lang=zh&source=pc&view_type=page&ref=wwwtop_accounts_index"

  /**
    * 获取request的builder对象
    * @return
    */
  private def getRequestBuilder(): Request.Builder ={
    (new Request.Builder)
      .cacheControl(CacheControl.FORCE_NETWORK)
      .addHeader("User-Agent", userAgent)
      .addHeader("Referer", "https://www.pixiv.net")
      .addHeader("Accept-Language", "zh-CN,zh;q=0.8")
      .addHeader("Connection", "keep-alive")
      .addHeader("Cookie", CookieUtils.getCookie())
  }

  /**
    * 执行登录操作
    * @param url
    * @param cookie
    * @param params
    * @return
    */
  def login(url: String, cookie: String, params: Map[String, String]): Response = {
    var formBuild = new FormBody.Builder
    params.foreach(param => formBuild.add(param._1, param._2))
    val formBody = formBuild.build()
    val request = (new Request.Builder)
      .addHeader("User-Agent", userAgent)
      .addHeader("Referer", referer)
      .addHeader("Cookie", cookie)
      .post(formBody).url(url).build()
    val response = client.newCall(request).execute()
    if(!response.isSuccessful){
      response.close()
      login(url, cookie, params)
    }
    response
  }

  /**
    * 获取登录页面
    * @param url
    * @return
    */
  def loginPage(url: String): Response = {
    val request = (new Request.Builder)
      .addHeader("User-Agent", userAgent)
      .addHeader("Referer", referer)
      .get().url(url).build()
    val response = client.newCall(request).execute()
    if(!response.isSuccessful){
      response.close()
      loginPage(url)
    }
    response
  }

  /**
    * 同步get请求获取html
    * @param url
    * @return
    */
  def get(url: String): String = {
    val response = getResponse(url)
    if (response.isSuccessful) {
      response.body().string()
    } else {
      response.close()
      get(url)
    }
  }

  /**
    * 带传参的同步get请求获取html
    * @param url
    * @param params
    * @return
    */
  def get(url: String, params: Map[String, String]): String = {
    var paramUrl = "&"
    params.foreach(map => paramUrl += map._1 + "=" + map._2 + "&")
    val request = getRequestBuilder.url(url +paramUrl).get().build()
    val response = client.newCall(request).execute()
    if (response.isSuccessful) {
      response.body().string()
    } else {
      response.close()
      get(url, params)
    }
  }

  /**
    * 同步获取请求获取返回的response
    * @param url
    * @return
    */
  def getResponse(url: String): Response = {
    val request = getRequestBuilder.url(url).build()
    client.newCall(request).execute()
  }

  /**
    * 异步get请求
    * @param url
    * @param callback 回调函数
    */
  def get(url: String, callback: OkHttpCallback): Unit = {
    val request = getRequestBuilder.url(url).build()
    client.newCall(request).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = {
        e.printStackTrace()
        call.cancel()
        get(url, callback)
      }
      override def onResponse(call: Call, response: Response): Unit = {
        callback.callBack(response)
        response.close()
      }
    })
  }

  /**
    * 下载
    * @param name
    * @param url
    * @param callback
    */
  private def download(name: String, url: String, callback: OkHttpCallback): Unit = {
    val request = getRequestBuilder.url(url).build()
    client.newCall(request).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = {
        e.printStackTrace()
        call.cancel()
        download(name, url, callback)
      }
      override def onResponse(call: Call, response: Response): Unit = {
        callback.callBack(response)
      }
    })
  }

  /**
    * 直接把数据转储到腾讯云cos中
    * @param name
    * @param url
    * @param namespace 文件夹名
    */
  def downloadToCOS(name: String, url: String, namespace: String): Unit = {
    download(name, url, (response: Response) => {
      if (response.isSuccessful) {
        println("开始转储 " + name + " " + url)
        val suffix = url.substring(url.lastIndexOf("."))
        val bytes = response.body().bytes()
        response.close()
        val result = QCOSUtils.uploadByBytes(namespace + "/" + name + suffix, bytes)
        println("转储完成 " + name)
        println(result)
      } else {
        response.close()
        println(name + " " + response.code())
      }
    })
  }

  /**
    * 下载到本机硬盘
    * @param id
    * @param name
    * @param url
    * @param namespace 文件夹名
    */
  def downloadToLocal(id: String, name: String, url: String, namespace: String): Unit = {
//    val value = RedisUtils.get("local_" + id)
//    if(value != null && !value.isEmpty){
//      println("该图片已下载过")
//      return
//    }
    val suffix = url.substring(url.lastIndexOf("."))
    val fixName = downFileRegex.replaceAllIn(name, "")
    val file = new File(filePath + namespace + "\\" + fixName + suffix)
    if(file.exists()){
      println(name + "已下载过")
      return
    }
    download(name, url, (response: Response) => {
      if (response.isSuccessful) {
        println("开始下载 " + name + " " + url)
        val bytes = response.body().bytes()
        response.close()
        if(!file.getParentFile.isDirectory){
          file.getParentFile.mkdirs()
        }
        val out = new FileOutputStream(file)
        out.write(bytes)
        out.flush()
        out.close()
        println("下载完成 " + name)
//        RedisUtils.set("local_" + id, url)
      } else {
        response.close()
        println(name + " " + response.code())
      }
    })
  }
  trait OkHttpCallback{
    def callBack(response: Response)
  }
}

