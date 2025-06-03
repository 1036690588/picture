package com.yupi.yupicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.*;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.picture.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 文件服务
 * @deprecated 已废弃，改为使用 upload 包的模板方法优化
 */
@Deprecated
@Service
@Slf4j
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     * @param multipartFile
     * @param uploadPathPrefix
     * @return
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix){
        validPicture(multipartFile);
        //图片上传地址
        String uuid = RandomUtil.randomString(16);
        String filename = multipartFile.getOriginalFilename();
        String formatName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,
                FileUtil.getSuffix(filename));
        String uploadPath = String.format("%s/%s",uploadPathPrefix,formatName);
        File tempFile = null;
        //创建临时文件
        try{
            tempFile = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(tempFile);
            //上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(String.format("%s/%s",cosClientConfig.getHost(),uploadPath));
            uploadPictureResult.setPicName(FileUtil.mainName(filename));
            uploadPictureResult.setPicSize(FileUtil.size(tempFile));
            uploadPictureResult.setPicWidth(imageInfo.getWidth());
            uploadPictureResult.setPicHeight(imageInfo.getHeight());
            Double scale = NumberUtil.round(imageInfo.getWidth() * 1.0 / imageInfo.getHeight(), 2).doubleValue();
            uploadPictureResult.setPicScale(scale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            return uploadPictureResult;

        } catch (Exception e) {
            log.error("file upload error",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }finally {
            deleteTempPicture(tempFile);
        }

    }

    public void deleteTempPicture(File file){
        if(file == null){
            return ;
        }
        boolean delete = file.delete();
        if(!delete){
            log.error("file delete error, filePath = "+ file.getPath());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件删除失败");
        }
    }

    /**
     * 通过url上传图片
     * @param fileUrl
     * @param uploadPathPrefix
     * @return
     */
    public UploadPictureResult uploadPictureByUrl(String fileUrl, String uploadPathPrefix){
        validPicture(fileUrl);
        //图片上传地址
        String uuid = RandomUtil.randomString(16);
        String filename = FileUtil.mainName(fileUrl);
        String formatName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()),uuid,
                FileUtil.getSuffix(filename));
        String uploadPath = String.format("%s/%s",uploadPathPrefix,formatName);
        File tempFile = null;
        //创建临时文件
        try{
            tempFile = File.createTempFile(uploadPath, null);
            HttpUtil.downloadFile(fileUrl,tempFile);
            //上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, tempFile);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            uploadPictureResult.setUrl(String.format("%s/%s",cosClientConfig.getHost(),uploadPath));
            uploadPictureResult.setPicName(FileUtil.mainName(filename));
            uploadPictureResult.setPicSize(FileUtil.size(tempFile));
            uploadPictureResult.setPicWidth(imageInfo.getWidth());
            uploadPictureResult.setPicHeight(imageInfo.getHeight());
            Double scale = NumberUtil.round(imageInfo.getWidth() * 1.0 / imageInfo.getHeight(), 2).doubleValue();
            uploadPictureResult.setPicScale(scale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            return uploadPictureResult;

        } catch (Exception e) {
            log.error("file upload error",e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败");
        }finally {
            deleteTempPicture(tempFile);
        }

    }

    /**
     * 校验图片
     * @param file
     */
    private void validPicture(MultipartFile file){
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR,"文件不能为空");
        //校验文件大小
        long size = file.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(size > 2 * ONE_M, ErrorCode.PARAMS_ERROR,"文件大小不能超过2M");
        //校验文件后缀
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        //允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR,"文件类型错误");
    }

    /**
     * 校验url
     * @param fileUrl
     */
    private void validPicture(String fileUrl){
        ThrowUtils.throwIf(fileUrl == null, ErrorCode.PARAMS_ERROR,"文件url不能为空");
        //验证url格式
        try{
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件地址格式不正确");
        }
        //校验url协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("https://") || fileUrl.startsWith("http://")),
                ErrorCode.PARAMS_ERROR,"仅支持HTTP或HTTPS协议的文件地址");
        //发送HEAD请求以验证文件是否存在
        HttpResponse response = null;
        try{
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            //未正常返回，则无需执行其他判断
            if(response.getStatus() != HttpStatus.HTTP_OK){
                return;
            }
            //检验文件类型
            String contentType = response.header("Content-Type");
            if(StrUtil.isNotBlank(contentType)){
                final List<String> ALLOW_CONTENT_TYPE =
                        Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPE.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR,"文件类型错误");
            }
            //检验文件大小
            String contentLength = response.header("Content-Length");
            if(StrUtil.isNotBlank(contentLength)){
                try{
                    long length = Long.parseLong(contentLength);
                    final long TWO_M = 2 * 1024 * 1024L;
                    ThrowUtils.throwIf(length > TWO_M, ErrorCode.PARAMS_ERROR,"文件大小不能超过2M");
                }catch (NumberFormatException e){
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,"文件大小格式错误");
                }
            }
        } finally {
            if(response != null){
                response.close();
            }
        }


    }
}
