package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import io.swagger.annotations.Api;

import org.apache.commons.io.FilenameUtils;
import org.csource.fastdfs.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Api(tags = "文件上传接口")
@RestController
@RequestMapping("admin/product")
public class FileUploadController {

    @Value("${fileServer.url}")
    private String fileUrl;

    @PostMapping("fileUpload")
    public Result fileUpload(MultipartFile file) throws Exception{


        /*
            1.  读取到tracker.conf 文件
            2.  初始化FastDFS
            3.  创建对应的TrackerClient,TrackerServer
            4.  创建一个StorageClient，调用文件上传方法
            5.  获取到文件上传的url 并返回
         */
        String configFile = this.getClass().getResource("/tracker.conf").getFile();

        String path = null;
        if (configFile != null){
            //初始化
            ClientGlobal.init(configFile);
            //
            TrackerClient trackerClient = new TrackerClient();

            TrackerServer trackerServer = trackerClient.getConnection();


            StorageClient1 storageClient1 = new StorageClient1(trackerServer,null);

            path = storageClient1.upload_appender_file1(file.getBytes(), FilenameUtils.getExtension(file.getOriginalFilename()),null);
            //  才是文件上传之后的全路径
            System.out.println("文件上传之后的全路径:\t"+fileUrl + path);
        }
        return Result.ok(fileUrl + path);
    }
}
