package com.yupi.yupicturebackend.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;


@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {
    @Resource
    private CosManager cosManager;
    /**
     *
     * @param multipartFile
     * @return
     */
    @PostMapping("/test/upload")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<String> testUploadFile(@RequestPart("file")MultipartFile multipartFile) {
        String filename = multipartFile.getOriginalFilename();
        String filePath = "/test/" + filename;
        File file = null;
        try{
            file = File.createTempFile(filePath, null);
            multipartFile.transferTo(file);
            cosManager.putObject(filePath, file);
            //返回可访问的地址
            return ResultUtils.success(filePath);
        } catch (Exception e) {
            log.error("file upload error, filePath = "+ filePath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }finally {
            if (file != null) {
                boolean delete = file.delete();
                if(!delete){
                    log.error("file delete error, filePath = "+ filePath);
                }
            }
        }
    }

    /**
     * 文件下载
     * @param filePath
     * @param response
     */
    @GetMapping("/test/download")
    @AuthCheck(mustRole = "admin")
    public void testDownloadFile(String filePath, HttpServletResponse response) throws IOException {
        COSObjectInputStream objectInputStream = null;
        try{
            COSObject cosObject = cosManager.getObject(filePath);
            objectInputStream = cosObject.getObjectContent();
            byte[] byteArray = IOUtils.toByteArray(objectInputStream);
            //设置响应头
            response.setContentType("application/octet-stream;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + filePath);
            //写入响应
            response.getOutputStream().write(byteArray);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filePath = "+ filePath,e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        }finally {
            if(objectInputStream != null){
                objectInputStream.close();
            }
        }
    }

}
