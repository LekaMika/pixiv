package com.pixivspider.utiils

import redis.clients.jedis.{JedisPool, JedisPoolConfig}

import scala.collection.JavaConverters._

object RedisUtils {

  private var config: JedisPoolConfig =_
  private var pool: JedisPool = _

  private def getConfig(): JedisPoolConfig = {
    if(config == null){
      config = new JedisPoolConfig
      config.setMaxIdle(10)
      config.setMaxTotal(18)
    }
    config
  }

  private def getPool(): JedisPool = {
    if(pool == null){
      pool = new JedisPool(getConfig(), "127.0.0.1", 6379)
    }
    pool
  }

  def get(key: String): String = {
    val client = getPool().getResource
    val value = client.get(key)
    client.close()
    value
  }

  def set(key: String, value: String): Unit = {
    val client = getPool().getResource
    client.set(key, value)
    client.close()
  }

  def keys(): scala.collection.mutable.Set[String] = {
    val client = getPool().getResource
    val set = client.keys("*")
    client.close()
    set.asScala
  }

}
