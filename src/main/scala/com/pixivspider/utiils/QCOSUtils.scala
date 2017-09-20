package com.pixivspider.utiils

import java.io.InputStream
import java.util.Properties

import com.qcloud.cos.request.UploadFileRequest
import com.qcloud.cos.sign.Credentials
import com.qcloud.cos.{COSClient, ClientConfig}

object QCOSUtils {

  private val stream: InputStream = this.getClass.getResourceAsStream("/cos")
  private val properties = new Properties
  properties.load(stream)
  private val appId: Long = properties.getProperty("appId").toLong
  private val url: String = properties.getProperty("url")
  private val secretId: String = properties.getProperty("secretId")
  private val secretKey: String = properties.getProperty("secretKey")
  private val bucketName: String = properties.getProperty("bucketName")

  var credentials: Credentials = _
  var config: ClientConfig = _
  private var client: COSClient = _

  private def getConfig(): ClientConfig = {
    if(config == null){
      config = new ClientConfig
      config.setRegion("hk")
    }
    config
  }

  private def getCredentials(): Credentials = {
    if(credentials == null){
      credentials = new Credentials(appId, secretId, secretKey)
    }
    credentials
  }

  private def getClient(): COSClient = {
    if(client == null){
      client = new COSClient(getConfig(), getCredentials())
    }
    client
  }

  def uploadByFile(path: String): String = {
    if(path == null || path.isEmpty){
      "文件路径为空"
    }else {
      val picName = path.substring(path.lastIndexOf("\\\\\\\\") + 1)
      val uploadFileRequest = new UploadFileRequest(bucketName, "/" + picName, path)
      getClient().uploadFile(uploadFileRequest)
    }
  }

  def uploadByBytes(name: String, bytes: Array[Byte]): String = {
    if(name == null || name.isEmpty || bytes == null || bytes.isEmpty){
      "文件名为空或文件字节流数据为空"
    }else{
      val uploadFileRequest = new UploadFileRequest(bucketName, "/" + name, bytes)
      getClient().uploadFile(uploadFileRequest)
    }
  }
}
