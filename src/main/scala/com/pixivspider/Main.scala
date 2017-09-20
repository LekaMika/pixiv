package com.pixivspider

object Main extends App{

  //val proxy: Map[String, Int] = Spider.searchProxy()
  //OkHttpUtils.proxyMap = proxy
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run(): Unit = {
      println("全部下载完毕")
    }
  })
  Spider.searchByKey("うらら迷路帖 100users入り", 1, 100)
  //Spider.searchByKey("イリヤスフィール・フォン・アインツベルン 1000users入り", 1, 10)
  //Spider.searchByArtistId("2600911", 1, 9999)
  //Spider.searchByRankNo("6")
  //Spider.searchByRankDaily()
  //Spider.searchByIllustId("64975522")
}
